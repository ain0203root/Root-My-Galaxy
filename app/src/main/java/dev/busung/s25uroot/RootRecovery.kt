package dev.busung.s25uroot

import java.io.File

/** What a recovery action ended as: whether it was accepted, and what to tell the user. */
internal data class RecoveryOutcome(
    val accepted: Boolean,
    val detail: String,
)

/**
 * What the installed KernelSU daemon can be asked to do, as its own help output states it.
 *
 * This exists because the daemon is not the same across payload feeds. The daemon the feed this app
 * ships with serves is a patched build whose own command table lists `late-load`, `soft-reboot`
 * ("Emulate system reboot"), `insmod` and `unload`, but a feed may serve a daemon without
 * `soft-reboot`, and offering a button that can only fail is worse than not offering it. So the
 * action is offered only when the installed daemon's own help lists it, and the match is whole-token
 * because the same binary mentions `emulated-soft-reboot` elsewhere - a name that must not be read as
 * the command.
 */
internal data class KsudCapabilities(
    val softReboot: Boolean = false,
    val lateLoad: Boolean = false,
) {
    val any: Boolean get() = softReboot || lateLoad
}

/**
 * Reads a daemon's command list out of its help output.
 *
 * Tokens are matched whole: `emulated-soft-reboot` is a feature name and must not be read as the
 * `soft-reboot` command.
 */
internal fun parseKsudCapabilities(help: String): KsudCapabilities = KsudCapabilities(
    softReboot = help.containsWord("soft-reboot"),
    lateLoad = help.containsWord("late-load"),
)

private fun String.containsWord(word: String): Boolean =
    Regex("(?<![A-Za-z0-9_-])${Regex.escape(word)}(?![A-Za-z0-9_-])").containsMatchIn(this)

/** The kernel's boot id, which every action here is scoped to. */
internal fun kernelBootToken(): String? = runCatching {
    File("/proc/sys/kernel/random/boot_id")
        .readText(Charsets.US_ASCII)
        .trim()
        .takeIf(String::isNotBlank)
}.getOrNull()

/**
 * Explicit post-root repair actions.
 *
 * These are the actions that are only possible once KernelSU is verified, and they are deliberately
 * kept away from the exploit path: nothing here acquires bootstrap root, replays the exploit, or
 * stages a payload. Every one of them runs its real work in a detached root shell that validates the
 * conditions itself - root, the same kernel boot, and a live Zygote for the restart - and then writes
 * an acknowledgement the app reads back. A successful fork is deliberately *not* enough: the app must
 * not report an action as scheduled when the child found the boot had already changed, or that the
 * framework was not running to restart.
 */
internal object RootRecovery {

    /** Where the verified late-load leaves the daemon. */
    private const val KSUD_PATH = "/data/adb/ksud"

    private const val ACCEPTED_MARKER = "RMG_RECOVERY_ACCEPTED"
    internal const val ACCEPT_POLL_ATTEMPTS = 100
    internal const val ACCEPT_POLL_INTERVAL_SEC = "0.1"

    /**
     * How long the app holds the channel open for the soft reboot, which is the window the child's
     * own worst case has to fit inside.
     */
    internal val softRebootAcceptWindowSeconds: Double
        get() = ACCEPT_POLL_ATTEMPTS * 2 * ACCEPT_POLL_INTERVAL_SEC.toDouble()

    /** A detached child's own words, read back from the acknowledgement. */
    internal fun parseHandoff(output: String): RecoveryOutcome {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.any { it == ACCEPTED_MARKER }) return RecoveryOutcome(accepted = true, detail = "")
        val childError = lines.firstOrNull { it.startsWith("error:") }
            ?.removePrefix("error:")
            ?.replace('-', ' ')
        return RecoveryOutcome(
            accepted = false,
            detail = childError ?: lines.lastOrNull() ?: "The recovery action did not answer",
        )
    }

    /** Asks the installed daemon what it supports; null when there is no daemon to ask. */
    internal fun capabilities(
        shell: (String) -> ShizukuController.ShellResult,
    ): KsudCapabilities? {
        val probe = shell("test -x $KSUD_PATH && $KSUD_PATH --help 2>&1 || true")
        val help = probe.output
        if (probe.exitCode != 0 && help.isBlank()) return null
        if (!help.containsWord("late-load") && !help.containsWord("soft-reboot")) return null
        return parseKsudCapabilities(help)
    }

    /**
     * Recreates the Android runtime through init, which is the only supported way to make an already
     * mounted module take effect without a reboot: `setprop ctl.restart zygote` asks init to restart
     * the service it owns, where killing Zygote from here would leave init to notice and recover by
     * accident. Running apps are closed, which is the price of the restart and is stated in the UI.
     *
     * The secondary Zygote, when the device runs one, is restarted first: it is the one that can be
     * restarted without the framework going down, so a failure there is still reportable.
     */
    suspend fun restartZygote(
        shell: (String) -> ShizukuController.ShellResult,
        bootToken: String,
    ): RecoveryOutcome = runDetached(
        shell = shell,
        scriptPath = "/data/local/tmp/rmg-restart-zygote.sh",
        logPath = "/data/local/tmp/rmg-restart-zygote.log",
        acceptedPath = "/data/local/tmp/.rmg-restart-zygote-accepted",
        script = restartZygoteScript(bootToken, "/data/local/tmp/.rmg-restart-zygote-accepted"),
    )

    /**
     * Hands the userspace transition to KernelSU's own `soft-reboot`, which stops and restarts the
     * Android userspace and walks the module lifecycle in its normal order.
     *
     * The keeper is a single owner per kernel boot: it takes a lock carrying the boot id, so a second
     * request in the same boot is told that the first one already owns it rather than racing it. The
     * daemon is never replaced and late-load is never replayed from here - consuming the daemon the
     * verified load installed is the whole point.
     */
    suspend fun softReboot(
        shell: (String) -> ShizukuController.ShellResult,
        bootToken: String,
        capabilities: KsudCapabilities,
    ): RecoveryOutcome {
        if (!capabilities.softReboot) {
            return RecoveryOutcome(
                accepted = false,
                detail = "The installed KernelSU has no soft-reboot command",
            )
        }
        return runDetached(
            shell = shell,
            scriptPath = "/data/local/tmp/rmg-soft-reboot-keeper.sh",
            logPath = "/data/local/tmp/rmg-soft-reboot.log",
            acceptedPath = "/data/local/tmp/.rmg-soft-reboot-accepted",
            script = softRebootScript(bootToken, "/data/local/tmp/.rmg-soft-reboot-accepted"),
            acceptPollAttempts = ACCEPT_POLL_ATTEMPTS * 2,
        )
    }

    /**
     * Restarts the phone with root switched off, which is what removes root: KernelSU is loaded into
     * the running kernel, so nothing about it survives a reboot by itself - what brings it back is
     * this app's own root-on-boot setting. The setting is cleared *before* the reboot is requested,
     * because a reboot that happened first would come back rooted.
     */
    suspend fun rebootAndUnroot(
        shell: (String) -> ShizukuController.ShellResult,
        bootToken: String,
    ): RecoveryOutcome = runDetached(
        shell = shell,
        scriptPath = "/data/local/tmp/rmg-reboot.sh",
        logPath = "/data/local/tmp/rmg-reboot.log",
        acceptedPath = "/data/local/tmp/.rmg-reboot-accepted",
        script = rebootScript(bootToken, "/data/local/tmp/.rmg-reboot-accepted"),
    )

    private suspend fun runDetached(
        shell: (String) -> ShizukuController.ShellResult,
        scriptPath: String,
        logPath: String,
        acceptedPath: String,
        script: String,
        acceptPollAttempts: Int = ACCEPT_POLL_ATTEMPTS,
    ): RecoveryOutcome {
        val command = detachedLaunchCommand(
            script = script,
            scriptPath = scriptPath,
            logPath = logPath,
            acceptedPath = acceptedPath,
            acceptPollAttempts = acceptPollAttempts,
        )
        val launch = runCatching { shell(command) }.getOrElse { error ->
            return RecoveryOutcome(
                accepted = false,
                detail = error.message ?: error.javaClass.simpleName,
            )
        }
        val outcome = parseHandoff(launch.output)
        if (outcome.accepted) return outcome
        return RecoveryOutcome(
            accepted = false,
            detail = outcome.detail.ifBlank { "The recovery action was refused (exit ${launch.exitCode})" },
        )
    }

    /**
     * Installs [script] and starts it detached, then waits for the child's own acknowledgement.
     *
     * The launch half is careful about ordering for two reasons: the script is written to a temporary
     * name and moved into place so a half-written script can never be executed, and the
     * acknowledgement file is removed *before* the child starts, so a marker left by an earlier attempt
     * cannot be mistaken for this one's.
     */
    internal fun detachedLaunchCommand(
        script: String,
        scriptPath: String,
        logPath: String,
        acceptedPath: String,
        acceptPollAttempts: Int = ACCEPT_POLL_ATTEMPTS,
    ): String = buildString {
        append("set -eu\n")
        append("script=${shellQuote(scriptPath)}\n")
        append("accepted=${shellQuote(acceptedPath)}\n")
        append("tmp=\"\$script.tmp.\$\$\"\n")
        append("cat > \"\$tmp\" <<'RMG_RECOVERY_EOF'\n")
        append(script)
        if (!script.endsWith('\n')) append('\n')
        append("RMG_RECOVERY_EOF\n")
        append("chmod 0700 \"\$tmp\"\n")
        append("mv -f \"\$tmp\" \"\$script\"\n")
        append("rm -f -- \"\$accepted\"\n")
        append(": > ${shellQuote(logPath)}\n")
        append("chmod 0666 ${shellQuote(logPath)} 2>/dev/null || true\n")
        append("setsid sh \"\$script\" >>${shellQuote(logPath)} 2>&1 < /dev/null &\n")
        append("i=0\n")
        append("while [ \"\$i\" -lt $acceptPollAttempts ]; do\n")
        append("  if [ -s \"\$accepted\" ]; then\n")
        append("    ack=\"\$(cat \"\$accepted\" 2>/dev/null || true)\"\n")
        append("    rm -f -- \"\$accepted\"\n")
        append("    printf '%s\\n' \"\$ack\"\n")
        append("    [ \"\$ack\" = '$ACCEPTED_MARKER' ] && exit 0\n")
        append("    exit 78\n")
        append("  fi\n")
        append("  i=\$((i + 1))\n")
        append("  sleep $ACCEPT_POLL_INTERVAL_SEC\n")
        append("done\n")
        append("rm -f -- \"\$accepted\"\n")
        append("echo 'the recovery action did not acknowledge the request' >&2\n")
        append("tail -n 8 ${shellQuote(logPath)} >&2 2>/dev/null || true\n")
        append("exit 78\n")
    }

    /**
     * The restart's own side of the contract: root, the same kernel boot, and a live Zygote, all
     * checked before anything is restarted, and the acknowledgement written only once those hold.
     *
     * The short sleep before the restart is not decoration: the app has to be able to read the
     * acknowledgement and persist what it says before the framework it is running in goes away.
     */
    internal fun restartZygoteScript(bootToken: String, acceptedPath: String): String = """
        #!/system/bin/sh
        EXPECTED_BOOT=${shellQuote(bootToken)}
        ACCEPTED=${shellQuote(acceptedPath)}
        ACCEPTED_VALUE='$ACCEPTED_MARKER'
        current_boot() { cat /proc/sys/kernel/random/boot_id 2>/dev/null; }
        publish_handoff() {
            printf '%s\n' "${'$'}1" > "${'$'}ACCEPTED" || exit 79
            chmod 0666 "${'$'}ACCEPTED" 2>/dev/null || true
        }
        reject_handoff() {
            publish_handoff "error:${'$'}1"
            rm -f -- "${'$'}0"
            exit 0
        }

        [ "${'$'}(id -u 2>/dev/null)" = "0" ] || reject_handoff 'not-root'
        [ "${'$'}(current_boot)" = "${'$'}EXPECTED_BOOT" ] || reject_handoff 'boot-changed'
        [ "${'$'}(getprop init.svc.zygote 2>/dev/null)" = "running" ] || reject_handoff 'zygote-not-running'

        if [ "${'$'}(getprop init.svc.zygote_secondary 2>/dev/null)" = "running" ]; then
            setprop ctl.restart zygote_secondary || reject_handoff 'zygote-secondary-restart-failed'
        fi

        publish_handoff "${'$'}ACCEPTED_VALUE"

        sleep 0.75
        rm -f -- "${'$'}0"
        setprop ctl.restart zygote
    """.trimIndent() + "\n"

    /**
     * The soft reboot's single owner for this kernel boot.
     *
     * It waits for `sys.boot_completed` because a userspace transition requested before Android is up
     * has nothing to stop in order, and it refuses to run when the boot id has changed under it. The
     * daemon must exist and be executable: a missing `/data/adb/ksud` means the verified load did not
     * leave one, which is a failure to report rather than a reason to fetch or stage another daemon
     * from here.
     *
     * Two waits are bounded on purpose, so that the child always answers *inside* the window the app
     * is waiting in, and never acts after the app has given up on it: the wait for Android to come up
     * is shorter than that window, and the daemon itself is watched, so a `soft-reboot` that has not
     * returned is stopped and reported instead of being left to fire a userspace transition the app
     * already reported as failed.
     *
     * The lock records its owner's pid as well as the boot, and only a lock whose owner is still
     * running is an owner: a keeper that was killed mid-transition would otherwise refuse every later
     * attempt for the rest of the boot. The check fails safe - if the pid has since been reused by an
     * unrelated process, the lock is respected and the request is refused rather than doubled.
     */
    internal fun softRebootScript(bootToken: String, acceptedPath: String): String = """
        #!/system/bin/sh
        EXPECTED_BOOT=${shellQuote(bootToken)}
        ACCEPTED=${shellQuote(acceptedPath)}
        ACCEPTED_VALUE='$ACCEPTED_MARKER'
        LOCK='/data/local/tmp/.rmg-soft-reboot-owner'
        KSUD_OUT='/data/local/tmp/rmg-soft-reboot-ksud.log'
        KSUD=$KSUD_PATH

        log() { echo "[keeper] ${'$'}(date +%s 2>/dev/null) ${'$'}*"; }
        current_boot() { cat /proc/sys/kernel/random/boot_id 2>/dev/null; }
        publish_handoff() {
            printf '%s\n' "${'$'}1" > "${'$'}ACCEPTED" || exit 79
            chmod 0666 "${'$'}ACCEPTED" 2>/dev/null || true
        }
        reject_handoff() {
            publish_handoff "error:${'$'}1"
            rm -f -- "${'$'}0"
            exit 0
        }

        [ "${'$'}(id -u 2>/dev/null)" = "0" ] || reject_handoff 'not-root'
        [ "${'$'}(current_boot)" = "${'$'}EXPECTED_BOOT" ] || reject_handoff 'boot-changed'
        [ -x "${'$'}KSUD" ] || reject_handoff 'installed-ksud-missing'

        if ! mkdir "${'$'}LOCK" 2>/dev/null; then
            LOCK_BOOT="${'$'}(cat "${'$'}LOCK/boot_id" 2>/dev/null)"
            LOCK_PID="${'$'}(cat "${'$'}LOCK/pid" 2>/dev/null)"
            if [ "${'$'}LOCK_BOOT" = "${'$'}EXPECTED_BOOT" ] && [ -n "${'$'}LOCK_PID" ] && \
               kill -0 "${'$'}LOCK_PID" 2>/dev/null; then
                reject_handoff 'another-soft-reboot-owns-this-boot'
            fi
            # A lock whose owner is gone is stale, not an owner: a keeper killed mid-transition would
            # otherwise lock this boot out of every later attempt. Taking it over is safe because a
            # request that is no longer running cannot be part-way through one.
            log "taking over a lock left by a keeper that is no longer running (pid ${'$'}LOCK_PID)"
            rm -rf -- "${'$'}LOCK" 2>/dev/null
            mkdir "${'$'}LOCK" 2>/dev/null || reject_handoff 'lock-failed'
        fi
        printf '%s\n' "${'$'}EXPECTED_BOOT" > "${'$'}LOCK/boot_id" 2>/dev/null
        printf '%s\n' "${'$'}${'$'}" > "${'$'}LOCK/pid" 2>/dev/null
        cleanup() { rm -rf -- "${'$'}LOCK" 2>/dev/null; }
        trap cleanup EXIT INT TERM

        i=0
        while [ "${'$'}i" -lt 10 ]; do
            [ "${'$'}(getprop sys.boot_completed 2>/dev/null)" = "1" ] && break
            i=${'$'}((i + 1))
            sleep 1
        done
        [ "${'$'}(getprop sys.boot_completed 2>/dev/null)" = "1" ] || reject_handoff 'boot-not-completed'

        [ "${'$'}(current_boot)" = "${'$'}EXPECTED_BOOT" ] || reject_handoff 'boot-changed'

        # The daemon's own account of what it did is what a failure has to show: a bare exit code
        # sends the user looking for a cause that the daemon already printed.
        : > "${'$'}KSUD_OUT" 2>/dev/null || true
        chmod 0666 "${'$'}KSUD_OUT" 2>/dev/null || true
        ksud_words() { tail -n 1 "${'$'}KSUD_OUT" 2>/dev/null | tr -d '\"' | cut -c 1-160; }

        log "requesting KernelSU native soft reboot"
        "${'$'}KSUD" soft-reboot >>"${'$'}KSUD_OUT" 2>&1 &
        KSUD_PID=${'$'}!
        n=0
        while kill -0 "${'$'}KSUD_PID" 2>/dev/null && [ "${'$'}n" -lt 8 ]; do
            n=${'$'}((n + 1))
            sleep 1
        done
        if kill -0 "${'$'}KSUD_PID" 2>/dev/null; then
            kill "${'$'}KSUD_PID" 2>/dev/null
            wait "${'$'}KSUD_PID" 2>/dev/null
            reject_handoff "ksud-soft-reboot-timed-out ${'$'}(ksud_words)"
        fi
        wait "${'$'}KSUD_PID"
        RC=${'$'}?
        [ "${'$'}RC" = "0" ] || reject_handoff "ksud-soft-reboot-failed-rc-${'$'}RC ${'$'}(ksud_words)"

        # The daemon hands the transition to a detached worker and returns, so a zero here means the
        # request was accepted - which is the only thing the app may report as scheduled. It is run
        # watched rather than plainly: a daemon that has not returned is stopped and reported, because
        # a transition that starts after the app gave up on it would be an action nobody asked for.
        publish_handoff "${'$'}ACCEPTED_VALUE"
        log "KernelSU native soft reboot accepted"
    """.trimIndent() + "\n"

    /** `sync` first, so what the app persisted before the reboot is on disk when it happens. */
    internal fun rebootScript(bootToken: String, acceptedPath: String): String = """
        #!/system/bin/sh
        EXPECTED_BOOT=${shellQuote(bootToken)}
        ACCEPTED=${shellQuote(acceptedPath)}
        ACCEPTED_VALUE='$ACCEPTED_MARKER'
        current_boot() { cat /proc/sys/kernel/random/boot_id 2>/dev/null; }
        publish_handoff() {
            printf '%s\n' "${'$'}1" > "${'$'}ACCEPTED" || exit 79
            chmod 0666 "${'$'}ACCEPTED" 2>/dev/null || true
        }
        reject_handoff() {
            publish_handoff "error:${'$'}1"
            rm -f -- "${'$'}0"
            exit 0
        }

        [ "${'$'}(id -u 2>/dev/null)" = "0" ] || reject_handoff 'not-root'
        [ "${'$'}(current_boot)" = "${'$'}EXPECTED_BOOT" ] || reject_handoff 'boot-changed'
        [ -x /system/bin/reboot ] || reject_handoff 'reboot-command-missing'

        sync
        publish_handoff "${'$'}ACCEPTED_VALUE"

        sleep 0.75
        rm -f -- "${'$'}0"
        /system/bin/reboot
    """.trimIndent() + "\n"
}
