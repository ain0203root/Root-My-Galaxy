package dev.busung.s25uroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTerminationTest {

    @Test
    fun `a retry in this boot needs a confirmed stop`() {
        // The rule the whole type exists for: a second payload on top of a live one is the one thing
        // the device cannot take, so a retry that runs in this boot is only safe once the first
        // payload was observed dead.
        assertTrue(Termination.Confirmed.inBootRetryIsSafe)
        assertFalse(Termination.Unconfirmed.inBootRetryIsSafe)
    }

    @Test
    fun `a run whose payload may still be running carries that on the failure`() {
        val failure = RunFailure.of(
            stage = RunStage.Exploit,
            reason = "The payload did not finish before its ceiling",
            payloadMayStillRun = true,
        )
        assertTrue(failure.payloadMayStillRun)
    }

    @Test
    fun `an ordinary failure does not claim a payload is running`() {
        // Defaulted, so no existing failure starts saying this: a notice that appears on failures it
        // does not apply to is a notice people learn to ignore.
        assertFalse(RunFailure.of(RunStage.Exploit, "boom").payloadMayStillRun)
    }

    @Test
    fun `the stop is given long enough to observe an exit, and not long enough to hold the screen`() {
        assertTrue(TERMINATION_GRACE_MILLIS >= 1_000L)
        assertTrue(TERMINATION_GRACE_MILLIS <= 10_000L)
    }
}
