package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading the running KernelSU, and comparing a manager against it.
 *
 * The readings are taken from the two commands KernelSU's own source defines - `ksud --version`
 * (`defs::FULL_VERSION`, which is `"{VERSION_NAME} (uapi: {n})"`) and `ksud debug version`
 * (`"Kernel Version: {n}"`) - so what is pinned here is what those actually print, including the
 * protocol number sitting inside the same line as the version. A parser that took the wrong number
 * would compare a manager's `3.3.0` against a `3` and report a mismatch on every device.
 */
class KernelSuVersionTest {

    // --- the daemon's own version ---------------------------------------------------------------------

    @Test
    fun `the release is read out of ksud's version line`() {
        // clap prints the binary's name before the version string.
        assertEquals("3.3.0", parseKsudVersion("ksud 3.3.0 (uapi: 3)"))
        // And the bare FULL_VERSION when something prints it without the name.
        assertEquals("3.3.0", parseKsudVersion("3.3.0 (uapi: 3)"))
        assertEquals("3.3.0", parseKsudVersion("KernelSU-Next 3.3.0 (uapi: 4)"))
    }

    /** The `uapi` number is a protocol, not a version, and it is what must not be mistaken for one. */
    @Test
    fun `a line with only a protocol number names no version`() {
        assertNull(parseKsudVersion("ksud (uapi: 3)"))
        assertNull(parseKsudVersion(""))
        assertNull(parseKsudVersion("error: unexpected argument '-V' found"))
    }

    // --- the kernel-side module -----------------------------------------------------------------------

    @Test
    fun `the kernel version code is read from its own line`() {
        assertEquals(32601, parseKernelVersionCode("Kernel Version: 32601"))
        assertEquals(32601, parseKernelVersionCode("  Kernel Version:   32601  "))
    }

    /** Zero is how the call reports a module that is not there to answer. */
    @Test
    fun `a zero kernel version is not a reading`() {
        assertNull(parseKernelVersionCode("Kernel Version: 0"))
        assertNull(parseKernelVersionCode("no output"))
    }

    // --- one round trip, both answers -----------------------------------------------------------------

    @Test
    fun `both readings come out of the markers they were printed under`() {
        val reading = parseVersionReadings(
            """
            RMG_DAEMON=ksud 3.3.0 (uapi: 3)
            RMG_KERNEL=Kernel Version: 32601
            """.trimIndent(),
        )

        assertEquals("3.3.0", reading.daemon)
        assertEquals(32601, reading.kernelCode)
        assertEquals(true, reading.isRead)
    }

    /** A `ksud` that is not there prints nothing for its own line and leaves the other one intact. */
    @Test
    fun `one silent command does not swallow the other`() {
        val reading = parseVersionReadings("RMG_DAEMON=\nRMG_KERNEL=Kernel Version: 32601")

        assertNull(reading.daemon)
        assertEquals(32601, reading.kernelCode)
    }

    @Test
    fun `nothing answered reads as nothing, not as a version`() {
        val reading = parseVersionReadings("RMG_DAEMON=\nRMG_KERNEL=")

        assertEquals(KernelSuVersionReading(), reading)
        assertEquals(false, reading.isRead)
    }

    // --- manager against kernel -----------------------------------------------------------------------

    @Test
    fun `a manager and a kernel from the same release match`() {
        assertEquals(
            ManagerVersionState.Matching,
            managerVersionState("3.3.0", "3.3.0"),
        )
    }

    /**
     * A build's suffix is not a release.
     *
     * The daemon's `VERSION_NAME` comes from `git describe`, so it can carry a suffix the manager's own
     * `versionName` does not - and treating that as a mismatch would warn about a pair that is right.
     */
    @Test
    fun `a suffix and a leading v do not make a different release`() {
        assertEquals(ManagerVersionState.Matching, managerVersionState("3.3.0", "3.3.0-ksun"))
        assertEquals(ManagerVersionState.Matching, managerVersionState("v3.3.0", "3.3.0"))
    }

    @Test
    fun `a manager from another line is the case this exists for`() {
        assertEquals(
            ManagerVersionState.Differing,
            managerVersionState("3.2.5", "3.3.0"),
        )
        assertEquals(
            ManagerVersionState.Differing,
            managerVersionState("3.3.0", "3.4.1"),
        )
    }

    /** Nothing to compare, so nothing is claimed: neither a warning nor an all-clear. */
    @Test
    fun `a missing reading leaves the pair unknown`() {
        assertEquals(ManagerVersionState.Unknown, managerVersionState(null, "3.3.0"))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("3.3.0", null))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("", null))
        assertEquals(ManagerVersionState.Unknown, managerVersionState("unknown", "3.3.0"))
    }

    // --- the fix offered with the warning -------------------------------------------------------------

    /**
     * The button under a mismatch installs the version the *kernel* is running.
     *
     * Not the flavour's default, which is a decision this app made and may itself be the thing that is
     * wrong: the phone is the one that knows what the manager has to talk to.
     */
    @Test
    fun `a mismatch offers the running version to install`() {
        assertEquals(
            "3.3.0",
            managerMismatchTarget(ManagerVersionState.Differing, "3.3.0"),
        )
    }

    /** A button that installs something on a row with no problem to fix is the thing to avoid. */
    @Test
    fun `nothing is offered when there is no mismatch to act on`() {
        assertNull(managerMismatchTarget(ManagerVersionState.Matching, "3.3.0"))
        assertNull(managerMismatchTarget(ManagerVersionState.Unknown, "3.3.0"))
        assertNull(managerMismatchTarget(ManagerVersionState.Differing, null))
    }
}
