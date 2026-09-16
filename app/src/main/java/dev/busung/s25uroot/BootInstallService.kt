package dev.busung.s25uroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that runs the standalone install path at boot.
 *
 * The run is standalone because Shizuku is generally unreachable right after
 * boot; that is requested for the run alone, so the stored preference is left
 * as the user set it. Artifacts come from the normal commit-pinned download
 * flow, which needs the network, so the service waits for a validated
 * connection before starting rather than spending the boot on a fetch that
 * cannot succeed.
 *
 * The service stops itself once the run reaches a terminal phase, driven by the
 * run finishing rather than by watching for terminal states, because the
 * view model's own startup check can publish a terminal state before the run
 * has begun. It is not restarted by the system afterwards; re-running happens
 * on the next boot or from the app UI.
 */
class BootInstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationId = 0x42554f54

    private lateinit var viewModel: InstallViewModel

    override fun onCreate() {
        super.onCreate()
        createChannel()
        viewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(InstallViewModel::class.java)
        startForeground(
            notificationId,
            buildNotification(getString(R.string.status_checking_github)),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            if (!awaitValidatedNetwork()) {
                notify(getString(R.string.notification_boot_offline))
                stopSelf()
                return@launch
            }
            scope.launch { reportProgress() }
            viewModel.runToCompletion(forceStandalone = true)
            notify(notificationTitleFor(viewModel.state.value))
            // A boot that had to re-establish root is also the boot that can now bring Shizuku back,
            // so the startup the user asked for follows a run that actually succeeded.
            if (viewModel.state.value.phase == InstallPhase.Installed &&
                AppPreferences.shizukuBootMode(this@BootInstallService)
            ) {
                ShizukuBootService.start(this@BootInstallService)
            }
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

    /**
     * Mirrors the run's progress in the notification. States published before the
     * run starts (the view model's startup support check can fail on a device
     * that has no root yet) are skipped so they cannot describe the run wrongly.
     */
    private suspend fun reportProgress() {
        var started = false
        viewModel.state.collect { state ->
            if (!started) {
                started = state.busy
            }
            if (!started) return@collect
            val text = state.log.lineSequence().lastOrNull()?.take(120) ?: state.message
            notify(notificationTitleFor(state), text)
        }
    }

    /**
     * Boot-time connectivity is routinely not up yet, so wait for a validated
     * network within a bounded window before touching anything else.
     */
    private suspend fun awaitValidatedNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val deadline = SystemClock.elapsedRealtime() + NETWORK_WAIT_MILLIS
        var announced = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasValidatedNetwork(manager)) return true
            if (!announced) {
                announced = true
                notify(getString(R.string.notification_boot_waiting_network))
            }
            delay(NETWORK_POLL_MILLIS)
        }
        return hasValidatedNetwork(manager)
    }

    private fun hasValidatedNetwork(manager: ConnectivityManager): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * A run nobody is watching is only explained by this notification, so a failure names the stage
     * it stopped in rather than just saying that it failed.
     */
    private fun notificationTitleFor(state: InstallUiState): String {
        val failure = state.failure
        if (state.phase == InstallPhase.Failed && failure != null) {
            return getString(
                R.string.status_stage_failed,
                getString(failure.stage.label),
            )
        }
        return getString(
            when (state.phase) {
                InstallPhase.Exploiting -> R.string.status_exploit_running
                InstallPhase.LoadingKernelSu -> R.string.status_ksu_loading
                InstallPhase.Installed -> R.string.status_ksu_active
                InstallPhase.Failed -> R.string.status_install_failed
                else -> R.string.status_checking_github
            },
        )
    }

    private fun notify(title: String, text: String = "") {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String = "") =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launcherPendingIntent())
            .setOngoing(true)
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
                getString(R.string.notification_channel_boot),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "boot_install"
        private const val NETWORK_WAIT_MILLIS = 90_000L
        private const val NETWORK_POLL_MILLIS = 2_000L

        fun start(context: Context) {
            val intent = Intent(context, BootInstallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
