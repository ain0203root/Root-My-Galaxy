package dev.busung.s25uroot

/**
 * What the Logs tab is being asked to show, in the three questions it is asked in.
 *
 * One object rather than three pieces of screen state, because the three are not independent: a tag
 * chip shows a count, that count depends on the level and the text, and whether a tag is even on the
 * row depends on the same two. Held apart, every one of those would be recomputed by hand where the
 * chip is drawn, and one of them would disagree with the list below it.
 *
 * Pure, so the answers that are worth checking - what a Problems filter lets through, that a
 * selected tag survives its own count dropping, that an empty selection means every tag - are
 * checked without a device.
 */
internal data class LogFilter(
    val minLevel: AppLogLevel = AppLogLevel.Debug,
    val query: String = "",
    val tags: Set<String> = emptySet(),
) {
    /**
     * Whether problems are all that is being shown.
     *
     * Derived from the level instead of being its own flag, because the two would otherwise be able
     * to disagree: a chip reading "not selected" over a list of warnings, or a lit chip over a floor
     * someone had just lowered. This way the chip and the list are the same fact.
     */
    val problemsOnly: Boolean get() = minLevel.ordinal >= AppLogLevel.Warn.ordinal

    /**
     * Raises the floor to warnings and up, or takes it back down to everything.
     *
     * Turning it off only lowers a floor that is warnings - the level this chip put there. Error is
     * a level someone picked deliberately, and clearing it would be this chip answering a question
     * it was not asked.
     */
    fun withProblemsOnly(on: Boolean): LogFilter = if (on) {
        copy(minLevel = AppLogLevel.Warn)
    } else {
        copy(minLevel = if (minLevel == AppLogLevel.Warn) AppLogLevel.Debug else minLevel)
    }

    /** Adds a tag to the selection or takes it out - the one gesture a tag chip has. */
    fun togglingTag(tag: String): LogFilter =
        copy(tags = if (tag in tags) tags - tag else tags + tag)

    /**
     * Whether a line belongs on screen.
     *
     * An empty tag selection is every tag rather than none: the row of chips is offered unselected,
     * and a filter nobody has touched must not hide the log.
     */
    fun matches(entry: AppLogEntry): Boolean =
        AppLogFormat.matches(entry, minLevel, query) &&
            (tags.isEmpty() || entry.tag in tags)
}

/** One tag offered as a chip, and how many lines it would let through. */
internal data class LogTagCount(val tag: String, val count: Int)

/**
 * The tags the chip row offers, most lines first.
 *
 * Ranked rather than listed alphabetically because the row is bounded and the useful question is
 * "where is the noise", and capped because fifteen chips are a wall of text to read before the log
 * itself: past the cap, the search field is the way to reach a quiet tag.
 *
 * Two rules make the row usable rather than merely informative. Counts come from the level and the
 * text but **not** from the tag selection, so the row keeps showing what is selectable while a tag is
 * on - and a tag still selected is listed first, at whatever count it has, because a selection with
 * no chip is a filter nobody can see or clear.
 */
internal fun logTagCounts(
    entries: List<AppLogEntry>,
    filter: LogFilter,
    limit: Int = MAX_LOG_TAG_CHIPS,
): List<LogTagCount> {
    if (entries.isEmpty()) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    filter.tags.forEach { counts[it] = 0 }
    entries.forEach { entry ->
        if (!AppLogFormat.matches(entry, filter.minLevel, filter.query)) return@forEach
        counts[entry.tag] = (counts[entry.tag] ?: 0) + 1
    }
    val ranked = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { LogTagCount(it.key, it.value) }
    val selected = ranked.filter { it.tag in filter.tags }
    val rest = ranked.filterNot { it.tag in filter.tags }
    return selected + rest.take((limit - selected.size).coerceAtLeast(0))
}

/** How many tag chips the row offers before the search field is the way to reach the rest. */
internal const val MAX_LOG_TAG_CHIPS = 8
