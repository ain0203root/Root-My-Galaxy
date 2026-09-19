package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which retry answers a stored failed run can still give, and when it must not give the one that restarts.
 *
 * The rule this is about is not a preference: a restart-and-retry runs the payload the app last *attempted*,
 * which is not necessarily the payload the record it was armed from is about. Arming it on the wrong record
 * restarts the phone to run something the reader did not ask for - and the wrong direction is just as bad,
 * because refusing to offer it on a record that is in fact the last attempt hides the answer with the best
 * odds behind the one the app itself calls "not recommended".
 */
class HistoryRetryTest {

    private fun entry(
        result: InstallRunResult = InstallRunResult.Failed,
        profileId: String? = "kdp",
        sourceId: String? = "rushiranpise/Root-My-Galaxy-Payloads@main",
        sourceCommit: String? = "abc1234",
    ) = InstallHistoryEntry(
        id = "run-1",
        startedAtMillis = 1_700_000_000_000,
        completedAtMillis = 1_700_000_060_000,
        result = result,
        log = "",
        profileId = profileId,
        sourceId = sourceId,
        sourceCommit = sourceCommit,
    )

    private fun attempted(
        profileId: String = "kdp",
        sourceId: String = "rushiranpise/Root-My-Galaxy-Payloads@main",
        sourceCommit: String = "abc1234",
    ) = CachedPayload(
        id = "cached-id",
        profileId = profileId,
        displayName = "KDP",
        models = emptyList(),
        kernelVersions = emptyList(),
        requiresFreshP0Session = false,
        routePolicy = ExploitRoutePolicy(),
        exploit = RemoteArtifact(url = "https://example.test/exploit", size = 1, sha256 = null),
        kernelSu = RemoteArtifact(url = "https://example.test/ksu", size = 1, sha256 = null),
        helperSha256 = "0".repeat(64),
        helperSize = 0,
        sourceId = sourceId,
        sourceLabel = "rushiranpise/Root-My-Galaxy-Payloads",
        sourceCommit = sourceCommit,
    )

    @Test
    fun `a record of the last attempt offers all three answers`() {
        val plan = recordRetryPlan(entry(), attempted())

        assertEquals(
            listOf(
                RecordRetryAnswer.RestartAndRetry,
                RecordRetryAnswer.WaitThenRetry,
                RecordRetryAnswer.TryNow,
            ),
            plan.answers,
        )
        assertTrue("nothing is missing, so nothing is explained", plan.gaps.isEmpty())
        assertEquals("rushiranpise/Root-My-Galaxy-Payloads@main|kdp", plan.selectionId)
    }

    @Test
    fun `a run that did not fail is offered nothing`() {
        for (result in listOf(
            InstallRunResult.Succeeded,
            InstallRunResult.RootOnly,
            InstallRunResult.Stopped,
            InstallRunResult.Running,
        )) {
            val plan = recordRetryPlan(entry(result = result), attempted())

            assertEquals("$result is offered a retry", emptyList<RecordRetryAnswer>(), plan.answers)
            assertEquals("$result is explained as retryable", emptyList<RecordRetryGap>(), plan.gaps)
            assertNull("$result names a payload to run", plan.selectionId)
        }
    }

    @Test
    fun `a restart is not offered when the last attempt was a different payload`() {
        val plan = recordRetryPlan(entry(), attempted(profileId = "other-profile"))

        assertEquals(listOf(RecordRetryAnswer.WaitThenRetry, RecordRetryAnswer.TryNow), plan.answers)
        assertEquals(
            "a restart that would run another payload is offered without saying so",
            listOf(RecordRetryGap.LastAttemptWasAnotherPayload),
            plan.gaps,
        )
    }

    @Test
    fun `with nothing attempted there is nothing a restart could run`() {
        val plan = recordRetryPlan(entry(), attempted = null)

        assertEquals(listOf(RecordRetryAnswer.WaitThenRetry, RecordRetryAnswer.TryNow), plan.answers)
        assertEquals(listOf(RecordRetryGap.LastAttemptWasAnotherPayload), plan.gaps)
    }

    @Test
    fun `a record that does not name its source can still be restarted into`() {
        // The one answer that needs no choice from the record: the attempt on disk is the payload.
        val plan = recordRetryPlan(entry(sourceId = null), attempted())

        assertEquals(listOf(RecordRetryAnswer.RestartAndRetry), plan.answers)
        assertNull("a bare profile id is not a choice to resolve", plan.selectionId)
        assertEquals(
            "the missing answers are not explained",
            listOf(RecordRetryGap.PayloadNotRecorded),
            plan.gaps,
        )
    }

    @Test
    fun `a record that names no payload is offered nothing, and says both reasons`() {
        // A record with no profile id cannot claim the attempt on disk is the payload it ran - and it has
        // no choice to resolve either. Nothing is offered, and both halves are explained rather than the
        // card being an empty box with a heading.
        val plan = recordRetryPlan(entry(profileId = null, sourceId = null), attempted())

        assertEquals(emptyList<RecordRetryAnswer>(), plan.answers)
        assertNull(plan.selectionId)
        assertEquals(
            listOf(RecordRetryGap.LastAttemptWasAnotherPayload, RecordRetryGap.PayloadNotRecorded),
            plan.gaps,
        )
    }

    @Test
    fun `the same profile from another source is another payload`() {
        // Two catalogs can offer the same payload id, which is why the record carries its source.
        val sameId = attempted(sourceId = "someone-else/Root-My-Galaxy-Payloads@main")

        assertFalse(
            "a payload from a different catalog is read as this record's",
            attemptedIsRecordedRun(sameId, entry()),
        )
    }

    @Test
    fun `the same source at another commit is another payload`() {
        assertFalse(
            "a moved branch is read as the bytes this run used",
            attemptedIsRecordedRun(attempted(sourceCommit = "9999999"), entry()),
        )
    }

    @Test
    fun `a key only one side has is not disagreement`() {
        // Older records state no commit, and an attempt recorded before it had one states none either:
        // neither can say the two are different, so the question stays answered the way it was.
        assertTrue(
            "a record with no commit cannot be refused for that",
            attemptedIsRecordedRun(attempted(), entry(sourceCommit = null)),
        )
        assertTrue(
            "an attempt with no commit cannot be refused for that",
            attemptedIsRecordedRun(attempted(sourceCommit = ""), entry()),
        )
        assertFalse(
            "a record with no payload id is never this attempt's",
            attemptedIsRecordedRun(attempted(), entry(profileId = null)),
        )
    }
}
