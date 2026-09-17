package dev.busung.s25uroot

/**
 * The kernel's pipe page budget, and what a payload that runs into it looks like.
 *
 * A pipe in this exploit is not a small one: the race is built on a pipe buffer whose pages are
 * charged to the user's budget (`fs.pipe-user-pages-soft` and `-hard`), and the budget is per boot
 * rather than per process. An attempt that spends it leaves the next attempt unable to allocate at
 * all, which the payload reports as a kernel refusal on the sizing call and which the app, until now,
 * reported as a generic exploit failure. Every later attempt in that boot fails the same way, so the
 * symptom is a phone that "stopped working" after the first failure rather than a run that failed
 * once - and the answer is a restart, not another attempt.
 *
 * The check is a pair read from the payload's own output, not a guess from a single word. A payload
 * prints its pipe limits as ordinary information (that is what the pre-check in the reference
 * implementation is for), and those lines are not a diagnosis; what is a diagnosis is one of those
 * limits appearing in the same line as a refusal.
 */
internal object PipeBudget {

    /**
     * What a payload calls the pipe page pool when it names it.
     *
     * `F_SETPIPE_SZ` is the sizing call itself, and the rest are the sysctls that bound how many pages
     * of pipe a user may hold at once.
     */
    private val LIMITS = listOf(
        "F_SETPIPE_SZ",
        "pipe-max-size",
        "pipe-user-pages-soft",
        "pipe-user-pages-hard",
    )

    /** How a kernel refuses an allocation, as payloads word it. */
    private val REFUSALS = listOf(
        "EPERM",
        "Operation not permitted",
        "denied",
        "failed",
    )

    /**
     * The payload's own line that shows it was refused for the budget, or null.
     *
     * Null is the answer for the ordinary case where the payload prints the limits it is working
     * under, and for a refusal about something else entirely: a diagnosis that fires on those would
     * send someone to restart a phone that has nothing wrong with it.
     */
    fun evidenceIn(log: String): String? = log.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .firstOrNull { line ->
            val namesALimit = LIMITS.any { limit -> line.contains(limit, ignoreCase = limit != "F_SETPIPE_SZ") }
            val refuses = REFUSALS.any { refusal -> line.contains(refusal, ignoreCase = true) }
            namesALimit && refuses
        }
}
