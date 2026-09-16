package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The helper's own line, as the payload prints it. Real output from a verified boot:
 * `KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3`.
 */
class KernelSuRuntimeTest {
    @Test
    fun `reads the control report out of helper output`() {
        val control = parseControlReport(
            "[*] late-load\nKernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3\n",
        )

        assertEquals(33214, control?.version)
        assertEquals(7, control?.flags)
        assertEquals(4, control?.uapi)
        assertEquals(3, control?.features)
    }

    @Test
    fun `a decimal flags field is read too`() {
        assertEquals(3, parseControlReport("version=33214 flags=3 uapi=4 features=3")?.flags)
    }

    @Test
    fun `a failed control check is not a report`() {
        // Exactly what the helper prints when the driver answered but the version is unusable.
        val failed = "late-load: KernelSU control check failed ret=-1 errno=13 version=0 flags=0x0"

        assertNull(parseControlReport(failed))
    }

    @Test
    fun `a zero version is never a live channel`() {
        assertNull(parseControlReport("KernelSU control verified version=0 flags=0x0 uapi=0 features=0x0"))
    }

    @Test
    fun `output without a report parses to nothing`() {
        assertNull(parseControlReport("late-load: KernelSU driver fd unavailable"))
        assertNull(parseControlReport(""))
    }

    @Test
    fun `a malformed field rejects the whole report`() {
        // Half a reading is not evidence, so it must not be kept as one.
        assertNull(parseControlReport("version=33214 flags=0xzz uapi=4 features=0x3"))
        assertNull(parseControlReport("version=33214 flags=0x7 uapi=4"))
    }

    @Test
    fun `no proof at all means the channel is not confirmed`() {
        assertTrue(controlProofs(nativeProbe = false, shizukuElevated = false, helperOutput = "").isEmpty())
    }

    @Test
    fun `the helper report alone is enough`() {
        val proofs = controlProofs(
            nativeProbe = false,
            shizukuElevated = false,
            helperOutput = "KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3",
        )

        assertEquals(setOf(ControlProof.HelperReport), proofs)
    }

    @Test
    fun `independent proofs are all reported`() {
        val proofs = controlProofs(
            nativeProbe = true,
            shizukuElevated = true,
            helperOutput = "KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3",
        )

        assertEquals(
            setOf(ControlProof.NativeProbe, ControlProof.ShizukuElevation, ControlProof.HelperReport),
            proofs,
        )
    }

    @Test
    fun `a successful exit code with no reading proves nothing`() {
        // This is the bug the proof set exists to catch: late-load returned, nothing answered after.
        val proofs = controlProofs(
            nativeProbe = false,
            shizukuElevated = false,
            helperOutput = "late-load: private mount namespace: mnt:[4026531840]",
        )

        assertTrue(proofs.isEmpty())
    }

    @Test
    fun `every proof carries a label for the log line`() {
        val labels = ControlProof.values().map { it.label }

        assertEquals(labels.size, labels.distinct().size)
        assertTrue(labels.all(String::isNotBlank))
    }
}
