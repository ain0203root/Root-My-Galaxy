package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the install steps card puts its marks.
 *
 * The case that prompted this: a run that died in the kernel exploit showed the *support check* as the
 * step in progress and no mark at all on the step that failed. Both of the things the card exists to
 * answer - how far the run got, and where it stopped - were wrong at once, on the one screen a user
 * sees after a failure.
 */
class InstallerStepStateTest {

    private fun states(phase: InstallPhase, failureStage: RunStage? = null): List<InstallerStepState> =
        installerSteps.indices.map { installerStepState(phase, it, failureStage) }

    @Test
    fun `a failure at the exploit marks support and download done and the exploit failed`() {
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Failed,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.Failed, RunStage.Exploit),
        )
    }

    @Test
    fun `a failure during the download leaves nothing claimed after it`() {
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Failed,
                InstallerStepState.Pending,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.Failed, RunStage.Download),
        )
    }

    /** The support check is two stages: the transport and the target lookup. */
    @Test
    fun `a failure before anything ran is the first step`() {
        assertEquals(
            listOf(
                InstallerStepState.Failed,
                InstallerStepState.Pending,
                InstallerStepState.Pending,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.Failed, RunStage.Target),
        )
        assertEquals(
            listOf(
                InstallerStepState.Failed,
                InstallerStepState.Pending,
                InstallerStepState.Pending,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.Failed, RunStage.Transport),
        )
    }

    /** Loading KernelSU includes verifying the control channel, which is the same step on this card. */
    @Test
    fun `a failure while loading or verifying is the last step`() {
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Failed,
            ),
            states(InstallPhase.Failed, RunStage.KernelSu),
        )
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Failed,
            ),
            states(InstallPhase.Failed, RunStage.Verify),
        )
    }

    /** Nothing is claimed when the stage is unknown, rather than guessing at the first step. */
    @Test
    fun `a failure with no stage claims no step`() {
        assertEquals(
            List(installerSteps.size) { InstallerStepState.Pending },
            states(InstallPhase.Failed, null),
        )
    }

    @Test
    fun `a run in flight marks what it has done and what it is doing`() {
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Active,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.Exploiting),
        )
    }

    @Test
    fun `a finished run marks every step`() {
        assertEquals(
            List(installerSteps.size) { InstallerStepState.Done },
            states(InstallPhase.Installed),
        )
    }

    /** A run told not to load KernelSU never asked for the last step, so it is not ticked. */
    @Test
    fun `root only marks the exploit and stops`() {
        assertEquals(
            listOf(
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Done,
                InstallerStepState.Pending,
            ),
            states(InstallPhase.RootOnly),
        )
    }

    /** The bar stops where the run did, instead of resetting to nothing with the failure. */
    @Test
    fun `a failure leaves the bar where the run reached`() {
        assertEquals(
            (installerStepForStage(RunStage.Exploit) + 1) / installerSteps.size.toFloat(),
            installProgress(InstallPhase.Failed, RunStage.Exploit),
            0.0001f,
        )
    }
}
