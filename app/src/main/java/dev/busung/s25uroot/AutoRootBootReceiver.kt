package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The one thing that happens on `BOOT_COMPLETED`: decide whether this boot gets an automatic
 * install, and start the gate that performs it.
 *
 * The receiver is kept deliberately small - no payload hashing, no file walking, no network - because
 * it runs inside the framework's boot broadcast with a time budget it does not control. The gate does
 * the real work in its own process, where being slow costs nothing but its own start.
 *
 * The Shizuku start is collected before the install checks return early for the same reason it always
 * has been: a boot that already has root is exactly the boot where Shizuku can be brought back
 * without a cable, so it must not be skipped along with the install.
 */
class AutoRootBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // The quick reading only: this runs inside the framework's boot broadcast, and the fallback
        // starts a process and waits on it. A wrong no here costs a wake-up, not an install - the
        // gate asks again, authoritatively, before it spends this boot's attempt.
        val rootActive = RootStatusProbe.isActiveQuick()

        // Collected before the root checks rather than inside them, because Shizuku no longer needs
        // root to be started: the app's own adb identity and a stored start token are each routes of
        // their own, and a boot with one of those and no root is exactly the boot where starting
        // Shizuku matters most. A boot with none of them is left alone instead of being told once per
        // reboot that nothing can be done.
        if (AppPreferences.shizukuBootMode(context) &&
            shizukuBootStartWorthAttempting(
                rootAlreadyActive = rootActive,
                localAdbPaired = AdbCredentialStore.hasStoredKey(context) &&
                    AppPreferences.adbPaired(context),
                tokenConfigured = AppPreferences.shizukuAutomationToken(context).isNotBlank(),
            )
        ) {
            ShizukuBootService.start(context)
        }

        if (rootActive) return
        // A one-shot retry armed from the run screen counts here too: those two are the only ways this
        // boot can have been asked for an install, and the gate is where either one is carried out.
        if (!AppPreferences.bootRootMode(context) && !AppPreferences.retryArmed(context)) return

        // The boot id is the only thing that tells a real reboot from a userspace restart that
        // re-emits BOOT_COMPLETED, and claiming it has to happen before anything is started.
        val bootToken = AutoRootSupport.currentBootToken() ?: return
        if (!AutoRootSupport.claimBootCompletedForKernel(context, bootToken)) {
            Log.i(TAG, "Ignoring a repeated BOOT_COMPLETED within one kernel boot")
            AutoRootService.stop(context)
            return
        }
        if (AutoRootSupport.hasAttemptedBoot(context, bootToken)) {
            AutoRootService.stop(context)
            return
        }
        if (!AutoRootSupport.shouldRunForBoot(context, bootToken)) {
            AutoRootService.stop(context)
            return
        }
        AutoRootService.start(context)
    }

    private companion object {
        const val TAG = "RootMyGalaxyBoot"
    }
}

/** The actions a boot-install notification offers. */
class AutoRootActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISABLE_ROOT_ON_BOOT -> {
                // Turning the setting off first is what makes the decision stick: the service stops
                // for this boot, and the next boot's receiver sees the setting before anything else.
                AppPreferences.setBootRootMode(context, false)
                AutoRootService.stop(context)
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_ROOT_ON_BOOT =
            "dev.busung.s25uroot.action.DISABLE_ROOT_ON_BOOT"
    }
}
