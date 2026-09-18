package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the pre-flight's findings add up to. */
class PreflightTest {

    private fun item(
        id: PreflightItemId,
        state: PreflightState,
        detail: String = "checked",
    ) = PreflightItem(id = id, state = state, detail = detail)

    @Test
    fun `nothing wrong is a run with nothing left to weigh`() {
        val items = listOf(
            item(PreflightItemId.Target, PreflightState.Ready),
            item(PreflightItemId.Match, PreflightState.Ready),
            item(PreflightItemId.Partitions, PreflightState.Note),
        )
        assertEquals(PreflightVerdict.Ready, preflightVerdict(items))
        assertTrue(preflightVerdict(items) != PreflightVerdict.Blocked)
    }

    @Test
    fun `a caution is worth running for, which is the whole reason the attempt exists`() {
        val items = listOf(
            item(PreflightItemId.Target, PreflightState.Ready),
            item(PreflightItemId.Match, PreflightState.Warn),
        )
        assertEquals(PreflightVerdict.MayBeRefused, preflightVerdict(items))
    }

    @Test
    fun `a failure outranks a caution rather than being listed beside it`() {
        // "May still be refused" over a run that cannot start is a sentence someone would be right to act
        // on and wrong to believe.
        val items = listOf(
            item(PreflightItemId.Match, PreflightState.Warn),
            item(PreflightItemId.Budget, PreflightState.Fail),
        )
        assertEquals(PreflightVerdict.Blocked, preflightVerdict(items))
    }

    @Test
    fun `a note is not a finding about the device`() {
        // The partition guard and offline mode change what a run does; neither makes it less likely to work.
        val items = listOf(
            item(PreflightItemId.Partitions, PreflightState.Note),
            item(PreflightItemId.Staging, PreflightState.Note),
        )
        assertEquals(PreflightVerdict.Ready, preflightVerdict(items))
    }

    @Test
    fun `the report's lines are what a reader needs, in the order they were answered`() {
        val report = PreflightReport(
            deviceLabel = "SM-S9360 6.6.98",
            targetLabel = "Galaxy S25 series",
            items = listOf(
                item(PreflightItemId.Target, PreflightState.Ready, "found"),
                item(PreflightItemId.Budget, PreflightState.Fail, "spent"),
            ),
        )

        assertEquals(1, report.blockers.size)
        assertEquals(PreflightItemId.Budget, report.blockers.single().id)
        assertTrue(report.warnings.isEmpty())
        assertEquals(PreflightVerdict.Blocked, report.verdict)
    }

    @Test
    fun `an empty check is nothing to report rather than a refusal`() {
        // The one direction this rule must not fail in: a check that produced no lines is not a run that
        // cannot start, it is a check that found nothing to say.
        assertEquals(PreflightVerdict.Ready, preflightVerdict(emptyList()))
    }

    @Test
    fun `every item a check reports has a name of its own`() {
        // The label is what the report's block and the sheet's rows are made of, and two items sharing one
        // would leave a reader unable to tell which line a state belongs to.
        val labels = PreflightItemId.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
