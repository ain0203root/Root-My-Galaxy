package dev.busung.s25uroot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The way pairing is started, and the way it is *forced* to start again.
 *
 * The second part is what makes this an activity rather than a plain service call. A stored pairing
 * can be stale in a way nothing on this side can see: the device may have discarded this app from its
 * paired-device list, while the key and the flag are still on disk. Retrying then looks like a no-op,
 * because a service that trusts the flag has nothing to do. Asking for a re-pair clears the flag
 * first, so the next pairing starts from the device's own state rather than from the app's record of
 * it.
 */
class AdbPairingSetupActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startPairingService()
        } else {
            Toast.makeText(
                this,
                getString(R.string.adb_pair_notification_permission_required),
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_FORCE_REPAIR, false)) {
            // The code field has to live in a notification, so the permission is what makes pairing
            // possible at all - it is cleared here and set again only when a pairing succeeds.
            AppPreferences.setAdbPaired(this, false)
        } else if (AppPreferences.adbPaired(this) && AdbCredentialStore.hasStoredKey(this)) {
            // Already paired with a key that can be tried: nothing to do, and the connection test is
            // what says whether it still works.
            finish()
            return
        }

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startPairingService()
            finish()
        } else {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startPairingService() {
        ContextCompat.startForegroundService(this, AdbPairingService.startIntent(this))
    }

    companion object {
        private const val EXTRA_FORCE_REPAIR = "force_repair"

        fun pairingIntent(context: Context, forceRepair: Boolean = false): Intent =
            Intent(context, AdbPairingSetupActivity::class.java)
                .putExtra(EXTRA_FORCE_REPAIR, forceRepair)
    }
}
