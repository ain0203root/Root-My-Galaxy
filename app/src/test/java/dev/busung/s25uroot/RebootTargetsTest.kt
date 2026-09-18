package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reboot menu's two rules, both of which fail silently.
 *
 * A row offered on a tier that cannot ask for it is a button that does nothing where it should have been
 * greyed out, and an argument that is not a single word is a command that turns into two - so the target
 * list is checked as text rather than trusted.
 */
class RebootTargetsTest {

    @Test
    fun `a root shell can ask for every one of them`() {
        RebootTarget.entries.forEach { target ->
            assertTrue("root cannot ask for ${target.name}", ShellTier.Root.canAskFor(target))
            assertNull(
                "root is refused ${target.name}",
                rebootRefusalFor(ShellTier.Root, target),
            )
        }
    }

    @Test
    fun `a shizuku shell can reboot the phone, in every mode, but cannot restart the userspace`() {
        // The shell user holds the reboot permission - it is how `adb reboot recovery` works - so the
        // target travels as the command's own argument and none of these needs root.
        RebootTarget.entries
            .filterNot { it.viaDaemon }
            .forEach { target ->
                assertTrue(
                    "a Shizuku shell cannot ask for ${target.name}",
                    ShellTier.Unprivileged.canAskFor(target),
                )
            }

        // The soft restart is the daemon emulating a reboot, which is not a reboot request at all.
        assertFalse(ShellTier.Unprivileged.canAskFor(RebootTarget.SoftRestart))
        assertEquals(
            RebootRefusal.NeedsRoot,
            rebootRefusalFor(ShellTier.Unprivileged, RebootTarget.SoftRestart),
        )
    }

    @Test
    fun `no shell at all offers nothing, and says so`() {
        RebootTarget.entries.forEach { target ->
            assertFalse("nothing can ask for ${target.name}", ShellTier.None.canAskFor(target))
            assertEquals(
                RebootRefusal.NothingToAskWith,
                rebootRefusalFor(ShellTier.None, target),
            )
        }
    }

    @Test
    fun `the command asks for the target by name`() {
        assertEquals(
            listOf("/system/bin/reboot download", "/system/bin/svc power reboot download"),
            rebootCommands(RebootTarget.Download),
        )
        assertEquals(
            listOf("/system/bin/reboot recovery", "/system/bin/svc power reboot recovery"),
            rebootCommands(RebootTarget.Recovery),
        )
    }

    @Test
    fun `a plain reboot carries no argument`() {
        assertEquals(
            listOf("/system/bin/reboot", "/system/bin/svc power reboot"),
            rebootCommands(RebootTarget.Reboot),
        )
    }

    @Test
    fun `the daemon's own restart is not a reboot command`() {
        // It goes through the recovery actions, which own the checks that make it verifiable.
        assertTrue(rebootCommands(RebootTarget.SoftRestart).isEmpty())
    }

    @Test
    fun `every argument is one word`() {
        // The argument is pasted into a shell command, so a second word in it would be a second command:
        // the list is checked as text rather than trusted to stay well formed.
        val argument = Regex("[a-z]+")
        RebootTarget.entries.forEach { target ->
            val value = target.argument ?: return@forEach
            assertTrue("${target.name} carries '$value', which is not one lowercase word", argument.matches(value))
        }
    }

    @Test
    fun `the ones that leave Android are the ones that are confirmed first`() {
        assertEquals(
            listOf(
                RebootTarget.Recovery,
                RebootTarget.Bootloader,
                RebootTarget.Download,
                RebootTarget.Edl,
            ),
            RebootTarget.entries.filter { it.leavesAndroid },
        )
        // A plain reboot and a soft restart both come back to Android, so neither is a one-way door.
        assertFalse(RebootTarget.Reboot.leavesAndroid)
        assertFalse(RebootTarget.SoftRestart.leavesAndroid)
    }

    @Test
    fun `everything that leaves Android carries an argument, and the daemon's restart does not`() {
        RebootTarget.entries.forEach { target ->
            if (target.leavesAndroid || target == RebootTarget.SoftRestart) {
                assertTrue("${target.name} asks for nothing in particular", target.argument != null || target.viaDaemon)
            }
        }
        assertNull(RebootTarget.SoftRestart.argument)
    }
}
