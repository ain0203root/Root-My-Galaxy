package dev.busung.s25uroot

import org.json.JSONArray
import org.json.JSONObject

data class RemoteArtifact(
    val url: String,
    val size: Long,
    /**
     * Whether the declared [size] is enforced. The feed sets this to false for an artifact whose
     * declared size is not trustworthy, which is the only alternative to disabling the check for
     * every artifact of every source at once.
     */
    val verifySize: Boolean = true,
)

data class TargetProfile(
    val profileId: String,
    val displayName: String,
    val models: Set<String>,
    val kernelVersions: Set<String>,
    val requiresFreshP0Session: Boolean = false,
    val exploit: RemoteArtifact,
    val kernelSu: RemoteArtifact,
    /** Source that provided this target, empty when it was not loaded through one. */
    val sourceId: String = "",
    val sourceLabel: String = "",
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
                            exploit = RemoteArtifact(
                                url = exploit.getString("url"),
                                size = exploit.getLong("size"),
                                verifySize = exploit.optBoolean("verifySize", true),
                            ),
                            kernelSu = RemoteArtifact(
                                url = kernelSu.getString("url"),
                                size = kernelSu.getLong("size"),
                            ),
                        ),
                    )
                }
            }
            return SupportManifest(schemaVersion, payloads)
        }

        private fun JSONArray.strings(): Set<String> = buildSet {
            for (index in 0 until length()) add(getString(index))
        }
    }
}
