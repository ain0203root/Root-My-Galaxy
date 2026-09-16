package dev.busung.s25uroot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wireless debugging as a window, never as device state.
 *
 * The app turns it on to get a shell and turns it off again, because leaving it on leaves a shell
 * port open that the user did not ask for. Two things make that reliable rather than hopeful:
 *
 * - A short grace period between consecutive users. The transport is handed over - the exploit's
 *   session ends and the KernelSU handoff starts within a second or two of it - and tearing adbd down
 *   in that gap would break the handoff that follows.
 * - An alarm armed *before* the setting is turned on, so a process killed during the window still has
 *   something that turns it back off. The alarm is set well past the longest run this app starts, so
 *   the failsafe can never fire while a legitimate run is still using the transport.
 *
 * In-process users are serialized, because two of them can be alive at once (a boot-time Shizuku start
 * and an automatic install): without the lock, whoever finished first would turn wireless debugging off
 * while the other still held an authenticated session.
 */
internal object TemporaryWirelessAdb {

    private val sessionMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cleanupLock = Any()
    private var pendingGraceDisable: Runnable? = null

    /** Turns wireless debugging on if it is off. Returns false when the app may not. */
    fun begin(context: Context, onLog: (String) -> Unit = {}): Boolean {
        cancelGraceDisable()
        armCleanup(context)
        val enabled = AdbPairing.enableWirelessAdb(context)
        if (enabled) {
            onLog("[*] Wireless debugging enabled for this run only")
        } else {
            cancelCleanup(context)
            onLog("[!] Wireless debugging could not be enabled: WRITE_SECURE_SETTINGS is required")
        }
        return enabled
    }

    /** Runs [block] with wireless debugging on, and schedules it off again afterwards. */
    suspend fun <T> use(
        context: Context,
        settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        onLog: (String) -> Unit = {},
        block: suspend () -> T,
    ): T = sessionMutex.withLock {
        check(begin(context, onLog)) {
            "Wireless debugging could not be enabled: WRITE_SECURE_SETTINGS is required"
        }
        try {
            // adbd takes a moment to publish its port and start listening, and a lookup that raced it
            // would report a device with no transport.
            if (settleMillis > 0) delay(settleMillis)
            block()
        } finally {
            scheduleGraceDisable(context, onLog)
        }
    }

    /** Turns wireless debugging off now, whatever the grace period was doing. */
    fun forceDisable(context: Context, onLog: (String) -> Unit = {}) {
        cancelGraceDisable()
        val disabled = runCatching { AdbPairing.disableWirelessAdb(context) }.getOrDefault(false)
        cancelCleanup(context)
        if (disabled) {
            onLog("[+] Wireless debugging disabled")
        } else {
            Log.e(TAG, "Wireless debugging could not be turned off")
            onLog("[!] Wireless debugging could not be turned off")
        }
    }

    private fun scheduleGraceDisable(context: Context, onLog: (String) -> Unit) {
        val appContext = context.applicationContext
        val task = Runnable {
            synchronized(cleanupLock) { pendingGraceDisable = null }
            forceDisable(appContext)
        }
        synchronized(cleanupLock) {
            pendingGraceDisable?.let(mainHandler::removeCallbacks)
            pendingGraceDisable = task
            mainHandler.postDelayed(task, HANDOFF_GRACE_MILLIS)
        }
        onLog("[*] Wireless debugging will be turned off once this transport is no longer in use")
    }

    private fun cancelGraceDisable() {
        synchronized(cleanupLock) {
            pendingGraceDisable?.let(mainHandler::removeCallbacks)
            pendingGraceDisable = null
        }
    }

    private fun armCleanup(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + FAILSAFE_DISABLE_DELAY_MILLIS,
            cleanupPendingIntent(context),
        )
    }

    private fun cancelCleanup(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(cleanupPendingIntent(context))
    }

    private fun cleanupPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        CLEANUP_REQUEST_CODE,
        Intent(context, WirelessAdbCleanupReceiver::class.java).setAction(ACTION_FORCE_DISABLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_FORCE_DISABLE = "dev.busung.s25uroot.action.FORCE_DISABLE_WIRELESS_ADB"

    private const val TAG = "RootMyGalaxyAdb"
    private const val CLEANUP_REQUEST_CODE = 0x57414442
    private const val HANDOFF_GRACE_MILLIS = 5_000L

    /** Past the longest run this app starts, so the failsafe cannot fire under a live run. */
    private const val FAILSAFE_DISABLE_DELAY_MILLIS = 20 * 60 * 1_000L
    private const val DEFAULT_SETTLE_MILLIS = 1_000L
}

/** What turns wireless debugging off when the process that enabled it is no longer there to. */
class WirelessAdbCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TemporaryWirelessAdb.ACTION_FORCE_DISABLE) return
        TemporaryWirelessAdb.forceDisable(context)
    }
}
