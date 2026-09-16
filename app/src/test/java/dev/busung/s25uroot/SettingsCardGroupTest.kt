package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape a run of settings cards has to be in.
 *
 * A group is one rounded container: `Top` draws the rounded top and flat bottom, `Middle` is flat
 * both ways, `Bottom` closes it. The compiler is happy with any order, so a card filed as `Top`
 * between two others is invisible until someone looks at the screen - which is exactly how the
 * payloads group came to draw its sources card as the start of a second list.
 *
 * The positions are read out of the source because that is where the decision is written; the test
 * exists to fail when the order stops being one a group can be drawn from.
 */
class SettingsCardGroupTest {

    @Test
    fun `every group opens once and closes once`() {
        val positions = positionsInSource()
        assertTrue("no card positions found; the scan is looking at the wrong file", positions.isNotEmpty())

        var groupOpen = false
        positions.forEachIndexed { index, position ->
            when (position) {
                "Top" -> {
                    assertFalse(
                        "a card opened a group at #$index while another was still open",
                        groupOpen,
                    )
                    groupOpen = true
                }
                "Middle" -> assertTrue(
                    "a middle card at #$index has no group to belong to",
                    groupOpen,
                )
                "Bottom" -> {
                    assertTrue("a bottom card at #$index has no group to close", groupOpen)
                    groupOpen = false
                }
                "GroupedSingle" -> assertFalse(
                    "a card at #$index is a group of its own, inside another group",
                    groupOpen,
                )
                else -> throw AssertionError("card at #$index uses an unhandled position: $position")
            }
        }
        assertFalse("the last group was never closed", groupOpen)
    }

    @Test
    fun `each group is at most one top and one bottom`() {
        val positions = positionsInSource().filter { it != "GroupedSingle" }
        val groups = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        positions.forEach { position ->
            current.add(position)
            if (position == "Bottom") {
                groups.add(current)
                current = mutableListOf()
            }
        }

        assertTrue("no complete group was found", groups.isNotEmpty())
        groups.forEach { group ->
            assertEquals("a group starts with something other than a top", "Top", group.first())
            assertEquals("a group ends with something other than a bottom", "Bottom", group.last())
            assertTrue(
                "a group holds more than one top: $group",
                group.count { it == "Top" } == 1,
            )
        }
    }

    /**
     * The positions in the order the file declares them.
     *
     * Only the cards themselves count, and only those that name a position: a card that leaves it out
     * is a single on its own and belongs to no group. Matching the assignment rather than the enum
     * keeps the shape helper's own comparisons out of the sequence.
     */
    private fun positionsInSource(): List<String> {
        val source = settingsSource().readText()
        return Regex("""position = SettingsCardPosition\.(\w+)""")
            .findAll(source)
            .map { match -> match.groupValues[1] }
            .filter { position -> position != "Single" }
            .toList()
    }

    private fun settingsSource(): File {
        val candidates = listOf(
            File("src/main/java/dev/busung/s25uroot/MainActivity.kt"),
            File("app/src/main/java/dev/busung/s25uroot/MainActivity.kt"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: throw AssertionError(
                "MainActivity.kt was not found from ${File(".").absolutePath}",
            )
    }
}
