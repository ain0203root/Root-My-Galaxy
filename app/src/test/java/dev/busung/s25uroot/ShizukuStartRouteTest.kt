package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStartRouteTest {

    @Test
    fun `root is preferred whenever it is available`() {
        // The native starter's result can be checked - the app sees the process it started - while a
        // request to another app can only be answered by waiting, so root wins even with a token set.
        assertEquals(
            ShizukuStartRoute.NativeStarter,
            shizukuStartRoute(rootShellAvailable = true, tokenConfigured = true),
        )
        assertEquals(
            ShizukuStartRoute.NativeStarter,
            shizukuStartRoute(rootShellAvailable = true, tokenConfigured = false),
        )
    }

    @Test
    fun `without root the token is the only route left`() {
        assertEquals(
            ShizukuStartRoute.AuthenticatedIntent,
            shizukuStartRoute(rootShellAvailable = false, tokenConfigured = true),
        )
    }

    @Test
    fun `a boot with a token and no root still asks Shizuku to start`() {
        // The case this exists for: the boot start used to live inside the root check, so a device
        // with a token and no root never asked, and the one route that works without root was the
        // one route a boot could not take.
        assertTrue(
            shizukuBootStartWorthAttempting(rootAlreadyActive = false, tokenConfigured = true),
        )
        assertTrue(
            shizukuBootStartWorthAttempting(rootAlreadyActive = true, tokenConfigured = false),
        )
    }

    @Test
    fun `a boot with neither is left alone rather than told it cannot be done`() {
        assertFalse(
            shizukuBootStartWorthAttempting(rootAlreadyActive = false, tokenConfigured = false),
        )
    }

    @Test
    fun `without either, the app says so instead of trying`() {
        // The wrong behaviour here is a broadcast sent with no credential to a privileged process
        // that must refuse it: the user would be told Shizuku failed to start rather than that this
        // app was never able to start it.
        assertEquals(
            ShizukuStartRoute.Unavailable,
            shizukuStartRoute(rootShellAvailable = false, tokenConfigured = false),
        )
    }
}
