package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one vocabulary four surfaces now share. */
class RunVerdictTest {

    @Test
    fun `a run in flight is one verdict whatever stage it is in`() {
        // Busy is the state the app is in for five of the nine phases, and the surface that shows it says
        // "running": a stage-by-stage colour would be movement for its own sake.
        InstallPhase.entries.forEach { phase ->
            val busy = phase in setOf(
                InstallPhase.Checking,
                InstallPhase.Settling,
                InstallPhase.Downloading,
                InstallPhase.Exploiting,
                InstallPhase.LoadingKernelSu,
            )
            if (busy) assertEquals("$phase", RunVerdict.Running, runVerdict(phase, busy))
        }
    }

    @Test
    fun `the four ways a run can end get four different verdicts`() {
        assertEquals(RunVerdict.Succeeded, runVerdict(InstallPhase.Installed, busy = false))
        assertEquals(RunVerdict.RootOnly, runVerdict(InstallPhase.RootOnly, busy = false))
        assertEquals(RunVerdict.Failed, runVerdict(InstallPhase.Failed, busy = false))
        assertEquals(RunVerdict.Stopped, runVerdict(InstallPhase.Stopped, busy = false))
    }

    @Test
    fun `root without KernelSU is neither of the two outcomes it sits between`() {
        // The reason the vocabulary has this state at all: it is a run that worked and a phone that is not
        // running KernelSU, and both neighbours would be a lie about one half of that.
        val rootOnly = runVerdict(InstallPhase.RootOnly, busy = false)
        assertTrue(rootOnly != RunVerdict.Succeeded)
        assertTrue(rootOnly != RunVerdict.Failed)
    }

    @Test
    fun `a screen with nothing behind it is not a failure`() {
        assertEquals(RunVerdict.Idle, runVerdict(InstallPhase.Ready, busy = false))
    }

    @Test
    fun `every stored result maps to a verdict of its own`() {
        // Exhaustive by construction - this is what makes the history rows and the live card agree - and the
        // assertion that matters is that the mapping is one to one rather than collapsing two into one.
        val verdicts = InstallRunResult.entries.map { runVerdict(it) }
        assertEquals(InstallRunResult.entries.size, verdicts.toSet().size)
        assertEquals(RunVerdict.Running, runVerdict(InstallRunResult.Running))
        assertEquals(RunVerdict.Succeeded, runVerdict(InstallRunResult.Succeeded))
        assertEquals(RunVerdict.RootOnly, runVerdict(InstallRunResult.RootOnly))
        assertEquals(RunVerdict.Failed, runVerdict(InstallRunResult.Failed))
        assertEquals(RunVerdict.Stopped, runVerdict(InstallRunResult.Stopped))
    }

    @Test
    fun `no verdict is painted in the role kept for warnings`() {
        // The verdict is the colour of the install button itself, so this is what a screenshot of a run is
        // mostly made of. Checked as source because `verdictColors` reads the theme and cannot be called from
        // a plain unit test, and because the failure is invisible until somebody looks at a phone mid-run: a
        // run painted in the caution role reads as something having gone wrong when nothing has.
        val colors = source("RunVerdict.kt").substringAfter("internal fun verdictColors")

        assertFalse(
            "a verdict is wearing the caution role again, which a run in flight is not",
            colors.contains("tertiary"),
        )
        // The branch itself, with the line breaks normalised so this pins the role rather than a formatting.
        val running = colors.substringAfter("RunVerdict.Running ->")
            .substringBefore("RunVerdict.Succeeded ->")
            .replace(Regex("\\s+"), " ")
        assertTrue(
            "the running verdict is no longer on the accent",
            running.contains("VerdictColors(scheme.primaryContainer"),
        )
    }

    @Test
    fun `a verdict and its label are one to one`() {
        // The label is what the notification's title and the history chip are made of, and two verdicts
        // wearing one word is the disagreement this file exists to end.
        val labels = RunVerdict.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    private fun source(name: String): String {
        val file = listOf(File("src/main/java"), File("app/src/main/java"))
            .filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .firstOrNull()
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }
}
