package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three states, and the one that used to have nowhere to be.
 *
 * The settings row branched on two facts it never asked about separately - whether a start was in
 * flight, and whether a binder answered - so "Shizuku is up but this app is not allowed to use it" fell
 * through to the same rendering as "Shizuku is not running", and the row kept offering to start a
 * service that was already up. The middle state is the one worth pinning: it is not a start problem.
 */
class ShizukuAvailabilityTest {

    @Test
    fun `no binder is not running`() {
        assertEquals(
            ShizukuAvailability.NotRunning,
            shizukuAvailability(running = false, granted = false),
        )
    }

    @Test
    fun `running without the grant is its own state`() {
        assertEquals(
            ShizukuAvailability.WithoutPermission,
            shizukuAvailability(running = true, granted = false),
        )
    }

    @Test
    fun `running and granted is ready`() {
        assertEquals(
            ShizukuAvailability.Ready,
            shizukuAvailability(running = true, granted = true),
        )
    }

    @Test
    fun `a grant without a service is still not running`() {
        // The precedence, stated: a permission answers a question about a service that is not there, so
        // it cannot make the app ready. `isGranted()` already implies a binder in the live reading; this
        // pins the mapping on its own so the two cannot disagree.
        assertEquals(
            ShizukuAvailability.NotRunning,
            shizukuAvailability(running = false, granted = true),
        )
    }
}
