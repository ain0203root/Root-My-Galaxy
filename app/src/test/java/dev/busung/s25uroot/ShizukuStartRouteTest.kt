package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStartRouteTest {

    @Test
    fun `a paired adb identity starts Shizuku without root`() {
        // The route this app did not have: no root, so Shizuku's starter has to be run in some other
        // shell, and the device's own adbd is the only one that needs neither a computer nor a network.
        assertEquals(
            ShizukuStartRoute.LocalAdb,
            shizukuStartRoute(
                rootShellAvailable = false,
                localAdbPaired = true,
                tokenConfigured = false,
            ),
        )
    }

    @Test
    fun `the adb route outranks the token request`() {
        // Both work without root. The adb one is preferred because the app runs the starter itself - so
        // it sees whether a binder followed - and because the request it replaces asks the Shizuku app
        // to start by a method that may need a wifi connection to do it.
        assertEquals(
            ShizukuStartRoute.LocalAdb,
            shizukuStartRoute(
                rootShellAvailable = false,
                localAdbPaired = true,
                tokenConfigured = true,
            ),
        )
    }

    @Test
    fun `root is preferred whenever it is available`() {
        // The native starter's result can be checked - the app sees the process it started - while a
        // request to another app can only be answered by waiting, so root wins even with a token set.
        assertEquals(
            ShizukuStartRoute.NativeStarter,
            shizukuStartRoute(
                rootShellAvailable = true,
                localAdbPaired = true,
                tokenConfigured = true,
            ),
        )
        assertEquals(
            ShizukuStartRoute.NativeStarter,
            shizukuStartRoute(
                rootShellAvailable = true,
                localAdbPaired = false,
                tokenConfigured = false,
            ),
        )
    }

    @Test
    fun `without root the token is the only route left`() {
        assertEquals(
            ShizukuStartRoute.AuthenticatedIntent,
            shizukuStartRoute(
                rootShellAvailable = false,
                localAdbPaired = false,
                tokenConfigured = true,
            ),
        )
    }

    @Test
    fun `a boot with a token and no root still asks Shizuku to start`() {
        // The case this exists for: the boot start used to live inside the root check, so a device
        // with a token and no root never asked, and the one route that works without root was the
        // one route a boot could not take.
        assertTrue(
            shizukuBootStartWorthAttempting(
                rootAlreadyActive = false,
                localAdbPaired = false,
                tokenConfigured = true,
            ),
        )
        assertTrue(
            shizukuBootStartWorthAttempting(
                rootAlreadyActive = true,
                localAdbPaired = false,
                tokenConfigured = false,
            ),
        )
    }

    @Test
    fun `a boot with a paired adb identity and no root starts Shizuku too`() {
        assertTrue(
            shizukuBootStartWorthAttempting(
                rootAlreadyActive = false,
                localAdbPaired = true,
                tokenConfigured = false,
            ),
        )
    }

    @Test
    fun `a boot with none of them is left alone rather than told it cannot be done`() {
        assertFalse(
            shizukuBootStartWorthAttempting(
                rootAlreadyActive = false,
                localAdbPaired = false,
                tokenConfigured = false,
            ),
        )
    }

    @Test
    fun `without either, the app says so instead of trying`() {
        // The wrong behaviour here is a broadcast sent with no credential to a privileged process
        // that must refuse it: the user would be told Shizuku failed to start rather than that this
        // app was never able to start it.
        assertEquals(
            ShizukuStartRoute.Unavailable,
            shizukuStartRoute(
                rootShellAvailable = false,
                localAdbPaired = false,
                tokenConfigured = false,
            ),
        )
    }
}
