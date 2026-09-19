package dev.busung.s25uroot

/**
 * What the Logs tab is being asked to show, in the two questions it is asked in.
 *
 * One object rather than two pieces of screen state, because the two are not independent: a line is shown
 * when it clears the floor *and* matches the text, and held apart they would be applied in two places that
 * could disagree about the same line.
 *
 * Pure, so the answers worth checking - what the Problems floor lets through, that turning it off only
 * lowers the floor it raised - are checked without a device.
 *
 * There used to be a third question, a set of tags drawn as a row of chips with a count beside each one.
 * It is **gone**, and deliberately not replaced by a smaller version of itself: the row was the tallest
 * thing above the log, and for the question it actually answered - where is the noise - the text field is
 * one word away.
 */
internal data class LogFilter(
    val minLevel: AppLogLevel = AppLogLevel.Debug,
    val query: String = "",
) {
    /**
     * Whether problems are all that is being shown.
     *
     * Derived from the level instead of being its own flag, because the two would otherwise be able to
     * disagree: a chip reading "not selected" over a list of warnings, or a lit chip over a floor someone
     * had just lowered. This way the chip and the list are the same fact.
     */
    val problemsOnly: Boolean get() = minLevel.ordinal >= AppLogLevel.Warn.ordinal

    /**
     * Raises the floor to warnings and up, or takes it back down to everything.
     *
     * Turning it off only lowers a floor that is warnings - the level this chip put there. Error is a level
     * someone picked deliberately, and clearing it would be this chip answering a question it was not asked.
     */
    fun withProblemsOnly(on: Boolean): LogFilter = if (on) {
        copy(minLevel = AppLogLevel.Warn)
    } else {
        copy(minLevel = if (minLevel == AppLogLevel.Warn) AppLogLevel.Debug else minLevel)
    }

    /** Whether a line belongs on screen. */
    fun matches(entry: AppLogEntry): Boolean = AppLogFormat.matches(entry, minLevel, query)
}
