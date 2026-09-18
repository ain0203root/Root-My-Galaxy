package dev.busung.s25uroot

import org.junit.Assert.assertEquals
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
    fun `a verdict and its label are one to one`() {
        // The label is what the notification's title and the history chip are made of, and two verdicts
        // wearing one word is the disagreement this file exists to end.
        val labels = RunVerdict.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
