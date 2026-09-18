package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says when a list has nothing in it.
 *
 * An empty area and a broken one look the same, so a page that is empty for a reason has to say which
 * reason and what would fill it. The two emptinesses are not the same thing and are not answered the same
 * way: a list that is empty because nothing has happened yet needs a way to go and make something happen,
 * while a list that is empty because a filter excluded everything needs a way to undo the filter - and
 * naming three controls to change by hand is not that way.
 *
 * Held as a rule over every empty card rather than one assertion per card, so a fourth empty state added
 * later cannot be a blank area that looks broken.
 */
class EmptyStateTest {

    @Test
    fun `every empty card offers the action that fills it`() {
        val cards = emptyCards()
        assertTrue("no empty cards were found; the scan is looking at the wrong shape", cards.size >= 3)

        cards.forEach { (name, body) ->
            assertTrue(
                "$name has no action, so an empty area is all it can show",
                body.contains("FilledTonalButton(") || body.contains("Button("),
            )
        }
    }

    @Test
    fun `the way out of a filter clears every control that could have emptied the list`() {
        val source = mainActivity()
        val clear = source.substringAfter("onClearFilters = {", missingDelimiterValue = "")
            .substringBefore("},")

        assertTrue("no clear-filters action was found", clear.isNotBlank())
        listOf(
            "minLevel = AppLogLevel.Debug",
            "selectedTags = emptyList()",
            "query = \"\"",
        ).forEach { reset ->
            assertTrue(
                "clearing the log filters leaves $reset behind, so the list stays empty",
                clear.contains(reset),
            )
        }
    }

    @Test
    fun `an empty history leads to where a run is started`() {
        val source = mainActivity()

        assertTrue(
            "the empty history card is not offered the way to Home",
            source.contains("EmptyHistoryCard(onOpenHome)"),
        )
        assertTrue(
            "Home is not what the card's action goes to",
            source.contains("onOpenHome = { selectedPage = AppPage.Overview }"),
        )
    }

    /** Every `Empty…Card` composable, by name, with its body. */
    private fun emptyCards(): List<Pair<String, String>> {
        val source = mainActivity()
        return Regex("""private fun (Empty\w+)\(""")
            .findAll(source)
            .map { match -> match.groupValues[1] to source.substring(match.range.first) }
            // A card's body is indented, so the first closing brace at column zero is where it ends.
            .map { (name, fromStart) -> name to fromStart.substringBefore("\n}\n") }
            .toList()
    }

    private fun mainActivity(): String = listOf(
        File("src/main/java/dev/busung/s25uroot/MainActivity.kt"),
        File("app/src/main/java/dev/busung/s25uroot/MainActivity.kt"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("MainActivity was not found from ${File(".").absolutePath}")
}
