package dev.busung.s25uroot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything a run depends on except the exploit itself, checked before the run is started.
 *
 * The reason this exists is the shape of the thing being checked: this payload gets one attempt per boot, and
 * today the download, the hash, the flavour conflict, the transport and the pipe page budget are all
 * discovered *during* that attempt. A run that dies at the download stage has still spent the boot, so the
 * cheap facts are worth having first - and the expensive one, downloading and hashing the payload, is worth
 * doing before the attempt rather than inside it.
 *
 * Deliberately read-only about the device: it stages nothing, loads nothing, and sweeps nothing. What it does
 * to the outside world is one catalog fetch and one download into this app's own files directory, which is
 * where a run would put them anyway.
 *
 * Ordering matters twice over. Each item is answered in the order a run meets it, so the first failure in the
 * list is the first thing that would have gone wrong. And the items that need the chosen entry are answered
 * only once it has been resolved: a download reported as fine under a target that does not exist would be the
 * check claiming more than it knows, so the failure comes first and the rest is left unsaid rather than
 * guessed at.
 */
internal object PreflightRunner {

    suspend fun run(context: Context, selectionId: String? = null): PreflightReport =
        withContext(Dispatchers.IO) {
            val snapshot = DeviceSnapshot.current()
            val repository = PayloadRepository(context)
            val offline = AppPreferences.payloadMode(context) == PayloadMode.Offline
            val items = mutableListOf<PreflightItem>()

            // The entry, and the payload that comes with it. Offline mode resolves neither: the cached
            // payload already names its target, and asking the catalog is the very thing that mode exists to
            // avoid - the same rule the run itself follows.
            val verified: VerifiedPayloads?
            val profile: TargetProfile?
            if (offline) {
                verified = runCatching { KnownGoodPayloadStore.load(context) }.getOrNull()
                profile = verified?.profile
                items += if (profile == null) {
                    PreflightItem(
                        id = PreflightItemId.Target,
                        state = PreflightState.Fail,
                        detail = context.getString(R.string.offline_cache_empty),
                    )
                } else {
                    PreflightItem(
                        id = PreflightItemId.Target,
                        state = PreflightState.Note,
                        detail = context.getString(R.string.preflight_target_cached, profile.displayName),
                    )
                }
            } else {
                val resolved = runCatching {
                    if (selectionId == null) {
                        repository.resolveTarget(snapshot)
                    } else {
                        repository.resolveTarget(selectionId)
                    }
                }
                profile = resolved.getOrNull()
                verified = null
                items += resolved.fold(
                    onSuccess = { found ->
                        PreflightItem(
                            id = PreflightItemId.Target,
                            state = PreflightState.Ready,
                            detail = context.getString(
                                R.string.preflight_target_found,
                                found.displayName.ifBlank { found.profileId },
                                found.sourceLabel.ifBlank { context.getString(R.string.preflight_no_source) },
                            ),
                        )
                    },
                    onFailure = { failure ->
                        PreflightItem(
                            id = PreflightItemId.Target,
                            state = PreflightState.Fail,
                            detail = failure.message ?: failure.javaClass.simpleName,
                        )
                    },
                )
            }

            if (profile != null) {
                items += matchItem(context, profile, snapshot)
                items += flavorItem(context, profile)
                items += transportItem(context, profile)
                items += payloadItem(context, repository, profile, offline, verified)
            }

            // About the device rather than about the entry, so they are answered whatever the entry turned
            // out to be - a phone that has spent its boot's budget cannot run anything, and that is worth
            // knowing even on a phone with no payload at all.
            items += budgetItem(context)
            items += partitionsItem(context)
            items += stagingItem(context)

            PreflightReport(
                deviceLabel = TargetGap.describe(snapshot),
                targetLabel = profile?.displayName?.ifBlank { profile.profileId }
                    ?: context.getString(R.string.preflight_no_target),
                items = items,
            )
        }

    /**
     * Whether this firmware is the build the entry was written for.
     *
     * The app's own reading of the feed's two forms, and the one line here that is a warning rather than a
     * fact: an entry listing the three-part release is the right model on the right line, and whether the
     * native layer accepts this build is its decision rather than this app's.
     */
    private fun matchItem(
        context: Context,
        profile: TargetProfile,
        snapshot: DeviceSnapshot,
    ): PreflightItem = when (profile.kernelMatch(snapshot)) {
        KernelMatch.Exact -> PreflightItem(
            id = PreflightItemId.Match,
            state = PreflightState.Ready,
            detail = context.getString(R.string.preflight_match_exact, snapshot.kernelRelease),
        )
        KernelMatch.Version -> PreflightItem(
            id = PreflightItemId.Match,
            state = PreflightState.Warn,
            detail = context.getString(R.string.preflight_match_version, profile.supportedKernelVersions),
        )
        KernelMatch.None -> PreflightItem(
            id = PreflightItemId.Match,
            state = PreflightState.Warn,
            detail = context.getString(R.string.preflight_match_none),
        )
    }

    /**
     * Which KernelSU the entry carries, and whether the kernel already runs the other one.
     *
     * A failure rather than a warning when they differ, because it is not a risk: both projects hook the same
     * syscall paths, a loader refuses a module into a kernel that carries the other one, and the run refuses
     * for exactly this reason before it stages anything.
     */
    private fun flavorItem(context: Context, profile: TargetProfile): PreflightItem {
        val loaded = AppPreferences.loadedFlavor(context)
        return if (loaded != null && loaded != profile.flavor) {
            PreflightItem(
                id = PreflightItemId.Flavor,
                state = PreflightState.Fail,
                detail = context.getString(
                    R.string.run_flavor_conflict,
                    loaded.label,
                    profile.flavor.label,
                ),
            )
        } else {
            PreflightItem(
                id = PreflightItemId.Flavor,
                state = PreflightState.Ready,
                detail = context.getString(R.string.preflight_flavor_ok, profile.flavor.label),
            )
        }
    }

    /** What would carry the payload, from the same four readings the run makes in the same place. */
    private fun transportItem(context: Context, profile: TargetProfile): PreflightItem {
        val shellRequired = profile.routePolicy.prefersShellTransport
        val shizukuRequested = AppPreferences.shizukuMode(context)
        val shizukuUsable = ShizukuController.isRunning() && ShizukuController.isGranted()
        val localAdbPaired = AdbCredentialStore.hasStoredKey(context) && AppPreferences.adbPaired(context)
        val transport = chooseRunTransport(
            shellRequired = shellRequired,
            shizukuRequested = shizukuRequested,
            shizukuUsable = shizukuUsable,
            localAdbPaired = localAdbPaired,
        )
        return if (transport == null) {
            PreflightItem(
                id = PreflightItemId.Transport,
                state = PreflightState.Fail,
                detail = context.getString(shellTransportRefusalStringId(shizukuRequested)),
            )
        } else {
            PreflightItem(
                id = PreflightItemId.Transport,
                state = PreflightState.Ready,
                detail = context.getString(
                    when (transport) {
                        RunTransport.Shizuku -> R.string.preflight_transport_shizuku
                        RunTransport.LocalAdb -> R.string.preflight_transport_local_adb
                        RunTransport.App -> R.string.preflight_transport_app
                    },
                ),
            )
        }
    }

    /**
     * The payload itself, downloaded and hashed.
     *
     * The one item that does real work, and the reason the check is worth opening: it is the step a run spends
     * its attempt on, and it is the step that fails for reasons that have nothing to do with the exploit -
     * a source that moved, a file that is not what it declares, no network at all. The download lands in this
     * app's own files directory, which is where a run puts it too.
     */
    private fun payloadItem(
        context: Context,
        repository: PayloadRepository,
        profile: TargetProfile,
        offline: Boolean,
        cached: VerifiedPayloads?,
    ): PreflightItem {
        if (offline) {
            val loaded = cached ?: runCatching { KnownGoodPayloadStore.load(context, profile.profileId) }
                .getOrNull()
            return if (loaded == null) {
                PreflightItem(
                    id = PreflightItemId.Payload,
                    state = PreflightState.Fail,
                    detail = context.getString(R.string.offline_cache_empty),
                )
            } else {
                PreflightItem(
                    id = PreflightItemId.Payload,
                    state = PreflightState.Ready,
                    detail = context.getString(R.string.preflight_payload_cached, loaded.exploit.name),
                )
            }
        }
        return runCatching { repository.download(profile) { } }.fold(
            onSuccess = { payloads ->
                PreflightItem(
                    id = PreflightItemId.Payload,
                    state = PreflightState.Ready,
                    detail = context.getString(
                        R.string.preflight_payload_verified,
                        payloads.exploit.name,
                        payloads.kernelSu.name,
                    ),
                )
            },
            onFailure = { failure ->
                PreflightItem(
                    id = PreflightItemId.Payload,
                    state = PreflightState.Fail,
                    detail = failure.message ?: failure.javaClass.simpleName,
                )
            },
        )
    }

    /**
     * Whether this boot has already told the app its pipe page budget is gone.
     *
     * Read rather than probed, because the record comes from a payload's own output: the app can only ever
     * refuse a boot the device has already answered for, and the wording says as much - "not recorded as
     * spent" is the honest form of "this has not been seen to be spent".
     */
    private fun budgetItem(context: Context): PreflightItem {
        val spent = PipeBudget.spentInBoot(context, kernelBootToken())
        return PreflightItem(
            id = PreflightItemId.Budget,
            state = if (spent) PreflightState.Fail else PreflightState.Ready,
            detail = context.getString(
                if (spent) {
                    R.string.error_pipe_budget_spent
                } else {
                    R.string.preflight_budget_intact
                },
            ),
        )
    }

    /**
     * The optional guard that marks the writable image partitions read-only.
     *
     * A note and never a warning: it is a choice this app offers rather than something wrong with the phone,
     * and what a reader needs to know is which way it is set before a run rather than that it exists.
     */
    private fun partitionsItem(context: Context): PreflightItem {
        val on = AppPreferences.partitionReadOnlyMode(context)
        return PreflightItem(
            id = PreflightItemId.Partitions,
            state = PreflightState.Note,
            detail = context.getString(
                if (on) R.string.preflight_partitions_on else R.string.preflight_partitions_off,
            ),
        )
    }

    /**
     * What the shared temp directory holds, and whether something is already running from it.
     *
     * The directory is where a run stages its helper, and the one place any app on the device can look. Both
     * facts are worth having before a run rather than after: a run in flight makes this check a statement
     * about that run, and leftovers are what a detector finds.
     */
    private fun stagingItem(context: Context): PreflightItem {
        val holder = RunInFlight.holder(context)
        if (holder != null) {
            return PreflightItem(
                id = PreflightItemId.Staging,
                state = PreflightState.Warn,
                detail = context.getString(R.string.preflight_staging_in_flight),
            )
        }
        val report = runCatching { StagedResidue.read() }.getOrNull()
            ?: return PreflightItem(
                id = PreflightItemId.Staging,
                state = PreflightState.Note,
                detail = context.getString(R.string.preflight_staging_unknown),
            )
        return PreflightItem(
            id = PreflightItemId.Staging,
            state = PreflightState.Note,
            detail = report.logLine(context),
        )
    }
}
