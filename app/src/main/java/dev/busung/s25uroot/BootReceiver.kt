package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-establishes root after a reboot: on these targets KernelSU is loaded as
 * a runtime module and nothing persists across power cycles, so the install
 * has to run again every boot. Skips when the module is already active.
 *
 * A boot that already has root also has the means to bring Shizuku back without a computer, so the
 * startup the user asked for is collected before the install check returns early - otherwise the
 * one boot where Shizuku could be started without adb is exactly the boot that skips it. When root
 * has to be re-established instead, [BootInstallService] starts Shizuku after a run that succeeded.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (NativeProbe.isKernelSuActive()) {
            if (AppPreferences.shizukuBootMode(context)) ShizukuBootService.start(context)
            return
        }
        if (!AppPreferences.bootRootMode(context)) return
        BootInstallService.start(context)
    }
}
