package dev.busung.s25uroot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Which way a start attempt should be made, from the two things that decide it. */
internal enum class ShizukuStartRoute {
    /** Root exists, so Shizuku's own starter can be run where it is verifiable. */
    NativeStarter,

    /** No root, but the user configured a token: the broadcast is the only way left. */
    AuthenticatedIntent,

    /** No root and no token: nothing in this app can start a privileged process. */
    Unavailable,
}

/**
 * The route a start takes, as a function of the two facts that decide it.
 *
 * Pure, so the choice can be tested without a device, and worth testing because the fallback exists
 * precisely in the state that is hardest to reproduce: a device with no root. Root wins when it is
 * there, because the native starter's result is verifiable - the app can see the process it started -
 * while a broadcast is a request to another app that can only be answered by waiting for a binder.
 */
internal fun shizukuStartRoute(rootShellAvailable: Boolean, tokenConfigured: Boolean): ShizukuStartRoute =
    when {
        rootShellAvailable -> ShizukuStartRoute.NativeStarter
        tokenConfigured -> ShizukuStartRoute.AuthenticatedIntent
        else -> ShizukuStartRoute.Unavailable
    }

/**
 * Whether a boot is worth starting Shizuku on at all.
 *
 * The start used to be triggered from inside the boot's root check, which made it root-only in
 * practice: a device with a stored token and no root never asked Shizuku to start, even though the
 * token is exactly the route that does not need root. The same two facts as the route decide it, and
 * a boot with neither is left alone rather than told, once per reboot, that nothing can be done.
 */
internal fun shizukuBootStartWorthAttempting(
    rootAlreadyActive: Boolean,
    tokenConfigured: Boolean,
): Boolean = shizukuStartRoute(rootAlreadyActive, tokenConfigured) != ShizukuStartRoute.Unavailable

/**
 * Starts Shizuku by asking the Shizuku app itself, for devices where the app has no root to work with.
 *
 * Shizuku cannot normally be started by another app - that is the whole point of its security model -
 * so this is deliberately narrow. It exists for builds whose manager exposes an authenticated start
 * broadcast, and it does nothing at all unless the user has stored the matching token in settings.
 * Without a token the app would be sending a broadcast it cannot authenticate, which is a request to
 * start a privileged process that nobody should honour.
 *
 * The broadcast is package-scoped, so it can only be delivered to the Shizuku package, and the token
 * is never logged - not on success, and not in the failure detail.
 */
internal object ShizukuIntentStarter {

    internal data class Outcome(
        val started: Boolean,
        val attempted: Boolean,
        val detail: String = "",
    )

    suspend fun start(
        context: Context,
        binderTimeoutMillis: Long,
        onLog: (String) -> Unit = {},
    ): Outcome {
        val token = AppPreferences.shizukuAutomationToken(context).trim()
        if (token.isBlank()) {
            onLog("[*] No Shizuku start token is configured, so the app cannot ask Shizuku to start")
            return Outcome(
                started = false,
                attempted = false,
                detail = "Shizuku start token is not configured",
            )
        }

        // Deliberately no component pre-query: a package-scoped broadcast is harmless when nothing
        // listens, while querying can report absence for a receiver that exists but is disabled.
        val intent = Intent(START_ACTION)
            .setPackage(SHIZUKU_PACKAGE)
            .putExtra(AUTH_EXTRA, token)

        return try {
            context.sendBroadcast(intent)
            onLog("[*] Asked Shizuku to start itself with the configured token")
            if (ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started itself and its binder answered")
                Outcome(started = true, attempted = true)
            } else {
                val detail =
                    "the Shizuku start request was sent but no binder followed; check the token and " +
                        "that this Shizuku build accepts start requests"
                onLog("[!] $detail")
                Outcome(started = false, attempted = true, detail = detail)
            }
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            onLog("[!] Asking Shizuku to start itself failed: $detail")
            Outcome(started = false, attempted = true, detail = detail)
        }
    }

    /**
     * Whether Shizuku's own start-on-boot receiver is enabled.
     *
     * Reported rather than acted on: if Shizuku already starts itself at boot, this app starting it
     * too is redundant, and knowing that is the difference between "my boot start is broken" and "it
     * was never needed".
     */
    fun ownBootReceiverEnabled(context: Context): Boolean {
        val component = ComponentName(SHIZUKU_PACKAGE, BOOT_RECEIVER_CLASS)
        val manager = context.packageManager
        return runCatching {
            when (manager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> false
                else -> {
                    @Suppress("DEPRECATION")
                    manager.getReceiverInfo(component, 0).enabled
                }
            }
        }.getOrDefault(false)
    }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val START_ACTION = "$SHIZUKU_PACKAGE.START"
    private const val AUTH_EXTRA = "auth"
    private const val BOOT_RECEIVER_CLASS = "moe.shizuku.manager.receiver.BootCompleteReceiver"
}
