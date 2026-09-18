package dev.busung.s25uroot

/**
 * Which runs the history list shows, as the result chips name them.
 *
 * One filter at a time rather than a set, because the interesting question in this screen is almost
 * always "which of these went wrong" - a run has exactly one result, so a selection of several would
 * only ever mean the union, which is a longer way of saying one of them.
 */
enum class HistoryFilter {
    All,
    Succeeded,
    RootOnly,
    Failed,
    Stopped,
    Running;

    fun matches(result: InstallRunResult): Boolean = when (this) {
        All -> true
        Succeeded -> result == InstallRunResult.Succeeded
        RootOnly -> result == InstallRunResult.RootOnly
        Failed -> result == InstallRunResult.Failed
        Stopped -> result == InstallRunResult.Stopped
        Running -> result == InstallRunResult.Running
    }
}

/**
 * The chips worth drawing: all of them, then one per result this history actually holds.
 *
 * A chip that can only ever produce an empty list is noise, so a result the history has never recorded
 * gets no chip - with one exception, the result currently selected. Without it, deleting the last run of
 * a filtered kind would take the chip away and leave the list empty with nothing to explain why or to
 * press to get back, which is the state this screen exists to avoid.
 */
fun historyResultFilters(
    entries: List<InstallHistoryEntry>,
    selected: HistoryFilter,
): List<HistoryFilter> = buildList {
    add(HistoryFilter.All)
    HistoryFilter.entries.forEach { filter ->
        if (filter == HistoryFilter.All) return@forEach
        val recorded = entries.any { filter.matches(it.result) }
        if (recorded || filter == selected) add(filter)
    }
}

/**
 * The catalog a run is attributed to, as a filter key.
 *
 * The id is preferred to the label because the label is written by whoever added the source and two
 * sources can be named the same thing. An empty key is not a missing value: it is the bucket for runs
 * that recorded no source at all - every run from a build before the history carried one, and the ones
 * that ran a payload already on the device.
 */
fun historySourceKey(entry: InstallHistoryEntry): String =
    entry.sourceId?.takeIf(String::isNotBlank)
        ?: entry.sourceLabel?.takeIf(String::isNotBlank)
        ?: ""

/** One source chip: the key to filter on, and what to call it. An empty label is the unrecorded bucket. */
data class HistorySourceOption(val key: String, val label: String)

/**
 * The sources this history has runs from, busiest first.
 *
 * Ordered by how often a source was used rather than alphabetically, because the reason to reach for
 * this filter is usually the source being tested right now, and that is the one with the most runs.
 * Ties break on the label so the order cannot change between two readings of the same history.
 */
fun historySourceOptions(entries: List<InstallHistoryEntry>): List<HistorySourceOption> = entries
    .groupBy(::historySourceKey)
    .map { (key, runs) ->
        HistorySourceOption(
            key = key,
            label = runs.firstNotNullOfOrNull { entry ->
                entry.sourceLabel?.takeIf(String::isNotBlank) ?: entry.sourceId?.takeIf(String::isNotBlank)
            }.orEmpty(),
        ) to runs.size
    }
    .sortedWith(compareByDescending<Pair<HistorySourceOption, Int>> { it.second }.thenBy { it.first.label })
    .map { it.first }

/**
 * The runs to draw: the result chip's kind, and the chosen source when one is chosen.
 *
 * `null` means any source. Empty is a real key here, so it selects the runs that named none rather than
 * being read as "no filter".
 */
fun filterHistory(
    entries: List<InstallHistoryEntry>,
    filter: HistoryFilter,
    sourceKey: String?,
): List<InstallHistoryEntry> = entries.filter { entry ->
    filter.matches(entry.result) && (sourceKey == null || historySourceKey(entry) == sourceKey)
}
