package dev.busung.s25uroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Starts Shizuku's server at boot, using the root KernelSU already provides.
 *
 * Shizuku is normally started by hand over adb, which means the transport this app stage payloads
 * through is gone after every reboot until someone finds a cable. With root on the device that is
 * avoidable, so a boot where KernelSU is already active can bring Shizuku back on its own.
 *
 * The service is foreground because a boot-time start has to outlive the broadcast, but its channel
 * is silent and low importance: succeeding is the normal case and is not worth a notification, so it
 * only announces itself while it is working and once when it fails. It never retries beyond a
 * bounded number of attempts and never asks for the network, because nothing here needs one.
 */
class ShizukuBootService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationId = 0x53484b5a

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(notificationId, buildNotification(getString(R.string.status_shizuku_starting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            // A cold boot is a busy moment for the system, and a root shell asked for too early
            // fails for reasons that have nothing to do with KernelSU.
            delay(SETTLE_DELAY_MILLIS)
            val outcome = startShizuku()
            notifyOutcome(outcome)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startShizuku(): ShizukuStartOutcome {
        // Reported rather than acted on: when Shizuku starts itself at boot there is nothing to do
        // here, and knowing that is the difference between "my boot start is broken" and "it was
        // never needed".
        if (runCatching { ShizukuIntentStarter.ownBootReceiverEnabled(this) }.getOrDefault(false)) {
            Log.i(TAG, "Shizuku starts itself on boot on this device; this app only waits for it")
        }
        var last = ShizukuStartOutcome(
            started = false,
            detail = getString(R.string.error_shizuku_start_no_root),
        )
        repeat(START_ATTEMPTS) { attempt ->
            if (rootShell("id") == null) {
                return last
            }
            last = ShizukuStarter.start(
                context = this,
                shell = ::rootShell,
            )
            if (last.started) return last
            if (attempt < START_ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS)
        }
        return last
    }

    /**
     * KernelSU's root shell is the transport: this runs before any Shizuku binder exists, so the
     * only way to reach a privileged process here is the root the device already has. A refusal is
     * reported as a failed command rather than thrown, so the starter's own reporting stays in
     * charge of what the user is told.
     */
    private fun rootShell(command: String): ShizukuController.ShellResult =
        KernelSuRuntime.rootShell(command)
            ?: ShizukuController.ShellResult(NO_ROOT_SHELL_EXIT, getString(R.string.error_shizuku_start_no_root))

    private fun notifyOutcome(outcome: ShizukuStartOutcome) {
        if (outcome.started) {
            notify(getString(R.string.status_shizuku_started))
            return
        }
        notify(getString(R.string.shizuku_not_running_title), outcome.detail.take(160))
    }

    private fun notify(title: String, text: String = "") {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String = "") =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launcherPendingIntent())
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun launcherPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_shizuku),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "RootMyGalaxyShizuku"
        private const val CHANNEL_ID = "shizuku_boot"
        private const val SETTLE_DELAY_MILLIS = 20_000L
        private const val RETRY_DELAY_MILLIS = 15_000L
        private const val START_ATTEMPTS = 3

        fun start(context: Context) {
            val intent = Intent(context, ShizukuBootService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
