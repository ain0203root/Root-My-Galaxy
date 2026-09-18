package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {

    private fun entry(
        id: String,
        result: InstallRunResult,
        sourceId: String? = null,
        sourceLabel: String? = null,
    ) = InstallHistoryEntry(
        id = id,
        startedAtMillis = 1_000,
        completedAtMillis = 2_000,
        result = result,
        log = "",
        sourceId = sourceId,
        sourceLabel = sourceLabel,
    )

    private val history = listOf(
        entry("a", InstallRunResult.Succeeded, sourceId = "one", sourceLabel = "Rushi's payloads"),
        entry("b", InstallRunResult.Failed, sourceId = "one", sourceLabel = "Rushi's payloads"),
        entry("c", InstallRunResult.Failed, sourceId = "two", sourceLabel = "Busung"),
        // Recorded before the history carried a source at all.
        entry("d", InstallRunResult.RootOnly),
    )

    // --- chips ---------------------------------------------------------------------------------

    @Test
    fun `a result the history never recorded gets no chip`() {
        val chips = historyResultFilters(history, selected = HistoryFilter.All)

        assertEquals(
            listOf(HistoryFilter.All, HistoryFilter.Succeeded, HistoryFilter.RootOnly, HistoryFilter.Failed),
            chips,
        )
    }

    @Test
    fun `the selected result keeps its chip after its last run is gone`() {
        val empty = emptyList<InstallHistoryEntry>()

        val chips = historyResultFilters(empty, selected = HistoryFilter.Failed)

        // Otherwise deleting the last failure would take the chip away and leave an empty list that
        // cannot be explained or cleared from the chips themselves.
        assertEquals(listOf(HistoryFilter.All, HistoryFilter.Failed), chips)
    }

    @Test
    fun `chips follow the enum order, not the order runs happen to be in`() {
        val reversed = history.reversed()

        assertEquals(
            historyResultFilters(history, HistoryFilter.All),
            historyResultFilters(reversed, HistoryFilter.All),
        )
    }

    // --- filtering -----------------------------------------------------------------------------

    @Test
    fun `all shows every run, a chip shows only its kind`() {
        assertEquals(4, filterHistory(history, HistoryFilter.All, sourceKey = null).size)
        assertEquals(listOf("b", "c"), filterHistory(history, HistoryFilter.Failed, null).map { it.id })
        assertEquals(listOf("a"), filterHistory(history, HistoryFilter.Succeeded, null).map { it.id })
        assertEquals(listOf("d"), filterHistory(history, HistoryFilter.RootOnly, null).map { it.id })
        assertEquals(emptyList<String>(), filterHistory(history, HistoryFilter.Stopped, null).map { it.id })
    }

    @Test
    fun `a source narrows the same list rather than replacing the result filter`() {
        val ids = filterHistory(history, HistoryFilter.Failed, sourceKey = "one").map { it.id }

        assertEquals(listOf("b"), ids)
    }

    @Test
    fun `the empty key selects the runs that named no source`() {
        val ids = filterHistory(history, HistoryFilter.All, sourceKey = historySourceKey(history.last())).map { it.id }

        // Not read as "any source": runs from before the history carried one are their own group.
        assertEquals(listOf("d"), ids)
    }

    @Test
    fun `any source is a null key and keeps runs with and without one`() {
        assertEquals(4, filterHistory(history, HistoryFilter.All, sourceKey = null).size)
    }

    @Test
    fun `a source is keyed by id, so two sources named the same stay apart`() {
        val twins = listOf(
            entry("a", InstallRunResult.Succeeded, sourceId = "one", sourceLabel = "Payloads"),
            entry("b", InstallRunResult.Succeeded, sourceId = "two", sourceLabel = "Payloads"),
        )

        val options = historySourceOptions(twins)

        assertEquals(listOf("one", "two"), options.map { it.key }.sorted())
        assertEquals(listOf("b"), filterHistory(twins, HistoryFilter.All, sourceKey = "two").map { it.id })
    }

    @Test
    fun `a source with no id falls back to its label`() {
        val labelled = listOf(entry("a", InstallRunResult.Succeeded, sourceLabel = "Busung"))

        assertEquals("Busung", historySourceKey(labelled.single()))
        assertEquals(listOf("a"), filterHistory(labelled, HistoryFilter.All, sourceKey = "Busung").map { it.id })
    }

    // --- source chips --------------------------------------------------------------------------

    @Test
    fun `source chips are busiest first, and the unrecorded bucket is one of them`() {
        val options = historySourceOptions(history)

        // "one" has two runs; the other two are tied on one each and break on the label, which puts
        // the unrecorded bucket (no label at all) first among them.
        assertEquals(listOf("one", "", "two"), options.map { it.key })
        assertEquals(listOf("Rushi's payloads", "", "Busung"), options.map { it.label })
    }

    @Test
    fun `equally used sources keep a stable order`() {
        val once = listOf(
            entry("a", InstallRunResult.Succeeded, sourceId = "zzz", sourceLabel = "Zeta"),
            entry("b", InstallRunResult.Succeeded, sourceId = "aaa", sourceLabel = "Alpha"),
        )

        assertEquals(listOf("Alpha", "Zeta"), historySourceOptions(once).map { it.label })
        assertEquals(
            historySourceOptions(once),
            historySourceOptions(once.reversed()),
        )
    }

    @Test
    fun `a history with no runs offers no source chips`() {
        assertEquals(emptyList<HistorySourceOption>(), historySourceOptions(emptyList()))
    }
}
