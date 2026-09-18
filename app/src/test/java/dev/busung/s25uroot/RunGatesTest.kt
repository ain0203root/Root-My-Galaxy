package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's "before a run" card, as the list of things a run still has to pass.
 *
 * The card exists to answer one question - what is stopping the next run - so what is asserted here is what it
 * leaves out as much as what it shows. A gate listed when it is already satisfied is a row somebody has to
 * read, dismiss, and learn to skip; a gate missing when it is not is a run failing for a reason the screen
 * knew and did not say.
 *
 * No device and no Compose: this is the decision, not the card.
 */
class RunGatesTest {

    private fun gates(
        kernelSu: KernelSuStatus = KernelSuStatus.Active,
        shizukuMode: Boolean = false,
        shizuku: ShizukuAvailability = ShizukuAvailability.NotRunning,
        batteryUnrestricted: Boolean = true,
    ) = pendingRunGates(kernelSu, shizukuMode, shizuku, batteryUnrestricted)

    @Test
    fun `a phone with everything in place has no gates, which is the good news`() {
        assertEquals(emptyList<RunGate>(), gates())
    }

    @Test
    fun `root in this boot is the one gate the app itself closes`() {
        val single = gates(kernelSu = KernelSuStatus.NotLoaded)

        assertEquals(1, single.size)
        assertEquals(R.string.readiness_ksu_not_loaded, single.single().state)
        // The fix is a run, because nothing else puts KernelSU into a kernel that does not have it.
        assertEquals(RunGateFix.RootNow, single.single().fix)
    }

    @Test
    fun `a reading that could not answer is reported, and offers nothing`() {
        val single = gates(kernelSu = KernelSuStatus.Unreadable)

        assertEquals(R.string.readiness_ksu_unreadable, single.single().state)
        // This is the one that must not carry a fix: an unreadable KernelSU is usually a rooted phone whose
        // reading raced the module load, and "root now" would be advice to re-root it.
        assertNull(single.single().fix)
    }

    @Test
    fun `shizuku is a gate only when the app is set to use it`() {
        // Switched off: the app runs without a shell transport at all, so a row about Shizuku would be a row
        // about nothing.
        assertEquals(emptyList<RunGate>(), gates(shizukuMode = false, shizuku = ShizukuAvailability.NotRunning))
    }

    @Test
    fun `running and permitted are separate gates, with separate fixes`() {
        val notRunning = gates(shizukuMode = true, shizuku = ShizukuAvailability.NotRunning).single()
        val noPermission = gates(shizukuMode = true, shizuku = ShizukuAvailability.WithoutPermission).single()

        assertEquals(R.string.readiness_shizuku_not_running, notRunning.state)
        assertEquals(RunGateFix.StartShizuku, notRunning.fix)
        assertEquals(R.string.settings_shizuku_state_needs_permission, noPermission.state)
        // Not the start fix: a start is exactly what the row already offers elsewhere, and it would be the
        // wrong one - Shizuku is up and this app simply has not been granted it.
        assertEquals(RunGateFix.AllowShizuku, noPermission.fix)
        // And ready is no gate at all, whatever else is wrong.
        assertTrue(gates(shizukuMode = true, shizuku = ShizukuAvailability.Ready).none { it.label == R.string.readiness_shizuku })
    }

    @Test
    fun `a restricted app is a gate of its own`() {
        val single = gates(batteryUnrestricted = false).single()

        assertEquals(R.string.run_gate_battery, single.label)
        assertEquals(RunGateFix.AllowBattery, single.fix)
    }

    @Test
    fun `the gates are listed in the order they decide a run`() {
        // Root first: it decides whether the run can load anything. Then the transport it communicates
        // through. Then the exemption, which is what the unattended run needs to survive.
        val all = gates(
            kernelSu = KernelSuStatus.NotLoaded,
            shizukuMode = true,
            shizuku = ShizukuAvailability.NotRunning,
            batteryUnrestricted = false,
        )

        assertEquals(
            listOf(R.string.readiness_kernelsu, R.string.readiness_shizuku, R.string.run_gate_battery),
            all.map { it.label },
        )
        assertEquals(
            listOf(RunGateFix.RootNow, RunGateFix.StartShizuku, RunGateFix.AllowBattery),
            all.mapNotNull { it.fix },
        )
    }

    @Test
    fun `every gate carries a fix or says why it cannot`() {
        // A row with neither would be a line that only worries people, and one with both is a contradiction.
        val everyState = listOf(
            gates(kernelSu = KernelSuStatus.NotLoaded),
            gates(kernelSu = KernelSuStatus.Unreadable),
            gates(shizukuMode = true, shizuku = ShizukuAvailability.NotRunning),
            gates(shizukuMode = true, shizuku = ShizukuAvailability.WithoutPermission),
            gates(batteryUnrestricted = false),
        ).flatten()

        assertTrue("a gate was produced with no state to show", everyState.isNotEmpty())
        everyState.forEach { gate ->
            assertTrue("${gate.label} has no state", gate.state != 0)
            assertTrue("${gate.label} has no label", gate.label != 0)
        }
    }
}
