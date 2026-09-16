package dev.busung.s25uroot

import org.json.JSONArray
import org.json.JSONObject

data class RemoteArtifact(
    val url: String,
    val size: Long,
    /**
     * Whether the declared [size] is enforced. The feed sets this to false for an artifact whose
     * declared size is not trustworthy, which is the only alternative to disabling the check for
     * every artifact of every source at once. A declared [sha256] proves the same thing and more,
     * so it takes over the check and this flag stops mattering for that artifact.
     */
    val verifySize: Boolean = true,
    /**
     * Lowercase hex SHA-256 of the artifact, when the feed declares one.
     *
     * This is what a size cannot be: verifiable. A size says nothing about the content, so a feed
     * that cannot state a trustworthy one has to turn checking off altogether; a hash lets it state
     * something the app can check either way.
     */
    val sha256: String? = null,
) {
    init {
        require(!verifySize || size > 0) {
            "An enforced size has to be positive: $url declares $size"
        }
        require(sha256 == null || isSha256(sha256)) { "Invalid artifact SHA-256 for $url" }
    }

    /** Whether the declared size still has to be checked, which a hash makes redundant. */
    val checksSize: Boolean
        get() = verifySize && sha256 == null
}

/** Whether [value] is a lowercase hex SHA-256, the only form the app compares against. */
internal fun isSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

data class TargetProfile(
    val profileId: String,
    val displayName: String,
    val models: Set<String>,
    val kernelVersions: Set<String>,
    val requiresFreshP0Session: Boolean = false,
    /** How this target wants its exploit run. Carried with the profile so every path uses it. */
    val routePolicy: ExploitRoutePolicy = ExploitRoutePolicy.LEGACY,
    val exploit: RemoteArtifact,
    val kernelSu: RemoteArtifact,
    /** Source that provided this target, empty when it was not loaded through one. */
    val sourceId: String = "",
    val sourceLabel: String = "",
    /**
     * Commit the source was read at when this target was loaded. Artifact URLs are pinned to it, so
     * recording it is what makes a finished run traceable to the catalog revision it came from
     * rather than to whatever the branch held at the time.
     */
    val sourceCommit: String = "",
) {
    init {
        require(models.isNotEmpty()) { "Payload must support at least one model" }
        require(kernelVersions.isNotEmpty()) { "Payload must support at least one kernel version" }
    }

    fun matchesDevice(snapshot: DeviceSnapshot): Boolean =
        models.any { it.equals(snapshot.model, ignoreCase = true) }

    fun matchesKernelVersion(snapshot: DeviceSnapshot): Boolean =
        snapshot.kernelVersion in kernelVersions

    fun matches(snapshot: DeviceSnapshot): Boolean =
        matchesDevice(snapshot) && matchesKernelVersion(snapshot)

    /** Unique across sources, unlike [profileId], which two sources may both offer. */
    val selectionId: String
        get() = selectionIdFor(sourceId, profileId)

    val supportedModels: String
        get() = models.joinToString()

    val supportedKernelVersions: String
        get() = kernelVersions.joinToString()
}

/**
 * Picks the profile for [snapshot].
 *
 * An exact full kernel-release match wins, so regional builds that share a
 * model and the three-part kernel version (for example `SM-S9360` ZCS vs ZHS)
 * resolve to the profile that documents their build. Profiles that only list a
 * three-part version keep the legacy first-match behaviour.
 */
fun List<TargetProfile>.resolveFor(snapshot: DeviceSnapshot): TargetProfile? =
    firstOrNull { it.matches(snapshot) && snapshot.kernelRelease in it.kernelVersions }
        ?: firstOrNull { it.matches(snapshot) }

/**
 * How a profile's declared kernel versions line up with a device.
 *
 * A profile that lists the device's full `uname -r` release documents this exact build; one that
 * lists only the three-part version may still be the right payload, but the feed has not tied it to
 * this build, which is what regional siblings look like from the app's side.
 */
enum class KernelMatch {
    /** The device's full kernel release is listed. */
    Exact,

    /** Only the three-part kernel version is listed. */
    Version,

    /** Neither is listed; only reachable in the sheet when the device filter is off. */
    None,
}

fun TargetProfile.kernelMatch(snapshot: DeviceSnapshot): KernelMatch = when {
    snapshot.kernelRelease in kernelVersions -> KernelMatch.Exact
    snapshot.kernelVersion in kernelVersions -> KernelMatch.Version
    else -> KernelMatch.None
}

data class SupportManifest(
    val schemaVersion: Int,
    val targets: List<TargetProfile>,
) {
    companion object {
        fun parse(bytes: ByteArray): SupportManifest {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            require(schemaVersion == 3) { "Unsupported support manifest schema" }
            val payloadsJson = root.getJSONArray("payloads")
            val payloads = buildList {
                for (index in 0 until payloadsJson.length()) {
                    val payload = payloadsJson.getJSONObject(index)
                    val exploit = payload.getJSONObject("exploit")
                    val kernelSu = payload.getJSONObject("kernelsu")
                    add(
                        TargetProfile(
                            profileId = payload.getString("payloadId"),
                            displayName = payload.getString("displayName"),
                            models = payload.getJSONArray("models").strings(),
                            kernelVersions = payload.getJSONArray("kernelVersions").strings(),
                            requiresFreshP0Session = payload.optBoolean("requiresFreshP0Session", false),
                            routePolicy = ExploitRoutePolicy.parse(payload.optJSONObject("routePolicy")),
                            exploit = exploit.artifact(),
                            kernelSu = kernelSu.artifact(),
                        ),
                    )
                }
            }
            return SupportManifest(schemaVersion, payloads)
        }

        private fun JSONArray.strings(): Set<String> = buildSet {
            for (index in 0 until length()) add(getString(index))
        }

        /** Reads one artifact. Both artifacts of a payload take the same optional fields. */
        private fun JSONObject.artifact(): RemoteArtifact = RemoteArtifact(
            url = getString("url"),
            size = getLong("size"),
            verifySize = optBoolean("verifySize", true),
            sha256 = optString("sha256").trim().takeIf(String::isNotEmpty),
        )
    }
}

/**
 * What a catalog offers, summarised so a source can be judged before it is saved rather than
 * discovered to be useless by a failed run.
 *
 * [models] and [kernelVersions] are the union across every payload, sorted, because the question a
 * source has to answer is what it covers, not which payload happens to be listed first.
 */
data class SourceCoverage(
    /** Revision the catalog was read at, so a summary is tied to the revision that produced it. */
    val commit: String,
    val payloadCount: Int,
    val models: List<String>,
    val kernelVersions: List<String>,
    /** The payload a run would pick on this device, or null when nothing here fits it. */
    val deviceProfileId: String?,
    /** How many payloads list this device's model and kernel version. */
    val deviceProfileCount: Int,
)

/**
 * Summarises a parsed catalog for [snapshot].
 *
 * The device question is answered with the same [resolveFor] the installer uses, so a summary
 * cannot claim a catalog covers a device that a run would then refuse.
 */
fun SupportManifest.coverageFor(snapshot: DeviceSnapshot, commit: String): SourceCoverage =
    SourceCoverage(
        commit = commit,
        payloadCount = targets.size,
        models = targets.flatMap { it.models }.distinct().sorted(),
        kernelVersions = targets.flatMap { it.kernelVersions }.distinct().sorted(),
        deviceProfileId = targets.resolveFor(snapshot)?.profileId,
        deviceProfileCount = targets.count { it.matches(snapshot) },
    )
