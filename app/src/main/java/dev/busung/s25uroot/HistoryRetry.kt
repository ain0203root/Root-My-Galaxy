package dev.busung.s25uroot

/**
 * What a stored failed run can still be retried with, which is not the same question as a live failure.
 *
 * The run screen's three answers are asked of a run that is in front of it: its selection, its payload
 * files, its boot. A record read later has only what the run wrote down, and two of the three answers
 * survive that and one does not.
 *
 * - **Restart and retry** survives, but only when the payload the app last attempted is the one *this*
 *   record is about. The armed retry runs the attempt on disk, not a choice resolved again - that is what
 *   makes it worth the restart - so on a phone where something else was tried since, arming from an older
 *   record would restart the phone to run a payload the reader did not ask for.
 * - **Retry now** and **wait, then retry** survive by re-resolving the record's own choice, which is the
 *   same thing the run screen's "retry now" does. That needs the record to say which choice it was, which
 *   is why a run that did not name its source can only be offered the restart.
 *
 * The wait is about state the payload leaves in the boot it failed in, so it is only literally "a minute
 * since that attempt" when the record is read in the same boot - which a record does not record. It is
 * still the honest middle answer: what it costs is a minute, and what it is waiting for is stated on the
 * button.
 */
internal enum class RecordRetryAnswer {
    RestartAndRetry,
    WaitThenRetry,
    TryNow,
}

/**
 * Why an answer a reader would expect is missing from a record.
 *
 * Said rather than left to be noticed: a record with only "Retry now" under it, and no restart beside it,
 * reads as the app having lost the part that works.
 */
internal enum class RecordRetryGap {
    /** The app's last attempt was a different payload, so a restart would run that one instead. */
    LastAttemptWasAnotherPayload,

    /** The record does not name the payload it ran, so there is nothing to resolve again. */
    PayloadNotRecorded,
}

internal data class RecordRetryPlan(
    /** The choice to run again, in the payload sheet's own form, or null when the record names none. */
    val selectionId: String?,
    val answers: List<RecordRetryAnswer>,
    val gaps: List<RecordRetryGap>,
)

/**
 * The answers a record can offer, from the record and what the device holds now.
 *
 * Only a failed run: a run that succeeded has nothing to retry, one that was stopped was stopped on
 * purpose, and one still marked running is a run somebody is still on - the record's own live case, where
 * offering a retry would be offering a second exploit beside the first.
 */
internal fun recordRetryPlan(
    entry: InstallHistoryEntry,
    attempted: CachedPayload?,
): RecordRetryPlan {
    if (entry.result != InstallRunResult.Failed) {
        return RecordRetryPlan(selectionId = null, answers = emptyList(), gaps = emptyList())
    }
    val selectionId = entry.payloadSelectionId()
    val restartRunsThisRun = attemptedIsRecordedRun(attempted, entry)
    return RecordRetryPlan(
        selectionId = selectionId,
        answers = buildList {
            if (restartRunsThisRun) add(RecordRetryAnswer.RestartAndRetry)
            if (selectionId != null) {
                add(RecordRetryAnswer.WaitThenRetry)
                add(RecordRetryAnswer.TryNow)
            }
        },
        gaps = buildList {
            if (!restartRunsThisRun) add(RecordRetryGap.LastAttemptWasAnotherPayload)
            if (selectionId == null) add(RecordRetryGap.PayloadNotRecorded)
        },
    )
}

/**
 * Whether the payload this device last attempted is the one [entry] is a record of.
 *
 * Three keys rather than one, and each of them has to agree *where both sides state it*: a profile id
 * alone does not identify a payload, because two sources can offer the same id - which is why the record
 * carries its source at all. A field only one side has is not disagreement, and the answer stays as it
 * was: the question is whether what is on disk is this run's payload, and a record that does not say which
 * source it came from cannot make that claim either way.
 *
 * An attempt with no record at all is never this run's: the store is cleared with the app's data, and a
 * record outlives it.
 */
internal fun attemptedIsRecordedRun(attempted: CachedPayload?, entry: InstallHistoryEntry): Boolean {
    if (attempted == null) return false
    val profileId = entry.profileId?.takeIf(String::isNotBlank) ?: return false
    if (attempted.profileId != profileId) return false
    val sourceId = entry.sourceId?.takeIf(String::isNotBlank)
    if (sourceId != null && attempted.sourceId.isNotBlank() && sourceId != attempted.sourceId) return false
    val commit = entry.sourceCommit?.takeIf(String::isNotBlank)
    val attemptedCommit = attempted.sourceCommit.takeIf(String::isNotBlank)
    if (commit != null && attemptedCommit != null && commit != attemptedCommit) return false
    return true
}

/**
 * The choice a record was made from, as [selectionIdFor] builds it, or null when it does not name one.
 *
 * Rebuilt rather than stored, because it is exactly the pair the record already keeps: the source it was
 * read from and the payload id inside it. A record from before the source was recorded has only the
 * profile id, and that is not a choice - resolving it would guess which catalog to ask.
 */
internal fun InstallHistoryEntry.payloadSelectionId(): String? {
    val profile = profileId?.takeIf(String::isNotBlank) ?: return null
    val source = sourceId?.takeIf(String::isNotBlank) ?: return null
    return selectionIdFor(source, profile)
}
