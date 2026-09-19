package dev.busung.s25uroot

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Logs tab's filter: the level floor, the text, and nothing else. */
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
    fun `the filter is a floor and a text, with no tag dimension left`() {
        // The tag row and the count behind each chip were removed rather than hidden, because the row was
        // the tallest thing above the log - and the one question it answered is now the text field's job.
        // Read off the declaration rather than searched for by name, so a `tags` field added back fails
        // here even if it is called something else.
        // Instance fields only: the class also carries the Compose compiler's static `$stable`, which is not
        // one of the questions the filter is asked.
        val fields = LogFilter::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertTrue("the level floor is gone from the filter", fields.contains("minLevel"))
        assertTrue("the text is gone from the filter", fields.contains("query"))
        assertFalse(
            "a tag set is part of the filter again; it was removed, not hidden",
            fields.any { it.contains("tag", ignoreCase = true) },
        )
    }
}
