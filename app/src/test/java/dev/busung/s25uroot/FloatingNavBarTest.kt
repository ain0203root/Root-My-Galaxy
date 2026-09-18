package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The floating navigation bar: drawn over the pages, and cleared by everything it can cover.
 *
 * Both halves fail silently. A bar back in the scaffold's bottom slot looks identical on screen - it is the
 * same pill in the same place - and the only difference is the empty band it reserves at the bottom of every
 * page, which no screenshot of a full page shows. And a list that does not count the bar is a last row you
 * can only read by scrolling it under the pill, which reads as a layout bug on one screen out of five.
 *
 * Checked here rather than on a device, because both are properties of the source: one file decides where
 * the bar is drawn, and the height it takes is a single value.
 */
class FloatingNavBarTest {

    @Test
    fun `the bar is drawn over the pages, not given a strip of its own`() {
        val main = source("MainActivity.kt")

        assertFalse(
            "the bar is back in the scaffold's bottom slot, which reserves a strip at the bottom of every page",
            main.contains("bottomBar ="),
        )
        assertTrue(
            "nothing draws the bar over the pages, so it is either missing or inside a page",
            main.contains("\n        AppNavBar(\n"),
        )
    }

    @Test
    fun `the height the bar takes is one value`() {
        val owners = sourceFiles()
            .filter { it.readText().contains("NAV_BAR_HEIGHT = ") }
            .map(File::getName)

        assertEquals(
            "the bar's height is defined somewhere other than beside the bar",
            listOf("MainActivity.kt"),
            owners,
        )
    }

    @Test
    fun `the shared page wrapper clears the bar, under the rows and under the button`() {
        val shared = source("ScrollToTop.kt")

        assertTrue(
            "a page's last row would sit under the pill",
            shared.contains("bottom = contentPadding.calculateBottomPadding() + pageBottomClearance()"),
        )
        assertTrue(
            "the clearance is the button's room plus the bar's, and the larger of the two would leave a row under one of them",
            shared.contains("BACK_TO_TOP_CLEARANCE + NAV_BAR_HEIGHT"),
        )
        assertTrue(
            "the button would sit behind the pill, having only ever cleared the edge of the page",
            shared.contains("padding(20.dp).padding(bottom = NAV_BAR_HEIGHT)"),
        )
    }

    @Test
    fun `the pages that run under the pill fade what is under it`() {
        val owners = sourceFiles()
            .filter { it.readText().contains("BottomScrim") }
            .map(File::getName)
            .sorted()

        assertEquals(
            "the scrim is drawn somewhere other than the shared wrapper and the history list, which are " +
                "the two places a list runs under the bar",
            listOf("MainActivity.kt", "ScrollToTop.kt"),
            owners,
        )
        assertTrue(
            "the scrim fades over some height other than the bar's, so a row would still arrive at the pill",
            source("ScrollToTop.kt").contains(".height(NAV_BAR_HEIGHT)"),
        )
    }

    @Test
    fun `the scrim and the button agree on what a moved list is`() {
        // One definition, because the two have to agree and the failures of a disagreement are opposite:
        // a scrim over a list at its top, or a button that is missing when the list has run off the screen.
        val shared = source("ScrollToTop.kt")

        assertEquals(1, shared.split("firstVisibleItemIndex > 0").size - 1)
    }

    @Test
    fun `the history list, which draws its own, counts the bar twice`() {
        val main = source("MainActivity.kt")

        // Three, and each named: the value itself, the rows under the selection buttons, and the button
        // stack in that corner. A page that stops counting shows it as a last row under the pill.
        assertEquals(3, main.split("NAV_BAR_HEIGHT").size - 1)
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
