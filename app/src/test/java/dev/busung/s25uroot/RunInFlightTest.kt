package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record of a run in flight, and the two places that refuse to start a second one beside it.
 *
 * The record is read by processes that did not write it and outlives the process that did, so what it means
 * has to hold when the facts around it are stale: a pid that has been handed out again, a boot that has
 * ended, a field an older build never wrote. Every one of those is a case here.
 */
class RunInFlightTest {

    private val thisBoot = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"

    @Test
    fun `a record that matches the boot and the process is a run`() {
        val holder = RunHolder(thisBoot, 8123, "run-1", startTicks = "555")

        assertTrue(holder.holds(thisBoot, alive = true, startTicks = "555"))
    }

    @Test
    fun `a pid that has been handed out again is not the run that wrote the record`() {
        // The record outlives its process on purpose - a run killed with its process never clears it - so
        // "pid 8123 is alive" is not "the run is alive" unless the start time agrees. On a long boot the
        // number comes round again, and this is what keeps the record from describing a stranger.
        val holder = RunHolder(thisBoot, 8123, "run-1", startTicks = "555")

        assertFalse(holder.holds(thisBoot, alive = true, startTicks = "999"))
    }

    @Test
    fun `a start time that cannot be read does not refuse`() {
        // Only a positive mismatch refuses, the same rule the module-mount check follows: a reading this
        // cannot make is not evidence that a run is over, and treating it as one would strand a device
        // mid-run - or, worse here, let a second exploit start because the first looked gone.
        val holder = RunHolder(thisBoot, 8123, "run-1", startTicks = "555")

        assertTrue(
            "a process whose start time could not be read is treated as the run's",
            holder.holds(thisBoot, alive = true, startTicks = null),
        )
        assertTrue(
            "a record written before the start time existed still holds",
            RunHolder(thisBoot, 8123, "run-1").holds(thisBoot, alive = true, startTicks = "999"),
        )
    }

    @Test
    fun `a record from another boot or a dead process is not a run`() {
        val holder = RunHolder(thisBoot, 8123, "run-1", startTicks = "555")

        assertFalse(holder.holds("another-boot", alive = true, startTicks = "555"))
        assertFalse(holder.holds(thisBoot, alive = false, startTicks = "555"))
        assertFalse(holder.holds(null, alive = true, startTicks = "555"))
    }

    @Test
    fun `every field of the record survives being stored and read back`() {
        val parsed = RunHolder.of(
            bootToken = " $thisBoot ",
            pid = " 8123 ",
            entryId = " run-1 ",
            startTicks = " 555 ",
        )

        assertEquals(RunHolder(thisBoot, 8123, "run-1", "555"), parsed)
        assertNull("a pid that is not one is not a record", RunHolder.of(thisBoot, "not-a-pid"))
        assertNull("a pid that is not positive is not a record", RunHolder.of(thisBoot, "0"))
        assertNull("a boot the record cannot name is not a record", RunHolder.of("  ", "8123"))
    }

    @Test
    fun `an in-flight record from another process does not hard-refuse this attempt`() {
        val viewModel = source("src/main/java/dev/busung/s25uroot/InstallViewModel.kt")
        val body = viewModel.substringAfter("fun install(").substringBefore("\n    suspend fun ")

        val guard = body.indexOf("runInFlightElsewhere()?.let")
        assertTrue("the cross-process record remains observable", guard > 0)

        val guardEnd = body.indexOf("\n\n        // Taken before the question below", guard)
        assertTrue("the observational guard has a bounded body", guardEnd > guard)

        val guardBody = body.substring(guard, guardEnd)
        assertFalse(
            "a stale/foreign run record must not hard-refuse the current attempt",
            Regex("\\breturn\\b").containsMatchIn(guardBody),
        )
        assertTrue(
            "the foreign run is explicitly logged as observational",
            guardBody.contains("continuing this exploit attempt"),
        )
        assertTrue(
            "the run record is still created for the current attempt",
            body.indexOf("RunInFlight.begin(", guardEnd) > guardEnd,
        )
    }

    @Test
    fun `the boot gate asks the same question twice, and gives way`() {
        val decision = source("src/main/java/dev/busung/s25uroot/AutoRootSupport.kt")
        val service = source("src/main/java/dev/busung/s25uroot/AutoRootService.kt")

        assertTrue(
            "the gate's decision no longer knows about a run in flight",
            decision.contains("runInFlight = RunInFlight.holder(context) != null") &&
                decision.contains("runInFlight -> AutoRootDecision.SkipRunInFlight"),
        )
        assertTrue(
            "the gate no longer stands down over a run that started during its wait",
            service.contains("Root on boot stood down after the wait") &&
                service.contains("R.string.autoroot_run_in_flight"),
        )
        // The skip has to be handled where the decision is acted on, or the gate falls through to the run
        // it just decided not to make.
        assertTrue(
            "AutoRootDecision.SkipRunInFlight is not handled",
            service.contains("AutoRootDecision.SkipRunInFlight ->"),
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
