package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootSettleTest {

    @Test
    fun `the default is a wait, not zero`() {
        // The gate exists because a cold device makes the racy stage worse, so shipping it off would
        // ship it not at all.
        assertTrue(BootSettle.DEFAULT_SECONDS > 0)
        assertTrue(BootSettle.allowedSeconds.contains(BootSettle.DEFAULT_SECONDS))
    }

    @Test
    fun `the automatic floor is its own value, shorter than the manual one`() {
        // Two settings, two owners: a person tuning automation is not deciding how long a manual run
        // pauses, so the automatic floor must be able to move without the manual one following.
        assertTrue(BootSettle.allowedSeconds.contains(BootSettle.AUTO_ROOT_DEFAULT_SECONDS))
        assertTrue(BootSettle.AUTO_ROOT_DEFAULT_SECONDS > 0)
        assertTrue(BootSettle.AUTO_ROOT_DEFAULT_SECONDS < BootSettle.DEFAULT_SECONDS)
    }

    @Test
    fun `a stored value is rounded to one of the offered ones`() {
        assertEquals(180, BootSettle.normalize(180))
        assertEquals(120, BootSettle.normalize(119))
        assertEquals(300, BootSettle.normalize(400))
        assertEquals(0, BootSettle.normalize(-5))
    }

    @Test
    fun `a device already past the gate waits not at all`() {
        // Measured from the boot: two minutes of uptime satisfy a two minute gate.
        assertEquals(0L, BootSettle.remainingMillis(120, 120_000L))
        assertEquals(0L, BootSettle.remainingMillis(120, 900_000L))
    }

    @Test
    fun `a device just booted waits the rest of the gate`() {
        assertEquals(90_000L, BootSettle.remainingMillis(120, 30_000L))
        assertEquals(120_000L, BootSettle.remainingMillis(120, 0L))
    }

    @Test
    fun `the gate is off when it is set to zero`() {
        assertEquals(0L, BootSettle.remainingMillis(0, 0L))
    }

    @Test
    fun `a countdown reads as minutes and seconds, rounded up`() {
        assertEquals("1:42", BootSettle.formatRemaining(101_500L))
        assertEquals("2:00", BootSettle.formatRemaining(120_000L))
        assertEquals("0:09", BootSettle.formatRemaining(8_400L))
        // Never 0:00 while there is still something to wait for.
        assertEquals("0:01", BootSettle.formatRemaining(1L))
        assertEquals("0:00", BootSettle.formatRemaining(0L))
        assertEquals("0:00", BootSettle.formatRemaining(-5L))
    }

    @Test
    fun `a label says what was chosen`() {
        assertEquals("Off", BootSettle.label(0))
        assertEquals("30 s", BootSettle.label(30))
        assertEquals("1 min", BootSettle.label(60))
        assertEquals("1 min 30 s", BootSettle.label(90))
        assertEquals("2 min", BootSettle.label(120))
        assertEquals("5 min", BootSettle.label(300))
        assertEquals("10 min", BootSettle.label(600))
    }
}
