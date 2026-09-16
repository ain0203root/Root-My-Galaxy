package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BOOT = "0f2a4c6e-1b2d-4f6a-8c0e-2d4f6a8c0e2d"
private const val ACCEPTED = "/data/local/tmp/.rmg-restart-zygote-accepted"


class RootRecoveryTest {

    // --- what the installed daemon can be asked to do -------------------------------------------------

    @Test
    fun `the daemon this app's feed installs has late-load but no soft reboot`() {
        // Verbatim shape of that daemon's clap command list: it lists late-load, and the only
        // mention of a soft reboot is a feature name.
        val help = """
            Usage: ksud <COMMAND>
            Commands:
              late-load    Load kernelsu.ko into a running kernel
              module       Manage KernelSU modules
              feature      Manage feature config
              emulated-soft-reboot
        """.trimIndent()

        val capabilities = parseKsudCapabilities(help)

        assertTrue(capabilities.lateLoad)
        assertFalse(capabilities.softReboot)
    }

    @Test
    fun `the fork's daemon is recognised as supporting a soft reboot`() {
        val help = "Commands:\n  late-load\n  soft-reboot\n  insmod\n  boot-patch"

        val capabilities = parseKsudCapabilities(help)

        assertTrue(capabilities.lateLoad)
        assertTrue(capabilities.softReboot)
    }

    @Test
    fun `a feature name containing the command's name is not the command`() {
        assertFalse(parseKsudCapabilities("emulated-soft-reboot").softReboot)
        assertFalse(parseKsudCapabilities("soft_reboot").softReboot)
        assertFalse(parseKsudCapabilities("soft-reboot-v2").softReboot)
    }

    @Test
    fun `help that lists nothing is nothing`() {
        assertFalse(parseKsudCapabilities("").any)
        assertFalse(parseKsudCapabilities("ksud: not found").any)
    }

    // --- the app's side of the handoff --------------------------------------------------------------

    @Test
    fun `the marker the child writes is what counts as accepted`() {
        val outcome = RootRecovery.parseHandoff("RMG_RECOVERY_ACCEPTED")

        assertTrue(outcome.accepted)
    }

    @Test
    fun `a child that refused says why, in words`() {
        val outcome = RootRecovery.parseHandoff("error:boot-changed")

        assertFalse(outcome.accepted)
        assertEquals("boot changed", outcome.detail)
    }

    @Test
    fun `a silent child is a refusal, not an acceptance`() {
        // The whole point of the handoff: a fork is not an action.
        assertFalse(RootRecovery.parseHandoff("").accepted)
        assertFalse(RootRecovery.parseHandoff("8\n").accepted)
        assertEquals("The recovery action did not answer", RootRecovery.parseHandoff("").detail)
    }

    @Test
    fun `the log tail a failed launch prints is not mistaken for success`() {
        val outcome = RootRecovery.parseHandoff("the recovery action did not acknowledge the request\n")

        assertFalse(outcome.accepted)
        assertTrue(outcome.detail.isNotBlank())
    }

    // --- the launcher ------------------------------------------------------------------------------

    @Test
    fun `the launcher writes the script whole, then detaches it`() {
        val command = RootRecovery.detachedLaunchCommand(
            script = "#!/system/bin/sh\necho hi\n",
            scriptPath = "/data/local/tmp/x.sh",
            logPath = "/data/local/tmp/x.log",
            acceptedPath = ACCEPTED,
        )

        // Written aside and moved into place, so a half-written script can never run.
        assertTrue(command.contains("tmp=\"\$script.tmp.\$\$\""))
        assertTrue(command.contains("chmod 0700 \"\$tmp\""))
        assertTrue(command.contains("mv -f \"\$tmp\" \"\$script\""))
        // Cleared before the child starts, so an earlier attempt's marker cannot pass for this one's.
        assertTrue(command.indexOf("rm -f -- \"\$accepted\"") < command.indexOf("setsid sh"))
        assertTrue(command.contains("setsid sh \"\$script\""))
    }

    @Test
    fun `the script body is embedded literally, not expanded by the outer shell`() {
        val script = "#!/system/bin/sh\necho \"\$EXPECTED_BOOT\"\n"
        val command = RootRecovery.detachedLaunchCommand(script, "/x.sh", "/x.log", ACCEPTED)

        assertTrue(command.contains("<<'RMG_RECOVERY_EOF'"))
        assertTrue(command.contains("echo \"\$EXPECTED_BOOT\""))
    }

    @Test
    fun `the launcher waits for the child and reports its answer`() {
        val command = RootRecovery.detachedLaunchCommand("#!/system/bin/sh\n", "/x.sh", "/x.log", ACCEPTED)

        assertTrue(command.contains("RMG_RECOVERY_ACCEPTED"))
        assertTrue(command.contains("sleep 0.1"))
        assertTrue(command.contains("tail -n 8"))
        // A child that never answers exits non-zero rather than looking scheduled.
        assertTrue(command.trimEnd().endsWith("exit 78"))
    }

    // --- the restart -------------------------------------------------------------------------------

    @Test
    fun `zygote is restarted through init, never killed`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED)

        assertTrue(script.contains("setprop ctl.restart zygote"))
        assertFalse(script.contains("kill"))
        assertFalse(script.contains("SIGKILL"))
    }

    @Test
    fun `the secondary zygote is restarted first, and only when it runs`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED)

        val secondary = script.indexOf("ctl.restart zygote_secondary")
        // The last occurrence, because the secondary command literally starts with the primary's.
        val primary = script.lastIndexOf("ctl.restart zygote")
        assertTrue(secondary in 0 until primary)
        assertTrue(script.contains("[ \"\$(getprop init.svc.zygote_secondary 2>/dev/null)\" = \"running\" ]"))
    }

    @Test
    fun `the restart validates root, the boot and a live framework before it acts`() {
        val script = RootRecovery.restartZygoteScript(BOOT, ACCEPTED)

        assertTrue(script.contains("[ \"\$(id -u 2>/dev/null)\" = \"0\" ] || reject_handoff 'not-root'"))
        assertTrue(script.contains("boot-changed"))
        assertTrue(script.contains("zygote-not-running"))
        // The acknowledgement follows those checks and precedes the restart, so the app hears about
        // the action while there is still a framework to hear it in.
        val accepted = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(accepted > 0)
        assertTrue(accepted < script.lastIndexOf("ctl.restart zygote"))
    }

    // --- the soft reboot ---------------------------------------------------------------------------

    @Test
    fun `the soft reboot is handed to KernelSU and consumes the installed daemon`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("\"\$KSUD\" soft-reboot"))
        assertTrue(script.contains("KSUD=/data/adb/ksud"))
        assertTrue(script.contains("[ -x \"\$KSUD\" ] || reject_handoff 'installed-ksud-missing'"))
        // It must not stage a daemon of its own or replay the load: the verified load owns both.
        assertFalse(script.contains("late-load"))
        assertFalse(script.contains("/data/local/tmp/ksud"))
    }

    @Test
    fun `only one soft reboot owns a kernel boot`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("another-soft-reboot-owns-this-boot"))
        assertTrue(script.contains("LOCK='/data/local/tmp/.rmg-soft-reboot-owner'"))
    }

    @Test
    fun `the soft reboot waits for the boot to finish and checks it has not changed`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("getprop sys.boot_completed"))
        assertTrue(script.contains("boot-not-completed"))
        assertTrue(script.contains("boot-changed"))
    }

    @Test
    fun `the reboot is not reported as scheduled before the daemon accepted it`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        val call = script.indexOf("\"\$KSUD\" soft-reboot")
        val publish = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(call > 0)
        assertTrue(publish > call)
        assertTrue(script.contains("ksud-soft-reboot-rc-"))
    }

    // --- the reboot ---------------------------------------------------------------------------------

    @Test
    fun `a soft reboot always answers inside the window the app waits in`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        val launcherWindowSeconds = RootRecovery.softRebootAcceptWindowSeconds
        val bootWait = numberOf(script, "while \\[ \"\\\$i\" -lt (\\d+) \\]")
        val daemonWatch = numberOf(script, "\\[ \"\\\$n\" -lt (\\d+) \\]")

        // The child's own worst case has to fit in the caller's, or the app reports a failure while
        // the child is still on its way to doing what was asked.
        assertTrue(bootWait + daemonWatch < launcherWindowSeconds)
    }

    @Test
    fun `a daemon that has not returned is stopped, never left to fire later`() {
        val script = RootRecovery.softRebootScript(BOOT, ACCEPTED)

        assertTrue(script.contains("\"\$KSUD\" soft-reboot &"))
        assertTrue(script.contains("kill -0 \"\$KSUD_PID\""))
        assertTrue(script.contains("kill \"\$KSUD_PID\""))
        assertTrue(script.contains("reject_handoff 'ksud-soft-reboot-timed-out'"))
    }

    private fun numberOf(script: String, pattern: String): Int =
        Regex(pattern).find(script)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("not found in the script: $pattern")

    @Test
    fun `the reboot flushes what the app persisted before it goes`() {
        val script = RootRecovery.rebootScript(BOOT, ACCEPTED)

        val accepted = script.indexOf("publish_handoff \"\$ACCEPTED_VALUE\"")
        assertTrue(accepted > 0)
        assertTrue(script.indexOf("sync") < accepted)
        assertTrue(script.contains("/system/bin/reboot"))
        assertTrue(script.contains("boot-changed"))
    }

    @Test
    fun `every action is scoped to the kernel boot it was asked for`() {
        val scripts = listOf(
            RootRecovery.restartZygoteScript(BOOT, ACCEPTED),
            RootRecovery.softRebootScript(BOOT, ACCEPTED),
            RootRecovery.rebootScript(BOOT, ACCEPTED),
        )

        scripts.forEach { script ->
            assertTrue(script.contains("EXPECTED_BOOT='$BOOT'"))
            assertTrue(script.contains("current_boot()"))
            assertTrue(script.contains("cat /proc/sys/kernel/random/boot_id"))
        }
    }
}
