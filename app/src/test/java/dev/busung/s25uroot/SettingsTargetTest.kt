package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two rules behind "Open setting".
 *
 * A link that lands on the settings page and leaves the reader to find the row is the thing this
 * feature exists to avoid, so both halves are here: which extras are honoured, and how far the search
 * for a row walks before it gives up. The second is worth its own test because it is the part that can
 * fail silently - a search that stops one row short looks exactly like a card that is not there.
 */
class SettingsTargetTest {

    @Test
    fun `a target is taken only from the names this build knows`() {
        assertEquals(
            SettingsTarget.PartitionReadOnly,
            SettingsTarget.named(SettingsTarget.PartitionReadOnly),
        )
        assertNull(SettingsTarget.named(null))
        assertNull(SettingsTarget.named(""))
        // A key from a newer build, or a stray string from anywhere else, is not a jump to nowhere.
        assertNull(SettingsTarget.named("some_card_added_later"))
    }

    @Test
    fun `the search starts at the top, moves a row at a time, and stops at the end`() {
        // Nothing composed yet: the first look is at the top of the list.
        assertEquals(0, nextScrollSearchStep(visibleLastIndex = null, totalItems = 30))
        assertEquals(9, nextScrollSearchStep(visibleLastIndex = 8, totalItems = 30))
        // The last row has been looked at, so there is nothing left to search.
        assertNull(nextScrollSearchStep(visibleLastIndex = 29, totalItems = 30))
        assertNull(nextScrollSearchStep(visibleLastIndex = 30, totalItems = 30))
        // An empty list is not a place to look.
        assertNull(nextScrollSearchStep(visibleLastIndex = null, totalItems = 0))
        assertNull(nextScrollSearchStep(visibleLastIndex = 0, totalItems = 0))
    }
}
