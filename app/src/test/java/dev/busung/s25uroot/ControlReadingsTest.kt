package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a refusal says about the three readings behind it.
 *
 * They have nothing in common - the native paths are hidden by policy on this hardware, `su` answers
 * only for the manager KernelSU knows, and the helper prints a report only when the load reached the
 * point of having one - so a single sentence covers a healthy load the app could not see and a load
 * that never happened. The summary is the part that tells them apart.
 */
class ControlReadingsTest {

    @Test
    fun `a silent helper is reported as silence, not as a failed report`() {
        val summary = readings(helperOutput = "").summary()
        assertTrue(summary, summary.contains("helper printed nothing"))
    }

    @Test
    fun `a helper that spoke without a report says how much it said`() {
        val summary = readings(helperOutput = "line one\nline two\n").summary()
        assertTrue(summary, summary.contains("printed 2 line(s) without a control report"))
    }

    @Test
    fun `a live report is named as one`() {
        val report = "KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3"
        val summary = readings(helperOutput = report).summary()
        assertTrue(summary, summary.contains("helper reported a live control channel"))
    }

    @Test
    fun `a native probe that could not see KernelSU gives the app-side su reason`() {
        val summary = readings(
            nativeProbe = false,
            appSuFailure = SuProbe.Failure.ABSENT,
        ).summary()
        assertTrue(summary, summary.contains("native probe no (the app's own su: absent)"))
    }

    @Test
    fun `a reading that succeeded does not carry a failure with it`() {
        assertEquals(
            "native probe yes; su through Shizuku yes; helper printed nothing",
            readings(nativeProbe = true, shizukuElevated = true, helperOutput = "").summary(),
        )
    }

    private fun readings(
        nativeProbe: Boolean = false,
        appSuFailure: SuProbe.Failure = SuProbe.Failure.NONE,
        shizukuElevated: Boolean = false,
        helperOutput: String = "",
    ) = ControlReadings(
        nativeProbe = nativeProbe,
        appSuFailure = appSuFailure,
        shizukuElevated = shizukuElevated,
        helperOutput = helperOutput,
    )
}
