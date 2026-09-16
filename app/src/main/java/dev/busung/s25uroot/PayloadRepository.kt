package dev.busung.s25uroot

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

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

    private fun loadSource(source: PayloadSource): List<TargetProfile> {
        val commit = resolveCommit(source)
        val manifestBytes = downloadBytes(
            rawUrl(source, commit, "support/targets-v3.json"),
            MAX_MANIFEST_BYTES,
        )
        return SupportManifest.parse(manifestBytes).targets.map { profile ->
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
        // its declared size is wrong; the default stays strict for every other download.
        val checked = artifact.verifySize
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(!checked || connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
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
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(!checked || total == artifact.size) {
            context.getString(R.string.repo_incomplete, label)
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

    private fun resolveCommit(source: PayloadSource): String {
        // A pinned source is taken as written: no API call, so its catalog cannot move and it keeps
        // loading when the API is rate limited or offline in every way except the raw download.
        if (source.isPinned) return source.pinnedCommit
        val response = downloadBytes(commitApiUrl(source), MAX_COMMIT_RESPONSE_BYTES)
        val commit = JSONObject(response.toString(Charsets.UTF_8)).getString("sha")
        require(PayloadSource.isCommitValid(commit)) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    /**
     * `/commits/{ref}` and not `/git/refs/heads/{branch}`: a source's ref may be a branch, a tag, or
     * a commit, and this resolves all three to the commit it points at.
     */
    private fun commitApiUrl(source: PayloadSource) =
        "https://api.github.com/repos/${source.repository}/commits/${source.branch}"

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

    private fun downloadBytes(url: String, maximum: Int): ByteArray {
        val connection = open(url)
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

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
        }

    companion object {
        private const val MAX_COMMIT_RESPONSE_BYTES = 16 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
