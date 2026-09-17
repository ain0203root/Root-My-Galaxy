package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions the recovery actions make before they can run anything.
 *
 * Both exist because the first version of them was wrong in the same way: it asked one question - can
 * this app get a root shell - and reported the answer as if it were about the phone, so a device with
 * KernelSU loaded and working was told "KernelSU root is not available for this boot" whenever the app
 * had not been granted root yet. The words are the whole point of the fix: which of those two things is
 * true decides whether the user goes looking for a missing install or for a missing grant.
 */
class RecoveryRefusalTest {

    @Test
    fun `a device with KernelSU loaded is refused for the missing shell, not for missing root`() {
        assertEquals(RecoveryRefusal.ShellMissing, recoveryRefusal(rootLoadedInThisBoot = true))
    }

    @Test
    fun `a device with nothing loaded is refused for the missing root`() {
        assertEquals(RecoveryRefusal.RootMissing, recoveryRefusal(rootLoadedInThisBoot = false))
    }

    @Test
    fun `a root id is root`() {
        // What KernelSU's own `su` prints for an approved app, context and all.
        assertTrue(
            isRootAnswer(
                ShizukuController.ShellResult(
                    exitCode = 0,
                    output = "uid=0(root) gid=0(root) groups=0(root) context=u:r:su:s0",
                ),
            ),
        )
    }

    @Test
    fun `an exit of zero without root is not root`() {
        // The shell uid answering happily, which is what an unapproved caller gets when `su` decides
        // to do nothing rather than to refuse loudly.
        assertFalse(
            isRootAnswer(
                ShizukuController.ShellResult(
                    exitCode = 0,
                    output = "uid=2000(shell) gid=2000(shell) groups=2000(shell)",
                ),
            ),
        )
    }

    @Test
    fun `a refusal is not root, whatever it prints`() {
        assertFalse(
            isRootAnswer(
                ShizukuController.ShellResult(
                    exitCode = 1,
                    output = "su: permission denied; uid=0 does not appear here",
                ),
            ),
        )
    }

    @Test
    fun `nothing answering is not root`() {
        // What a `su` that never answered comes back as, and the reason the direct route reports
        // "no shell" rather than guessing at a result.
        assertFalse(isRootAnswer(null))
    }

    @Test
    fun `a failed command that also printed root is not root`() {
        // Both halves are required: the Shizuku route makes the same test, and the two routes have to
        // agree about what a root shell is or the fallback becomes a source of disagreement.
        assertFalse(
            isRootAnswer(
                ShizukuController.ShellResult(
                    exitCode = 127,
                    output = "uid=0(root) gid=0(root) sh: id: not found",
                ),
            ),
        )
    }
}
