package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The back-to-top button: where it is drawn, and the one screen it must give way to.
 *
 * Both rules fail silently if they break. A button drawn by hand on a screen that needs it gated looks
 * right in every screenshot and only misbehaves under a thumb, and the run screen's stand-down is the
 * difference between a control that can be pressed and one that cannot - which is the failure this app has
 * already had once, in a run that could not be stopped.
 */
class BackToTopTest {

    @Test
    fun `the button is drawn in one place, so no screen can place its own`() {
        val owners = sourceFiles()
            .filter { it.readText().contains("SmallFloatingActionButton") }
            .map(File::getName)

        assertEquals(listOf("ScrollToTop.kt"), owners)
    }

    @Test
    fun `the run screen tells the page where its controls begin`() {
        val text = source("InstallActivity.kt")

        assertTrue(text.contains("controlsTop = coordinates.positionInWindow().y"))
        assertTrue(text.contains("controlsTop = controlsTop"))
    }

    @Test
    fun `the block it measures is the one the run's controls live in`() {
        val text = source("InstallActivity.kt")
        val measured = text.indexOf("controlsTop = coordinates.positionInWindow().y")
        val stop = text.indexOf("R.string.action_stop_run")
        val retry = text.indexOf("R.string.action_retry")

        // Ahead of both, because both are inside the column that measurement wraps: a measurement taken
        // after the buttons would report a position the button never stands down for.
        assertTrue(measured > 0 && stop > measured && retry > measured)
    }

    @Test
    fun `every tab page goes through the wrapper, and the one that does not draws the button itself`() {
        val main = source("MainActivity.kt")

        // Overview, the run detail, Logs and Settings go through the wrapper. The history list is the
        // exception, and it is the only place in this window that names the button directly - because the
        // export and delete buttons already occupy that corner while a selection is live.
        assertEquals(4, main.split("PageList(").size - 1)
        // Three of the four own their state through the wrapper's helper; Settings passes the state it
        // already holds, because that page jumps to a card by it.
        assertEquals(4, main.split("rememberPageListState()").size - 1)
        assertEquals(1, main.split("BackToTopFab(").size - 1)
    }

    private fun source(name: String): String {
        val file = sourceFiles().firstOrNull { it.name == name }
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
