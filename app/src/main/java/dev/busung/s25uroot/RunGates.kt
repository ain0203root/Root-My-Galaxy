package dev.busung.s25uroot

import androidx.annotation.StringRes

/**
 * What can be done about a gate, which is what its row's button does.
 *
 * The fix is a small closed list rather than a lambda because the *choice* of fix is a property of the state
 * and belongs with the state: "Shizuku is not running" is fixed by starting it, "this app has no root shell"
 * by the KernelSU grant, and deciding that at the row that happens to draw the button is how a row ends up
 * offering the wrong one.
 */
internal enum class RunGateFix(@StringRes val label: Int) {
    /** Ask Shizuku for this app's grant, which is the state Shizuku sends no callback for. */
    AllowShizuku(R.string.run_gate_fix_allow),

    /** Run Shizuku's own starter through the root shell the device already has. */
    StartShizuku(R.string.run_gate_fix_start),

    /** Ask Android to stop restricting this app, which is what an unattended run needs with the screen off. */
    AllowBattery(R.string.run_gate_fix_allow),

    /**
     * Start a run, because the only cure for "no root in this boot" is a run that loads some.
     *
     * The same thing the install card above offers: a gate says what is missing, and the thing that fixes
     * this one is the whole point of the app rather than a permission the user can grant.
     */
    RootNow(R.string.run_gate_fix_root_now),
}

/**
 * One thing a run needs, and where this phone stands on it.
 */
internal data class RunGate(
    @StringRes val label: Int,
    @StringRes val state: Int,
    /**
     * What to do about it, or null when nothing can be done from here.
     *
     * Null is a real answer and not a gap: a KernelSU that could not be read is not a KernelSU that is
     * missing - `su` may be unanswered, the reading may have raced the module loading - and offering "root
     * now" for it would be the app telling someone to re-root a phone that is very likely already rooted.
     */
    val fix: RunGateFix? = null,
)

/**
 * The gates a run still has to pass, in the order they are worth reading.
 *
 * Only the outstanding ones: this is the "what is stopping a run" card, and a checklist that lists what is
 * already fine is a list that has to be read to the end to find the one line that matters. An empty result is
 * therefore good news rather than no answer, and the card says so in as many words.
 *
 * The three are the states a manual run genuinely depends on, and each is a fact about the device rather than
 * a preference of this app's:
 *
 * - **KernelSU** is loaded per boot, so a phone rooted yesterday is a phone that has to be rooted again - and
 *   that is the one gate the app itself closes.
 * - **Shizuku**, and only when the user has asked for it: with it switched off the app runs without a shell
 *   transport at all, and a row about Shizuku that is not switched on is a row about nothing. Whether it is
 *   running and whether this app may use it are separate facts, which is why they are separate states.
 * - **The battery exemption**, because the run this card exists for is the unattended one: restricted, an
 *   install with the screen off loses its network and its process priority exactly when it needs them.
 *
 * The payload catalog is deliberately not one of these. Whether a payload fits this phone is a fact about the
 * feeds rather than about the device, it is answered in the sheet where a run is chosen, and a gate with no
 * button on it would be a row that only worries people.
 */
internal fun pendingRunGates(
    kernelSu: KernelSuStatus,
    shizukuMode: Boolean,
    shizuku: ShizukuAvailability,
    batteryUnrestricted: Boolean,
): List<RunGate> = buildList {
    when (kernelSu) {
        KernelSuStatus.Active -> Unit
        KernelSuStatus.NotLoaded -> add(
            RunGate(
                label = R.string.readiness_kernelsu,
                state = R.string.readiness_ksu_not_loaded,
                fix = RunGateFix.RootNow,
            ),
        )
        KernelSuStatus.Unreadable -> add(
            RunGate(
                label = R.string.readiness_kernelsu,
                state = R.string.readiness_ksu_unreadable,
            ),
        )
    }

    if (shizukuMode) {
        when (shizuku) {
            ShizukuAvailability.Ready -> Unit
            ShizukuAvailability.WithoutPermission -> add(
                RunGate(
                    label = R.string.readiness_shizuku,
                    state = R.string.settings_shizuku_state_needs_permission,
                    fix = RunGateFix.AllowShizuku,
                ),
            )
            ShizukuAvailability.NotRunning -> add(
                RunGate(
                    label = R.string.readiness_shizuku,
                    state = R.string.readiness_shizuku_not_running,
                    fix = RunGateFix.StartShizuku,
                ),
            )
        }
    }

    if (!batteryUnrestricted) {
        add(
            RunGate(
                label = R.string.run_gate_battery,
                state = R.string.run_gate_battery_restricted,
                fix = RunGateFix.AllowBattery,
            ),
        )
    }
}
