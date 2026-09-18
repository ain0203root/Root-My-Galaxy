package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Logs tab's filter, and the tag row it draws from. */
class LogFilterTest {

    private fun entry(
        level: AppLogLevel,
        tag: String,
        message: String = "line",
        atMillis: Long = 1_700_000_000_000,
    ) = AppLogEntry(atMillis = atMillis, level = level, tag = tag, message = message)

    private val log = listOf(
        entry(AppLogLevel.Info, AppLogTags.SHIZUKU, "binder came up"),
        entry(AppLogLevel.Debug, AppLogTags.SHIZUKU, "start requested"),
        entry(AppLogLevel.Warn, AppLogTags.KERNEL_SU, "daemon did not answer"),
        entry(AppLogLevel.Error, AppLogTags.KERNEL_SU, "load refused"),
        entry(AppLogLevel.Error, AppLogTags.RUN, "exploit failed"),
        entry(AppLogLevel.Info, AppLogTags.CATALOG, "fetched revision"),
    )

    @Test
    fun `an untouched filter shows every line`() {
        val filter = LogFilter()
        assertEquals(log.size, log.count(filter::matches))
    }

    @Test
    fun `problems is the warnings floor under a name that says what it is for`() {
        val filter = LogFilter().withProblemsOnly(true)
        assertEquals(AppLogLevel.Warn, filter.minLevel)
        assertTrue(filter.problemsOnly)
        assertEquals(
            listOf("daemon did not answer", "load refused", "exploit failed"),
            log.filter(filter::matches).map { it.message },
        )
    }

    @Test
    fun `turning problems off only lowers the floor that chip raised`() {
        assertEquals(AppLogLevel.Debug, LogFilter().withProblemsOnly(true).withProblemsOnly(false).minLevel)

        // An error floor is a level someone picked, and this chip did not pick it.
        val errors = LogFilter(minLevel = AppLogLevel.Error)
        assertEquals(AppLogLevel.Error, errors.withProblemsOnly(false).minLevel)
        assertTrue(errors.problemsOnly)
    }

    @Test
    fun `a tag narrows what the level let through`() {
        val filter = LogFilter(minLevel = AppLogLevel.Debug, tags = setOf(AppLogTags.KERNEL_SU))
        assertEquals(
            listOf("daemon did not answer", "load refused"),
            log.filter(filter::matches).map { it.message },
        )

        val problems = LogFilter(tags = setOf(AppLogTags.KERNEL_SU)).withProblemsOnly(true)
        assertEquals(
            listOf("daemon did not answer", "load refused"),
            log.filter(problems::matches).map { it.message },
        )
    }

    @Test
    fun `two tags are either of them, not both at once`() {
        val filter = LogFilter(tags = setOf(AppLogTags.RUN, AppLogTags.CATALOG))
        assertEquals(2, log.count(filter::matches))
    }

    @Test
    fun `an empty selection is every tag rather than none`() {
        assertTrue(LogFilter().tags.isEmpty())
        assertEquals(log.size, log.count(LogFilter()::matches))
    }

    @Test
    fun `tapping a tag twice puts the log back`() {
        val filter = LogFilter().togglingTag(AppLogTags.RUN)
        assertEquals(setOf(AppLogTags.RUN), filter.tags)
        assertTrue(filter.togglingTag(AppLogTags.RUN).tags.isEmpty())
    }

    @Test
    fun `the tag row is ranked by what it would show`() {
        val chips = logTagCounts(log, LogFilter())
        // Two tags hold two lines each and two hold one, so the ranking is by count and a tie is
        // broken by name - which is the rule that keeps the row from reordering itself at random.
        assertEquals(listOf(AppLogTags.KERNEL_SU, AppLogTags.SHIZUKU), chips.take(2).map { it.tag })
        assertEquals(listOf(2, 2, 1, 1), chips.map { it.count })
    }

    @Test
    fun `tag counts follow the level, which is what makes the row worth reading`() {
        val problems = logTagCounts(log, LogFilter().withProblemsOnly(true))
        assertEquals(listOf(AppLogTags.KERNEL_SU, AppLogTags.RUN), problems.map { it.tag })
        assertFalse(problems.any { it.tag == AppLogTags.SHIZUKU })
    }

    @Test
    fun `a selected tag keeps its chip at whatever count follows`() {
        // Selected, and nothing in it is loud enough to pass the floor: the chip has to stay, or the
        // filter is on with nothing on screen to say so - or to clear it with.
        val filter = LogFilter(minLevel = AppLogLevel.Error, tags = setOf(AppLogTags.SHIZUKU))
        val chips = logTagCounts(log, filter)
        assertEquals(AppLogTags.SHIZUKU, chips.first().tag)
        assertEquals(0, chips.first().count)
    }

    @Test
    fun `a selected tag cannot be pushed off the row by the ranking`() {
        val tags = (1..MAX_LOG_TAG_CHIPS + 4).map { "Tag$it" }
        val entries = tags.mapIndexed { index, tag ->
            entry(if (index == tags.lastIndex) AppLogLevel.Error else AppLogLevel.Info, tag)
        }
        val filter = LogFilter(tags = setOf(tags.last()))
        val chips = logTagCounts(entries, filter)
        assertEquals(MAX_LOG_TAG_CHIPS, chips.size)
        assertEquals(tags.last(), chips.first().tag)
    }

    @Test
    fun `an empty log offers no chips at all`() {
        assertTrue(logTagCounts(emptyList(), LogFilter()).isEmpty())
    }
}
