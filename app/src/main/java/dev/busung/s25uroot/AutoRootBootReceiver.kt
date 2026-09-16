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

        if (NativeProbe.isKernelSuActive()) {
            if (AppPreferences.shizukuBootMode(context)) ShizukuBootService.start(context)
            return
        }
        if (!AppPreferences.bootRootMode(context)) return

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
