package dev.busung.s25uroot

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import androidx.annotation.StringRes
import java.util.Locale

/**
 * What this app has left in `/data/local/tmp`, read the way a root detector reads it.
 *
 * The app stages there by necessity: the daemon, the helper and the exploit payload have to be
 * executable by a shell, and `/data/local/tmp` is the one directory that is both writable by the
 * transports this app uses and outside the app's own sandbox. The cost of that is that the staging is
 * *public in the way that matters*: the directory is mode `0771`, so any app on the device may reach a
 * path inside it by name, and the files this app leaves there are world-readable. A detector does not
 * need root or a shell to find them - it needs a list of names, and the names this app uses are fixed
 * and published in its own source.
 *
 * That is the whole reason this object exists, and it is why the reading is *this app's own context*
 * rather than a shell's. A shell reading would answer "what is on disk", which is a different and much
 * less interesting question: what matters is what another app can see, and the only honest way to
 * answer that is to look the way one would.
 *
 * ## Why a catalog instead of a directory listing
 *
 * `/data/local/tmp` is `drwxrwx--x shell shell`. The `--x` for other is deliberate, and it is exactly
 * the permission this check turns on: an app may traverse the directory but not read it, so it can
 * stat a path it already knows and cannot list the directory to discover one. Asked on this app's own
 * device, `ls /data/local/tmp` answers `Permission denied` while `ls -l /data/local/tmp/ksu-helper`
 * answers with the file. So the check enumerates the paths this app writes, from the same constants
 * the staging code uses, and there is deliberately no attempt to list anything: a listing is not
 * something this app is entitled to, and a check that pretended otherwise would report an empty
 * directory on every device.
 *
 * ## What this deliberately does not cover
 *
 * The app's own files - the run history, the app log, the cached payloads - are not here. Nothing
 * outside the app can read them, so they are not residue in the sense this is about. The catalog is
 * only the world-readable staging.
 */
internal enum class ResidueRole(@StringRes val labelRes: Int) {
    /** The KernelSU daemon. */
    Daemon(R.string.residue_role_daemon),

    /** The root helper the exploit is loaded with. */
    Helper(R.string.residue_role_helper),

    /** The exploit payload itself. */
    Payload(R.string.residue_role_payload),

    /** A script a repair action runs on the device. */
    Script(R.string.residue_role_script),

    /** A log a staged script writes. */
    Log(R.string.residue_role_log),

    /** A zero-byte or timestamp-only file a staged script coordinates through. */
    Marker(R.string.residue_role_marker),

    /** A socket the staged daemon leaves open. */
    Socket(R.string.residue_role_socket),
}

/**
 * One path this app is known to write, with what it is for.
 *
 * The role is carried because a name alone does not say it, and because the two names for one thing
 * (`ksu-helper` from the Shizuku route, `rmg-ksud-helper` from the wireless-ADB one) are otherwise
 * indistinguishable in a list of leftovers.
 */
internal data class StagedPath(val path: String, val role: ResidueRole) {
    /** The name on the device, which is what a detector's own catalog matches on. */
    val name: String get() = path.substringAfterLast('/')
}

/** What looking for a [StagedPath] found. */
internal sealed interface ResidueReading {
    /** It is there, with the size and age the filesystem reports. */
    data class Present(val sizeBytes: Long, val modifiedAtMillis: Long) : ResidueReading

    /** The filesystem answered, and it is not there. */
    data object Gone : ResidueReading

    /**
     * The filesystem did not answer, so this path is neither present nor absent.
     *
     * Its own case rather than being folded into [Gone], because the two mean opposite things to
     * whoever is reading: "gone" is good news and "unreadable" is no news at all. Reporting a denied
     * read as an absence is the same mistake as reporting a failed probe as a clean device, and this
     * app makes a point of not making it.
     */
    data object Unreadable : ResidueReading
}

/** One path and what came of looking for it. */
internal data class ResidueFinding(val staged: StagedPath, val reading: ResidueReading)

/**
 * The whole reading: every catalogued path, plus whether the app could see into the directory at all.
 *
 * The directory reading is the control. Without it, a page of "unreadable" cannot be told from a SELinux
 * policy that denies this app `/data/local/tmp` outright, and the two want different sentences: one is
 * about a file, the other is about the whole check being blind on this device.
 */
internal data class ResidueReport(
    val findings: List<ResidueFinding>,
    val directoryVisible: Boolean,
) {

    /** What is actually there, which is the list a detector would produce. */
    val present: List<ResidueFinding> get() = findings.filter { it.reading is ResidueReading.Present }

    /** Everything left behind, added up. */
    val totalBytes: Long
        get() = present.sumOf { (it.reading as ResidueReading.Present).sizeBytes }

    /** The oldest thing there, which is the one that says how long this has been accumulating. */
    val oldestMillis: Long?
        get() = present.minOfOrNull { (it.reading as ResidueReading.Present).modifiedAtMillis }

    /**
     * Whether this reading saw anything at all.
     *
     * True when the directory itself could not be stat'd, or when nothing in the catalog could be - the
     * two ways the check comes back with no information. An empty `present` list on a readable reading
     * is not this: it is a clean device, and it has to look like one.
     */
    val blind: Boolean
        get() = !directoryVisible ||
            (findings.isNotEmpty() && findings.all { it.reading is ResidueReading.Unreadable })

    /** The one line the card and the app log both say, so the two cannot disagree about the reading. */
    fun summaryLine(context: Context): String = when {
        blind -> context.getString(R.string.residue_blind)
        present.isEmpty() -> context.getString(R.string.residue_clean)
        else -> context.getString(
            R.string.residue_summary,
            present.size,
            StagedResidue.sizeLabel(totalBytes),
            StagedResidue.ageLabelOf(oldestMillis),
        )
    }

    /** The same facts with the names, which is what makes a line in the log actionable. */
    fun logLine(context: Context): String = when {
        blind -> context.getString(R.string.residue_log_blind, StagedResidue.DIRECTORY)
        present.isEmpty() -> context.getString(
            R.string.residue_log_clean,
            findings.size,
            StagedResidue.DIRECTORY,
        )
        else -> context.getString(
            R.string.residue_log_present,
            present.size,
            StagedResidue.sizeLabel(totalBytes),
            present.joinToString(", ") { it.staged.name },
        )
    }
}

/**
 * The check itself: a catalog, a stat per path, and the two label formats that go with them.
 *
 * Everything the answer depends on is either in the catalog or in [readingFor], which is pure and is
 * where the tests are. What is left here is the syscall and the arithmetic.
 */
internal object StagedResidue {

    /** Where the staging goes, which is also what the directory control is read for. */
    const val DIRECTORY = "/data/local/tmp"

    /**
     * Every path this app stages.
     *
     * Both transports are listed because both are reachable on one device: a run goes out through
     * Shizuku or through a paired adb, and whichever one it used leaves its own names behind. The
     * repair actions are here for the same reason, including the files their scripts only touch - a
     * marker that is never removed is still a file a detector can list.
     *
     * A hand-kept list, and that is the one thing about this object that can rot: a list that misses a
     * path reports a clean device for a file that is sitting right there. What keeps it whole is a test
     * that reads the app's own sources, collects every `/data/local/tmp/...` literal the staging code
     * writes, and fails when one of them is not in here - the same trick [ManifestPermissionTest] uses
     * for the permissions, and for the same reason.
     *
     * That scan can only cover the names this app writes itself, which is why one entry below is here
     * for a different reason: `temp_su.sock` is the daemon's, created by the staged binary rather than
     * by anything in this source tree. It is still this app's residue - it appears on a device because a
     * run of this app put the daemon there - and it is the one path no scan of this code could discover.
     */
    val catalog: List<StagedPath> = listOf(
        StagedPath("/data/local/tmp/ksud-s25u-kdp", ResidueRole.Daemon),
        StagedPath("/data/local/tmp/.ksud-stage", ResidueRole.Daemon),
        StagedPath("/data/local/tmp/temp_su.sock", ResidueRole.Socket),
        StagedPath("/data/local/tmp/ksu-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/ksu-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/ksu-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-ksud-helper", ResidueRole.Helper),
        StagedPath("/data/local/tmp/rmg-payload", ResidueRole.Payload),
        StagedPath("/data/local/tmp/rmg-exploit.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-restart-zygote.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-restart-zygote.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-restart-zygote-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-soft-reboot-keeper.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-soft-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-soft-reboot-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-soft-reboot-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmg-soft-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-reboot.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-reboot.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-reboot-accepted", ResidueRole.Marker),
        StagedPath("/data/local/tmp/rmg-reload-modules.sh", ResidueRole.Script),
        StagedPath("/data/local/tmp/rmg-reload-modules.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/rmg-reload-modules-ksud.log", ResidueRole.Log),
        StagedPath("/data/local/tmp/.rmg-reload-modules-owner", ResidueRole.Marker),
        StagedPath("/data/local/tmp/.rmg-reload-modules-accepted", ResidueRole.Marker),
    )

    /**
     * Reads the whole catalog.
     *
     * Blocking and unbuffered on purpose: it is a stat per path, so it is tens of microseconds of work
     * rather than a shell, and a caller that wants it off the main thread only has to say so.
     */
    fun read(): ResidueReport = ResidueReport(
        findings = catalog.map { staged -> ResidueFinding(staged, readingOf(staged.path)) },
        directoryVisible = runCatching { Os.stat(DIRECTORY) }.isSuccess,
    )

    /** One path, as a reading. */
    private fun readingOf(path: String): ResidueReading {
        val attempt = runCatching { Os.stat(path) }
        val stat = attempt.getOrNull()
            ?: return readingFor((attempt.exceptionOrNull() as? ErrnoException)?.errno ?: UNKNOWN_ERRNO)
        return ResidueReading.Present(
            sizeBytes = stat.st_size,
            // Seconds on the device, milliseconds everywhere else in this app.
            modifiedAtMillis = stat.st_mtime * 1_000L,
        )
    }

    /**
     * What an errno means for the check. The one rule here that is worth a test of its own.
     *
     * Only "there is no such file" is reported as an absence, and everything else - a denied read, a
     * loop, an error this app has never seen - is reported as no answer. The default is the point: a
     * stat this app cannot explain must never come back as a device with nothing on it.
     *
     * The two errnos are written out rather than read from `OsConstants`, and that is a deliberate
     * trade: `OsConstants` holds no compile-time values - they are filled in from the platform at
     * runtime - so a local unit test sees every one of them as zero and could not tell this rule from
     * any other. What is written here instead is the Linux ABI's own numbering, which no Android
     * device varies: `ENOENT` is 2 and `ENOTDIR` is 20 on every architecture this app ships for.
     */
    internal fun readingFor(errno: Int): ResidueReading =
        if (errno == ERRNO_NO_SUCH_FILE || errno == ERRNO_NOT_A_DIRECTORY) {
            ResidueReading.Gone
        } else {
            ResidueReading.Unreadable
        }

    /** `ENOENT`: the name is not there. */
    private const val ERRNO_NO_SUCH_FILE = 2

    /** `ENOTDIR`: a path component is not a directory, which is the same answer arrived at differently. */
    private const val ERRNO_NOT_A_DIRECTORY = 20

    /** The size a person reads, which is the one the platform's own tools would print. */
    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_000_000L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** How long something has been sitting there, from its own timestamp. */
    fun ageLabelOf(modifiedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        val stamps = modifiedAtMillis ?: return "unknown"
        val days = (nowMillis - stamps).coerceAtLeast(0L) / DAY_MILLIS
        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days"
            days < 14L -> "a week"
            days < 60L -> "${days / 7} weeks"
            days < 365L -> "${days / 30} months"
            else -> "${days / 365} years"
        }
    }

    private const val DAY_MILLIS = 86_400_000L

    /** What an exception that is not an [ErrnoException] is treated as, which is "no answer". */
    private const val UNKNOWN_ERRNO = Int.MIN_VALUE
}
