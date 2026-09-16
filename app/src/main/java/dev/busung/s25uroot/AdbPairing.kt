package dev.busung.s25uroot

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The port the pairing service published, from the output of a property read.
 *
 * Takes the first non-empty line so a stray warning on the way in cannot be read as a port, and
 * refuses anything outside the port range rather than passing it to a socket call.
 */
internal fun parseAdbTlsPort(raw: String): Int? = raw
    .lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.toIntOrNull()
    ?.takeIf { it in 1..65535 }

/**
 * Wireless debugging's state, and the loopback port adbd is listening on.
 *
 * Everything here is about the device's own settings rather than about this app: whether wireless
 * debugging is on, whether this app may turn it on, and which port it publishes. The pairing exchange
 * itself is [AdbPairingClient], and the UX around it is [AdbPairingService].
 */
object AdbPairing {

    private const val TAG = "RootMyGalaxyAdb"
    private const val ADB_WIFI_ENABLED_SETTING = "adb_wifi_enabled"
    private const val ADB_TLS_PORT_PROPERTY = "service.adb.tls.port"
    private const val PROPERTY_READ_TIMEOUT_MS = 750L
    private const val PROPERTY_POLL_INTERVAL_MS = 1_000L

    /** Turns wireless debugging on, which needs WRITE_SECURE_SETTINGS that only a rooted device has. */
    fun enableWirelessAdb(context: Context): Boolean = try {
        Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED_SETTING, 1)
    } catch (error: SecurityException) {
        Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted, so wireless debugging cannot be turned on")
        false
    }

    /**
     * Turns wireless debugging off.
     *
     * Only ever called to restore a state this app changed: leaving it on after a temporary use would
     * be leaving a shell port open that the user did not ask for.
     */
    fun disableWirelessAdb(context: Context): Boolean = try {
        Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED_SETTING, 0)
    } catch (error: SecurityException) {
        Log.w(TAG, "WRITE_SECURE_SETTINGS is not granted, so wireless debugging cannot be turned off")
        false
    }

    fun isWirelessAdbEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED_SETTING, 0) == 1
    }.getOrDefault(false)

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The local port adbd is listening on, without making Wi-Fi a prerequisite.
     *
     * The published property is asked first, because it works with no network at all and is
     * authoritative about this boot. mDNS is the fallback for a device that does not publish it, and
     * both are polled until the deadline because the port only exists while wireless debugging is up -
     * a lookup that raced the setting being turned on would otherwise report nothing and give up.
     *
     * The result is only ever connected to over loopback.
     */
    fun discoverConnectPort(context: Context, timeoutMs: Long = 15_000): Int {
        readTlsPortProperty()?.let { port ->
            Log.i(TAG, "Local ADB port from $ADB_TLS_PORT_PROPERTY: $port")
            return port
        }

        val found = CountDownLatch(1)
        val discoveredPort = AtomicInteger(-1)
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { discovered ->
            if (discovered > 0 && discoveredPort.compareAndSet(-1, discovered)) found.countDown()
        }
        mdns.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0L))
            while (discoveredPort.get() <= 0) {
                readTlsPortProperty()?.let { port ->
                    discoveredPort.compareAndSet(-1, port)
                    Log.i(TAG, "Local ADB port found without mDNS: $port")
                    break
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val waitMillis = minOf(
                    PROPERTY_POLL_INTERVAL_MS,
                    TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L),
                )
                if (found.await(waitMillis, TimeUnit.MILLISECONDS)) break
            }
        } finally {
            mdns.stop()
        }

        val port = discoveredPort.get().takeIf { it > 0 } ?: readTlsPortProperty() ?: -1
        if (port <= 0) Log.w(TAG, "No local ADB port was found, by property or by mDNS")
        return port
    }

    /**
     * Whether adbd currently accepts this app's key.
     *
     * Asked of the device by running `id` over the transport, because the stored "paired" flag only
     * records that a pairing once happened; the device can forget the key at any time.
     */
    fun testConnection(context: Context): Boolean {
        val port = discoverConnectPort(context)
        if (port <= 0) return false
        return runCatching {
            LocalAdbClient.shellOnce("127.0.0.1", port, AdbKeyManager(context), "id")
                .output
                .contains("uid=")
        }.getOrElse { error ->
            Log.w(TAG, "Local ADB connection test failed: ${error.message}")
            false
        }
    }

    private fun readTlsPortProperty(): Int? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", ADB_TLS_PORT_PROPERTY)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(PROPERTY_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            runCatching { process.waitFor(100, TimeUnit.MILLISECONDS) }
            if (process.isAlive) process.destroyForcibly()
            return@runCatching null
        }
        if (process.exitValue() != 0) return@runCatching null
        parseAdbTlsPort(process.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()
}
