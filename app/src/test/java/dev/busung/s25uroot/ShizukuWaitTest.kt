package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate's rule for Shizuku, which is the one place a boot can be held back by a setting.
 *
 * The cases worth writing down are the ones that cannot be tried by hand: a phone whose Shizuku is
 * switched off in settings but present on the device, and one that asked for Shizuku with nothing able
 * to start it - the second is the case the wait would spend two minutes discovering.
 */
class ShizukuWaitTest {

    @Test
    fun `a run that did not ask for Shizuku waits for nothing`() {
        // Even with Shizuku up and usable: asking is what makes it the run's transport.
        assertEquals(
            ShizukuWait.NotRequested,
            shizukuWait(requested = false, usable = true, startable = true),
        )
    }

    @Test
    fun `a usable Shizuku is taken without waiting`() {
        assertEquals(
            ShizukuWait.Ready,
            shizukuWait(requested = true, usable = true, startable = true),
        )
    }

    @Test
    fun `asking for Shizuku on a device that can start it means hold`() {
        assertEquals(
            ShizukuWait.Await,
            shizukuWait(requested = true, usable = false, startable = true),
        )
    }

    @Test
    fun `asking for Shizuku with no way to start it is refused, not waited for`() {
        // The wait would end by saying this same thing, two minutes later, in front of the one attempt
        // this boot gets.
        assertEquals(
            ShizukuWait.Unstartable,
            shizukuWait(requested = true, usable = false, startable = false),
        )
    }

    @Test
    fun `a usable Shizuku outranks the question of starting one`() {
        // The two readings are not ordered by what the device can do, and a device that cannot start
        // Shizuku is still a device that can use the one already running.
        assertEquals(
            ShizukuWait.Ready,
            shizukuWait(requested = true, usable = true, startable = false),
        )
    }
}
