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
    fun `the page draws a header for every section`() {
        val page = source("MainActivity.kt")

        SettingsSection.entries.forEach { section ->
            assertTrue(
                "the ${section.name} section has no header, so nothing on the page opens it",
                page.contains("SettingsSectionHeader(SettingsSection.${section.name},"),
            )
        }
        assertEquals(
            "the page draws a header that is not a section, or draws one twice",
            SettingsSection.entries.size,
            page.split("SettingsSectionHeader(SettingsSection.").size - 1,
        )
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
