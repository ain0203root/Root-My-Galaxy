package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The run screen's bar: when it exists, and the two things that have to hold about where its controls are.
 *
 * The rule fails quietly in one direction only - a phase that offers something but is not listed draws no bar,
 * and the screen then has controls nowhere: not on the page any more, and not in the bar either. It is the
 * kind of gap that shows up as a run that cannot be stopped, which is the one thing a hung run needs.
 *
 * The other two are properties of the source and both were bugs once: the controls sat at the end of the
 * scrolling page, so the log ended against them, and the button over the page had to be told where they began
 * - a measurement that had to be taken in the right place to work at all.
 */
class RunActionBarTest {

    @Test
    fun `every phase that offers a control says so`() {
        // Busy is every phase the app is working through, including the settle wait, and each has a stop.
        InstallPhase.entries.forEach { phase ->
            val busy = phase in setOf(
                InstallPhase.Checking,
                InstallPhase.Settling,
                InstallPhase.Downloading,
                InstallPhase.Exploiting,
                InstallPhase.LoadingKernelSu,
            )
            val expected = busy || phase in setOf(
                InstallPhase.Failed,
                InstallPhase.Stopped,
                InstallPhase.Installed,
                InstallPhase.RootOnly,
            )

            assertEquals("$phase", expected, runControlsOffered(phase, busy))
        }
    }

    @Test
    fun `the screen open before a run has started has no bar`() {
        // The one phase that is neither working nor finished: nothing to stop and nothing to do afterwards,
        // and an empty pill over the page would be the bar pretending otherwise.
        assertFalse(runControlsOffered(InstallPhase.Ready, busy = false))
    }

    @Test
    fun `busy is read from the state rather than recomputed`() {
        // The screen asks the state what it is doing and hands the answer on: a second list of which phases
        // are busy would be a second place for the two to disagree.
        assertTrue(source("InstallActivity.kt").contains("runControlsOffered(installState.phase, installState.busy)"))
    }

    @Test
    fun `the run's controls are in the bar and not at the end of the page`() {
        val text = source("InstallActivity.kt")
        val bar = text.indexOf("RunActionBar {")

        assertTrue("nothing draws the run's bar", bar > 0)
        listOf(
            "R.string.action_stop_run",
            "R.string.action_retry",
            "R.string.action_run_now",
            "R.string.action_done",
        ).forEach { control ->
            val at = text.indexOf(control)
            assertTrue("$control is not on this screen any more", at > 0)
            assertTrue("$control is still composed on the page rather than in the bar", at > bar)
        }
    }

    @Test
    fun `the page is given room for the bar it floats under`() {
        val shared = source("ScrollToTop.kt")

        // Measured in the one place that draws both, so a screen that floats a bar cannot forget to end clear
        // of it - and cannot hard-code a height that stops matching as the bar's own row changes.
        assertTrue(shared.contains("bar: @Composable BoxScope.() -> Unit = {}"))
        assertTrue(shared.contains("Spacer(Modifier.height(barHeight + BACK_TO_TOP_CLEARANCE))"))
        assertTrue(shared.contains("pageBottomInset(barHeight)"))
    }

    private fun source(name: String): String {
        val file = candidateRoots()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .firstOrNull()
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
