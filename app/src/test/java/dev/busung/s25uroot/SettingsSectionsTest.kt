package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The settings page's sections, and the two ways a closed one can go wrong silently.
 *
 * A card another screen points at that no section claims is a jump that opens nothing and scrolls nowhere -
 * the person is put on the settings page with no row to look at, and nothing anywhere says so. And a
 * section whose cards are drawn outside its guard is a section that looks closed while its switches are
 * still on screen, which is how a collapse feature ends up trusted less than no feature at all.
 *
 * Both are checked here rather than on a device, because both are properties of the source.
 */
class SettingsSectionsTest {

    @Test
    fun `every card another screen can ask for belongs to a section`() {
        SettingsTarget.all.forEach { target ->
            assertNotNull(
                "nothing claims $target, so a jump to it would open nothing",
                SettingsSection.holding(target),
            )
        }
    }

    @Test
    fun `no section claims a key that is not a card`() {
        val claimed = SettingsSection.entries.flatMap { it.targets }

        assertEquals(
            "a section names a key no screen can ask for, which is a claim nothing can check",
            emptyList<String>(),
            claimed.filterNot { it in SettingsTarget.all },
        )
    }

    @Test
    fun `the read-only switch is in the run section`() {
        // Named rather than left to the loop above: this is the jump the run screen's failure card makes,
        // and it is the one that has to land on a switch rather than on the top of the page.
        assertEquals(
            SettingsSection.Run,
            SettingsSection.holding(SettingsTarget.PartitionReadOnly),
        )
    }

    @Test
    fun `a key that is not a card has no section`() {
        // Null rather than a default: guessing would scroll somewhere nobody asked for.
        assertNull(SettingsSection.holding("not_a_card"))
        assertNull(SettingsSection.holding(""))
    }

    @Test
    fun `stored names survive, and names from another build are dropped`() {
        val stored = setOf(
            SettingsSection.Root.name,
            SettingsSection.Recovery.name,
            // A section this build no longer has: About moved out of the settings page entirely, and a
            // stored name left by a build that still had it must be dropped rather than crash the page
            // on the way in.
            "About",
            "ASectionThisBuildDoesNotHave",
        )

        assertEquals(
            setOf(SettingsSection.Root, SettingsSection.Recovery),
            SettingsSection.named(stored),
        )
    }

    @Test
    fun `a page with nothing stored opens no section`() {
        assertEquals(emptySet<SettingsSection>(), SettingsSection.named(emptySet()))
    }

    @Test
    fun `every section is found by an icon of its own`() {
        val icons = SettingsSection.entries.map { it.icon }

        assertEquals(
            "two sections share an icon, and a glyph cannot be read slowly: the eye keeps landing on the " +
                "wrong row of the index",
            icons.size,
            icons.distinct().size,
        )
    }

    @Test
    fun `the index is one card, so only its ends are rounded`() {
        val first = SettingsSection.entries.first()
        val last = SettingsSection.entries.last()

        assertEquals(SettingsCardPosition.Top, first.indexPosition())
        assertEquals(SettingsCardPosition.Bottom, last.indexPosition())
        // Everything between them has to be square on both ends, which is what makes the eight rows read as
        // one card rather than as eight: a single Middle row out of place leaves a rounded seam in the
        // middle of it, and nothing on screen says which row that is.
        SettingsSection.entries.drop(1).dropLast(1).forEach { section ->
            assertEquals(
                "${section.name} sits between two others and is drawn as a card of its own",
                SettingsCardPosition.Middle,
                section.indexPosition(),
            )
        }
    }

    @Test
    fun `the page's own spacing is the seam between index rows`() {
        val page = source("MainActivity.kt")

        // The page's arrangement is what makes the index one card: at a group's gap the eight rows would be
        // eight cards, which is the layout this replaced. Read from the settings list's own call rather than
        // from anywhere in the file, since a group's inner spacing is the same number for its own reason.
        val arrangement = page.substringAfter("listState = settingsList,")
            .substringAfter("verticalArrangement = ")
            .substringBefore(",")
        assertEquals(
            "the settings page leaves a gap between index rows, so the index is not one card",
            "Arrangement.spacedBy(SETTINGS_CARD_SEAM)",
            arrangement,
        )
    }

    @Test
    fun `the page draws a header for every section`() {
        val called = headerCalls(source("MainActivity.kt"))

        assertEquals(
            "the page draws a header that is not a section, or draws one twice",
            SettingsSection.entries.map { it.name }.toSet(),
            called.toSet(),
        )
        assertEquals("a section has no header, so nothing on the page opens it", called.size, called.distinct().size)
    }

    @Test
    fun `every section's rows sit behind that section's guard`() {
        val page = source("MainActivity.kt")

        SettingsSection.entries.forEach { section ->
            assertTrue(
                "the ${section.name} section has no guarded rows: its cards would stay on screen " +
                    "while the section reads as closed",
                page.contains("if (SettingsSection.${section.name} in openSections) item"),
            )
        }
    }

    /**
     * The sections the page draws an index row for, in the order they appear.
     *
     * Matched with the line wrapping left open, because the call wraps as soon as a row is given a value and
     * a search for the unwrapped text is a test that quietly stops looking at four of the eight rows.
     */
    private fun headerCalls(page: String): List<String> =
        Regex("SettingsSectionHeader\\(\\s*SettingsSection\\.(\\w+),")
            .findAll(page)
            .map { it.groupValues[1] }
            .toList()

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
