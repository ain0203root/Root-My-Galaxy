package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

private const val THIS_BOOT = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"
private const val LAST_BOOT = "9e8d7c6b-5a49-4837-2615-0403f2e1d0c9"

class AutoRootDecisionTest {

    private fun decide(
        enabled: Boolean = true,
        kernelSuLoadEnabled: Boolean = true,
        kernelSuActive: Boolean = false,
        hasVerifiedInstall: Boolean = true,
        verifiedBootToken: String? = LAST_BOOT,
        attemptedBootToken: String? = null,
        bootToken: String = THIS_BOOT,
    ) = autoRootDecision(
        enabled = enabled,
        kernelSuLoadEnabled = kernelSuLoadEnabled,
        kernelSuActive = kernelSuActive,
        hasVerifiedInstall = hasVerifiedInstall,
        verifiedBootToken = verifiedBootToken,
        attemptedBootToken = attemptedBootToken,
        bootToken = bootToken,
    )

    @Test
    fun `a fresh boot with a verified install runs`() {
        assertEquals(AutoRootDecision.Run, decide())
    }

    @Test
    fun `the setting is what makes it automatic`() {
        assertEquals(AutoRootDecision.SkipDisabled, decide(enabled = false))
    }

    @Test
    fun `a boot that already has root is left alone`() {
        assertEquals(AutoRootDecision.SkipAlreadyRooted, decide(kernelSuActive = true))
    }

    @Test
    fun `an install verified in this boot is not repeated`() {
        // This is the userspace-restart case: BOOT_COMPLETED arrives again, the kernel boot id does
        // not change, and the install that already succeeded in this boot is still the answer.
        assertEquals(
            AutoRootDecision.SkipAlreadyVerified,
            decide(verifiedBootToken = THIS_BOOT),
        )
    }

    @Test
    fun `a boot whose attempt is spent does not get a second one`() {
        assertEquals(AutoRootDecision.SkipAttempted, decide(attemptedBootToken = THIS_BOOT))
    }

    @Test
    fun `without a verified install the user is asked for one`() {
        assertEquals(
            AutoRootDecision.NeedsPriorInstall,
            decide(hasVerifiedInstall = false),
        )
    }

    @Test
    fun `a boot with no load to make is refused for that reason`() {
        // Root on boot is on and there is a verified install, so every other rule would say run. What
        // stops it is that runs are told not to load KernelSU, which is a different answer from the
        // setting being off - and has to stay different, because one of them the user chose.
        assertEquals(
            AutoRootDecision.SkipKernelSuLoadingOff,
            decide(kernelSuLoadEnabled = false),
        )
    }

    @Test
    fun `the load decision outranks what the device looks like`() {
        // Nothing is loaded on a boot that is not allowed to load anything, so there is nothing for a
        // reading about KernelSU to change - including a reading that says it is already active.
        assertEquals(
            AutoRootDecision.SkipKernelSuLoadingOff,
            decide(kernelSuLoadEnabled = false, kernelSuActive = true),
        )
    }

    @Test
    fun `turning root on boot off still wins over the load decision`() {
        // Both are configuration, and the automation being off is the outer answer: someone reading
        // the log should not be told about KernelSU loading when the feature itself is switched off.
        assertEquals(
            AutoRootDecision.SkipDisabled,
            decide(enabled = false, kernelSuLoadEnabled = false),
        )
    }

    @Test
    fun `root already active outranks the setting and the receipt`() {
        // The state of the device is what decides, not the bookkeeping: if KernelSU is answering,
        // there is nothing to do whatever the settings or the stored tokens say.
        assertEquals(
            AutoRootDecision.SkipAlreadyRooted,
            decide(kernelSuActive = true, hasVerifiedInstall = false, verifiedBootToken = null),
        )
    }
}
