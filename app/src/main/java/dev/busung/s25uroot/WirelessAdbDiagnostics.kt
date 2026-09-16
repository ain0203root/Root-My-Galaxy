package dev.busung.s25uroot

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What this app can say about its wireless-debugging authorization, and nothing more. */
internal enum class WirelessAdbAuthState {
    /** No key stored: there is nothing to authorize. */
    NoCredential,

    /** A key is stored, and nothing has asked the device whether it still accepts it. */
    SavedUnverified,

    /** A connection succeeded, so adbd answered with a shell identity. */
    Valid,

    /** adbd refused this app's certificate: it was unpaired device-side. */
    PairingRejected,

    /** No port could be found, by the property or by mDNS. */
    PortUnavailable,

    /** The app may not turn wireless debugging on, so it cannot test anything. */
    PermissionRequired,

    /** Anything else that stopped the connection. */
    ConnectionFailed,
}

internal data class WirelessAdbSnapshot(
    val credentialFlag: Boolean,
    val keyPresent: Boolean,
    val fingerprint: String?,
    val wirelessDebuggingEnabled: Boolean,
    val connectPort: Int? = null,
    val authState: WirelessAdbAuthState,
    val detail: String = "",
)

/**
 * Which state a failed connection test is, from the two things that distinguish them.
 *
 * A refused certificate and a missing port need opposite responses - pair again on the device, versus
 * turn wireless debugging on - so they are separated before anything is reported. Pure, so the
 * distinction can be tested without a device that has either problem.
 */
internal fun wirelessAdbFailureState(
    pairingRejected: Boolean,
    portUnavailable: Boolean,
): WirelessAdbAuthState = when {
    pairingRejected -> WirelessAdbAuthState.PairingRejected
    portUnavailable -> WirelessAdbAuthState.PortUnavailable
    else -> WirelessAdbAuthState.ConnectionFailed
}

/**
 * Whether the wireless transport actually works, as opposed to whether it was set up.
 *
 * A stored flag and a stored key are both records of the past: the device can forget this app at any
 * time, and nothing on this side changes when it does. So a saved credential is reported as
 * *unverified* rather than as ready, and only a connection that comes back with a shell identity is
 * reported as valid. The two are kept apart in the type ([WirelessAdbAuthState.SavedUnverified] versus
 * [WirelessAdbAuthState.Valid]) so no caller can accidentally treat one as the other.
 */
internal object WirelessAdbDiagnostics {

    fun passiveSnapshot(context: Context): WirelessAdbSnapshot {
        val keyPresent = AdbCredentialStore.hasStoredKey(context)
        val credentialFlag = AppPreferences.adbPaired(context)
        return WirelessAdbSnapshot(
            credentialFlag = credentialFlag,
            keyPresent = keyPresent,
            fingerprint = AdbCredentialStore.fingerprint(context),
            wirelessDebuggingEnabled = AdbPairing.isWirelessAdbEnabled(context),
            authState = if (keyPresent) {
                WirelessAdbAuthState.SavedUnverified
            } else {
                WirelessAdbAuthState.NoCredential
            },
            detail = when {
                credentialFlag && !keyPresent ->
                    "The recorded pairing is stale: this app's ADB key is gone, so pair again"
                !credentialFlag && keyPresent ->
                    "A key exists, but the device has not been asked whether it still accepts it"
                keyPresent -> "Saved; run the connection test to see whether the device still accepts it"
                else -> "No wireless-debugging credential is stored"
            },
        )
    }

    /**
     * Tests the transport for real, through a temporary window.
     *
     * The window is opened even when wireless debugging was off, and closed again afterwards, because
     * a test that left it on would be changing device state to answer a question.
     */
    suspend fun testConnection(context: Context): WirelessAdbSnapshot = withContext(Dispatchers.IO) {
        val initial = passiveSnapshot(context)
        if (!initial.keyPresent) {
            AppPreferences.setAdbPaired(context, false)
            return@withContext initial.copy(
                credentialFlag = false,
                authState = WirelessAdbAuthState.NoCredential,
                detail = "No wireless-debugging key is stored, so there is nothing to test",
            )
        }

        if (!AdbPairing.hasWriteSecureSettings(context)) {
            return@withContext initial.copy(
                authState = WirelessAdbAuthState.PermissionRequired,
                detail = "WRITE_SECURE_SETTINGS is required, and this build does not have it",
            )
        }

        var discoveredPort: Int? = null
        try {
            TemporaryWirelessAdb.use(context, settleMillis = ENABLE_SETTLE_MILLIS) {
                val port = AdbPairing.discoverConnectPort(context, DISCOVERY_TIMEOUT_MILLIS)
                if (port <= 0) throw ConnectPortUnavailableException()
                discoveredPort = port

                val result = LocalAdbClient.shellOnce(
                    host = "127.0.0.1",
                    port = port,
                    keyManager = AdbKeyManager(context),
                    command = "id",
                )
                // A shell that answered without an identity is not an authenticated session, whatever
                // its exit code said.
                if (result.exitCode != 0 || !result.output.contains("uid=")) {
                    throw IOException(
                        "The ADB shell gave no identity: ${result.output.trim().takeLast(180)}",
                    )
                }
            }

            AppPreferences.setAdbPaired(context, true)
            passiveSnapshot(context).copy(
                credentialFlag = true,
                connectPort = discoveredPort,
                authState = WirelessAdbAuthState.Valid,
                detail = "The device accepted this app's key and answered as a shell",
            )
        } catch (error: Throwable) {
            val pairingRejected = LocalAdbClient.isPairingLostError(error) ||
                LocalAdbClient.PAIRING_LOST_MARKER in (error.message ?: "")
            // A refused certificate is the device saying it no longer knows this app, so the recorded
            // pairing is cleared: keeping it would leave a flag that contradicts the device.
            if (pairingRejected) AppPreferences.setAdbPaired(context, false)

            val state = wirelessAdbFailureState(
                pairingRejected = pairingRejected,
                portUnavailable = error is ConnectPortUnavailableException,
            )
            passiveSnapshot(context).copy(
                credentialFlag = AppPreferences.adbPaired(context),
                connectPort = discoveredPort,
                authState = state,
                detail = when (state) {
                    WirelessAdbAuthState.PairingRejected ->
                        "The device rejected this app's certificate, so it has to be paired again"
                    WirelessAdbAuthState.PortUnavailable ->
                        "No wireless-debugging port was found, by the system property or by mDNS"
                    else -> error.message ?: error.javaClass.simpleName
                },
            )
        }
    }

    private class ConnectPortUnavailableException : IOException(
        "No wireless-debugging port was found, by the system property or by mDNS",
    )

    private const val ENABLE_SETTLE_MILLIS = 1_000L
    private const val DISCOVERY_TIMEOUT_MILLIS = 15_000L
}
