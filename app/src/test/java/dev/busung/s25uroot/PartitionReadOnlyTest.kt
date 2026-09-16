package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionReadOnlyTest {

    @Test
    fun `the count is the last number the script printed`() {
        assertEquals(4, PartitionReadOnly.countFrom("4\n"))
        // A shell that said something on the way in must not have its words read as the answer.
        assertEquals(9, PartitionReadOnly.countFrom("blockdev: /dev/block/by-name/boot: ok\n9\n"))
    }

    @Test
    fun `an unreadable answer counts as no protection at all`() {
        // Reporting protection that was not obtained is the one outcome this must not have.
        assertEquals(0, PartitionReadOnly.countFrom(""))
        assertEquals(0, PartitionReadOnly.countFrom("blockdev: not found\n"))
        assertEquals(0, PartitionReadOnly.countFrom("0"))
        assertEquals(0, PartitionReadOnly.countFrom("-3"))
    }

    @Test
    fun `the script covers the partitions that turn a rooted phone into a brick`() {
        val script = File(scriptPath()).readText()
        for (partition in listOf("boot", "dtbo", "init_boot", "vendor_boot", "super", "optics", "prism", "vbmeta")) {
            assertTrue("missing $partition", script.contains(partition))
        }
        // Both slots: the device boots whichever one the bootloader selected, and only the inactive
        // slot is written during a normal update.
        assertTrue(script.contains("\${p}_a"))
        assertTrue(script.contains("\${p}_b"))
        // The flag is what makes the write fail; a script that merely listed the devices would do
        // nothing at all.
        assertTrue(script.contains("blockdev --setro"))
    }

    /** The asset, from whichever directory the test JVM was started in. */
    private fun scriptPath(): String {
        val relative = "src/main/assets/${PartitionReadOnly.SCRIPT_ASSET}"
        return if (File(relative).isFile) relative else "app/$relative"
    }
}
