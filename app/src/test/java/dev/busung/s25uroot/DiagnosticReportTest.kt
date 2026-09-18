package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The block that gets pasted when someone asks why it did not work. */
class DiagnosticReportTest {

    private fun facts(
        preflight: String? = null,
        logTail: List<String> = listOf("09-18 21:04:33.123 I Run started"),
    ) = DiagnosticFacts(
        appLabel = "0.2.65 (13)",
        deviceLabel = "SM-S9360 6.6.98-android15-8-build",
        kernelLabel = "#1 SMP PREEMPT",
        bootLabel = "b1c2d3",
        rootLabel = "KernelSU active, flavour KernelSU, manager 3.3.0 installed, daemon 3.3.0",
        shizukuLabel = "use on, running and granted, wireless ADB not paired",
        settingsLabel = "payload mode online, image guard on",
        lastRunLabel = "09-18 20:58:00.000 failed at kernelsu - the daemon did not answer",
        residueLabel = "Staging: nothing this app wrote is left in /data/local/tmp",
        preflightLabel = preflight,
        logTail = logTail,
    )

    @Test
    fun `the two things nobody remembers to include are at the top`() {
        val text = diagnosticReport(facts())
        assertTrue(text.startsWith("Root-My-Galaxy report"))
        assertTrue(text.contains("App: 0.2.65 (13)"))
        assertTrue(text.contains("Device: SM-S9360"))
    }

    @Test
    fun `a report says which run failed and where it stopped`() {
        val text = diagnosticReport(facts())
        assertTrue(text.contains("Last run:"))
        assertTrue(text.contains("failed at kernelsu"))
    }

    @Test
    fun `the check goes in whole, before the log`() {
        val text = diagnosticReport(facts(preflight = "Pre-flight: Blocked\n- Payload: Stops the run - nothing\n"))
        val check = text.indexOf("Pre-flight: Blocked")
        val log = text.indexOf("App log (newest last):")

        assertTrue(check > 0)
        assertTrue("the log is not after the check", log > check)
    }

    @Test
    fun `an empty log says so rather than ending the report`() {
        val text = diagnosticReport(facts(logTail = emptyList()))
        assertTrue(text.contains("(nothing logged yet)"))
    }

    @Test
    fun `a long log is a tail, and says how much it left out`() {
        val lines = (1..200).map { "line $it" }
        val tail = reportLogTail(lines, limit = 60)

        assertEquals(61, tail.size)
        assertEquals("(140 earlier lines not shown)", tail.first())
        assertEquals("line 200", tail.last())
        assertFalse(tail.contains("line 100"))
    }

    @Test
    fun `a log that fits is kept whole and unmarked`() {
        val lines = (1..10).map { "line $it" }
        assertEquals(lines, reportLogTail(lines, limit = 60))
    }

    @Test
    fun `a report with no check still ends with the log`() {
        val text = diagnosticReport(facts(preflight = null))
        assertFalse(text.contains("Pre-flight"))
        assertTrue(text.trimEnd().lines().last().startsWith("09-18"))
    }
}
