package dev.busung.s25uroot

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.net.URL
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

/**
 * The commit a ref resolves to, from either shape of answer GitHub can give.
 *
 * The app asks for `application/vnd.github.sha`, which answers with the bare 40-character commit,
 * and that is what it reads first. A JSON commit object is still understood, because a server or a
 * proxy that ignores the requested media type would otherwise turn a resolvable source into an
 * unreadable one. Anything else - an error page, a truncated body - resolves to nothing rather than
 * to a commit the app then trusts.
 */
internal fun parseCommitResponse(body: String): String? {
    val trimmed = body.trim()
    if (PayloadSource.isCommitValid(trimmed)) return trimmed
    val fromJson = runCatching { JSONObject(trimmed).optString("sha") }.getOrNull() ?: return null
    return fromJson.trim().takeIf(PayloadSource::isCommitValid)
}

/** A revision a source can be pinned to, as a picker lists it. */
data class SourceRevision(
    val commit: String,
    /** The tag this revision is named by, when it has one; null for a plain commit. */
    val tag: String? = null,
    /** The first line of the commit message, or the tag name. */
    val label: String = "",
    /** The commit date, as the short form shown beside it. */
    val date: String = "",
)

/**
 * The commits GitHub returns, newest first, as the picker lists them.
 *
 * A commit whose SHA is not a full commit is dropped rather than listed: picking it would store a
 * pin that cannot be resolved again, which is the one thing a pin must never be. A message is only
 * ever shown, never parsed, so only its first line and a bounded length survive.
 */
internal fun parseCommits(body: String, limit: Int = Int.MAX_VALUE): List<SourceRevision> {
    val array = runCatching { org.json.JSONArray(body.trim()) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), limit)) {
            val entry = array.optJSONObject(index) ?: continue
            val sha = entry.optString("sha").trim()
            if (!PayloadSource.isCommitValid(sha)) continue
            val commit = entry.optJSONObject("commit")
            val message = commit?.optString("message").orEmpty().lineSequence()
                .firstOrNull(String::isNotBlank).orEmpty().trim().take(MESSAGE_MAX_LENGTH)
            val date = commit?.optJSONObject("committer")?.optString("date")
                ?.take(DATE_LENGTH)
                .orEmpty()
            add(SourceRevision(commit = sha, label = message, date = date))
        }
    }
}

/**
 * The tags GitHub returns, as the picker lists them. A tag carries the commit it points at, which
 * is what gets pinned - a tag can be moved onto another commit, so it is never stored as a pin.
 */
internal fun parseTags(body: String, limit: Int = Int.MAX_VALUE): List<SourceRevision> {
    val array = runCatching { org.json.JSONArray(body.trim()) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), limit)) {
            val entry = array.optJSONObject(index) ?: continue
            val name = entry.optString("name").trim()
            val sha = entry.optJSONObject("commit")?.optString("sha").orEmpty().trim()
            if (name.isEmpty() || !PayloadSource.isCommitValid(sha)) continue
            add(SourceRevision(commit = sha, tag = name, label = name.take(MESSAGE_MAX_LENGTH)))
        }
    }
}

/** How long a revision label may be before it stops being a label. */
private const val MESSAGE_MAX_LENGTH = 96

/** Enough of `2026-09-08T19:55:26Z` to read as a date. */
private const val DATE_LENGTH = 10

/** SHA-256 of [bytes] as lowercase hex, the form a manifest declares an artifact hash in. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Targets merged from every enabled source, plus one message per source that could not be
 * read. A source that fails is left out rather than taking the whole catalog down with it.
 */
data class LoadedCatalog(
    val targets: List<TargetProfile>,
    val sourceFailures: List<String>,
)

class PayloadRepository(private val context: Context) {
    fun loadCatalog(): LoadedCatalog {
        val sources = AppPreferences.payloadSources(context).enabledSources()
        require(sources.isNotEmpty()) { context.getString(R.string.repo_no_source_enabled) }

        val targets = mutableListOf<TargetProfile>()
        val failures = mutableListOf<String>()
        sources.forEach { source ->
            try {
                targets += loadSource(source)
            } catch (error: Throwable) {
                failures += context.getString(
                    R.string.repo_source_failed,
                    source.label,
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }

        require(targets.isNotEmpty()) {
            failures.ifEmpty { listOf(context.getString(R.string.repo_no_profile)) }.joinToString("\n")
        }
        return LoadedCatalog(targets, failures)
    }

    fun loadTargets(): List<TargetProfile> = loadCatalog().targets

    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile = loadTargets()
        .resolveFor(snapshot)
        ?: error(context.getString(R.string.repo_no_profile))

    /** Resolves a catalog selection, which may name the source it came from. */
    fun resolveTarget(selectionId: String): TargetProfile {
        val sourceId = sourceFromSelectionId(selectionId)
        val profileId = profileFromSelectionId(selectionId)
        val source = sourceId?.let { id ->
            AppPreferences.payloadSources(context).firstOrNull { it.id == id }
        }
        if (sourceId != null && source == null) {
            error(context.getString(R.string.repo_source_missing, sourceId))
        }

        val candidates = if (source != null) loadSource(source) else loadTargets()
        return candidates.firstOrNull { it.profileId == profileId }
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
    }

    /**
     * Reads a source without saving it: the check the sources sheet runs before a repository is
     * added. An unreachable repository, a missing manifest, or a schema this app cannot read is
     * reported here instead of becoming a source that fails silently on every later run.
     */
    fun inspect(source: PayloadSource, snapshot: DeviceSnapshot): SourceCoverage {
        val fetched = fetchManifest(source)
        return fetched.manifest.coverageFor(snapshot, fetched.commit)
    }

    /** The manifest and the revision it was read at. Both callers need the revision. */
    private data class FetchedManifest(val commit: String, val manifest: SupportManifest)

    private fun fetchManifest(source: PayloadSource): FetchedManifest {
        val commit = resolveCommit(source)
        val manifestBytes = downloadBytes(
            rawUrl(source, commit, MANIFEST_PATH),
            MAX_MANIFEST_BYTES,
        )
        return FetchedManifest(commit, SupportManifest.parse(manifestBytes))
    }

    private fun loadSource(source: PayloadSource): List<TargetProfile> {
        val fetched = fetchManifest(source)
        val commit = fetched.commit
        return fetched.manifest.targets.map { profile ->
            profile.copy(
                sourceId = source.id,
                sourceLabel = source.label,
                sourceCommit = commit,
                exploit = profile.exploit.copy(url = pinArtifactUrl(source, profile.exploit.url, commit)),
                kernelSu = profile.kernelSu.copy(url = pinArtifactUrl(source, profile.kernelSu.url, commit)),
            )
        }
    }

    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        val directory = File(context.filesDir, "payloads/${cacheKey(profile)}").apply { mkdirs() }
        val exploit = importedExploit(directory, onProgress) ?: downloadArtifact(
            profile.exploit,
            File(directory, "cve-2026-43499-app.so"),
            context.getString(R.string.artifact_exploit),
            onProgress,
        )
        val kernelSu = downloadArtifact(
            profile.kernelSu,
            File(directory, "ksud-s25u-kdp"),
            context.getString(R.string.artifact_kernelsu),
            onProgress,
        )
        Os.chmod(exploit.absolutePath, 0b100100100)
        Os.chmod(kernelSu.absolutePath, 0b100100100)
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    /**
     * Stages the imported payload in place of the downloaded exploit. KernelSU still comes from the
     * source matched to this device, because nothing about importing an exploit changes which
     * ksud this kernel needs.
     */
    private fun importedExploit(directory: File, onProgress: (String) -> Unit): File? {
        if (LocalPayload.file(context) == null) return null
        onProgress(
            context.getString(
                R.string.repo_using_local_payload,
                LocalPayload.displayName(context).orEmpty(),
            ),
        )
        return LocalPayload.stage(context, File(directory, "cve-2026-43499-app.so"))
    }

    private fun downloadArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        // A source can mark an artifact as unverifiable, which is the only way to accept it when
        // its declared size is wrong; the default stays strict for every other download. A declared
        // hash proves more than a size does, so it takes over and the size is then only a limit on
        // how much may be read rather than a value that has to match.
        val checked = artifact.checksSize
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(!checked || connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(!checked || total <= artifact.size) {
                        context.getString(R.string.repo_size_exceeded, label)
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(!checked || total == artifact.size) {
            context.getString(R.string.repo_incomplete, label)
        }
        artifact.sha256?.let { declared ->
            require(digest.digest().toHex() == declared) {
                context.getString(R.string.repo_hash_mismatch, label)
            }
        }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    /** Keeps two sources offering the same payload id from sharing a download directory. */
    private fun cacheKey(profile: TargetProfile): String {
        val profilePart = sanitize(profile.profileId)
        if (profile.sourceId.isEmpty()) return profilePart
        return "${sanitize(profile.sourceId)}--$profilePart"
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Resolves a source's ref to the commit it currently points at, for pinning it from the UI. */
    fun resolveRevision(source: PayloadSource): String = resolveCommit(source)

    /**
     * Resolves a ref the user named - a branch, a tag, or a commit - to the commit to pin.
     *
     * A source that is already pinned resolves its own ref instead, because the pin is the revision
     * being read; naming a different ref from the picker replaces the pin rather than the ref.
     */
    fun resolveNamedRevision(repository: String, ref: String): String {
        val trimmed = ref.trim()
        if (PayloadSource.isCommitValid(trimmed)) return trimmed.trim()
        require(PayloadSource.isRepositoryValid(repository)) {
            context.getString(R.string.payload_repository_invalid)
        }
        require(PayloadSource.isBranchValid(trimmed)) { context.getString(R.string.payload_branch_invalid) }
        val response = downloadBytes(
            repositoryCommitApiUrl(repository, trimmed),
            MAX_COMMIT_RESPONSE_BYTES,
            GITHUB_SHA_MEDIA_TYPE,
        )
        val commit = parseCommitResponse(response.toString(Charsets.UTF_8))
        require(commit != null) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    /**
     * The revisions a source can be pinned to: its tags, then its most recent commits, newest first.
     *
     * The first commit of a ref *is* that ref's head, so the picker needs no separate call to say
     * where the branch currently stands, and the list is what gives a pin something to choose from
     * rather than only "wherever it is now". A repository with no tags is the normal case and is
     * not an error.
     */
    fun revisions(source: PayloadSource, limit: Int = DEFAULT_REVISION_COUNT): List<SourceRevision> {
        val tags = runCatching { tags(source, limit) }.getOrDefault(emptyList())
        val commits = commits(source, limit)
        return tags + commits
    }

    private fun tags(source: PayloadSource, limit: Int): List<SourceRevision> = parseTags(
        downloadBytes(tagsApiUrl(source, limit), MAX_LIST_RESPONSE_BYTES).toString(Charsets.UTF_8),
        limit,
    )

    private fun commits(source: PayloadSource, limit: Int): List<SourceRevision> = parseCommits(
        downloadBytes(
            commitsApiUrl(source, limit),
            MAX_LIST_RESPONSE_BYTES,
        ).toString(Charsets.UTF_8),
        limit,
    )

    private fun resolveCommit(source: PayloadSource): String {
        // A pinned source is taken as written: no API call, so its catalog cannot move and it keeps
        // loading when the API is rate limited or offline in every way except the raw download.
        if (source.isPinned) return source.pinnedCommit
        // `application/vnd.github.sha` answers with the 40-character commit and nothing else: 40
        // bytes, against the tens of kilobytes a commit's own JSON carries once its file list is
        // included. Asking for the commit object is what made a source unreadable once its newest
        // commit touched enough files to exceed the response limit - the only part needed was the
        // SHA, and the size of the response had nothing to do with it.
        val response = downloadBytes(
            commitApiUrl(source),
            MAX_COMMIT_RESPONSE_BYTES,
            GITHUB_SHA_MEDIA_TYPE,
        )
        val commit = parseCommitResponse(response.toString(Charsets.UTF_8))
        require(commit != null) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    /**
     * `/commits/{ref}` and not `/git/refs/heads/{branch}`: a source's ref may be a branch, a tag, or
     * a commit, and this resolves all three to the commit it points at.
     */
    private fun commitApiUrl(source: PayloadSource) =
        repositoryCommitApiUrl(source.repository, source.branch)

    private fun repositoryCommitApiUrl(repository: String, ref: String) =
        "https://api.github.com/repos/$repository/commits/$ref"

    private fun commitsApiUrl(source: PayloadSource, limit: Int) =
        "https://api.github.com/repos/${source.repository}/commits" +
            "?sha=${source.branch}&per_page=$limit"

    private fun tagsApiUrl(source: PayloadSource, limit: Int) =
        "https://api.github.com/repos/${source.repository}/tags?per_page=$limit"

    private fun rawRepository(source: PayloadSource) =
        "https://raw.githubusercontent.com/${source.repository}"

    private fun rawUrl(source: PayloadSource, commit: String, path: String) =
        "${rawRepository(source)}/$commit/$path"

    private fun mutableRawPrefix(source: PayloadSource) =
        "${rawRepository(source)}/${source.branch}/"

    // Manifests written for the built-in feed reference its mutable branch URLs, so accept
    // that prefix too and re-pin it to the source the manifest was actually read from.
    private fun builtInMutableRawPrefix() =
        "https://raw.githubusercontent.com/${PayloadSource.DEFAULT.repository}/" +
            "${PayloadSource.DEFAULT.branch}/"

    private fun pinArtifactUrl(source: PayloadSource, url: String, commit: String): String {
        val prefix = mutableRawPrefix(source)
        val builtInPrefix = builtInMutableRawPrefix()
        val relative = when {
            url.startsWith(prefix) -> url.removePrefix(prefix)
            url.startsWith(builtInPrefix) -> url.removePrefix(builtInPrefix)
            else -> error(context.getString(R.string.repo_url_invalid))
        }
        return "${rawRepository(source)}/$commit/$relative"
    }

    private fun downloadBytes(url: String, maximum: Int, accept: String? = null): ByteArray {
        val connection = open(url, accept)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.repo_response_too_large)
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        connection.disconnect()
        return bytes
    }

    private fun open(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            accept?.let { setRequestProperty("Accept", it) }
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
        }

    companion object {
        /** Asks for the bare commit instead of the commit object, so the answer cannot grow with it. */
        private const val GITHUB_SHA_MEDIA_TYPE = "application/vnd.github.sha"

        // A ceiling, not a target: the SHA answer is 40 bytes. It is generous enough that a server
        // ignoring the media type and sending the commit object still resolves rather than failing
        // on a limit that only ever existed to bound memory.
        private const val MAX_COMMIT_RESPONSE_BYTES = 512 * 1024
        /** How many revisions a picker offers, per section, when a source is pinned. */
        const val DEFAULT_REVISION_COUNT = 15

        // A revision list is ~4 KB per commit, so this bounds memory with room to spare; the tags
        // answer is smaller still. Neither can grow with the commit the way a commit object did.
        private const val MAX_LIST_RESPONSE_BYTES = 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MANIFEST_PATH = "support/targets-v3.json"
    }
}
