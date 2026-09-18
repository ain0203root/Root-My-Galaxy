package dev.busung.s25uroot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything a report says about the phone, already resolved to the words it will be read in.
 *
 * Strings rather than the app's own types, because this is the one place where the *text* is the product: a
 * report is read by someone who has none of this app's state, in a thread, next to a photo of a screen. Kept
 * as values first and text second, so what goes into the block can be checked without a device - the part
 * that needs one is [diagnosticFacts], which is a dozen readings and no judgement.
 */
internal data class DiagnosticFacts(
    val appLabel: String,
    val deviceLabel: String,
    val kernelLabel: String,
    val bootLabel: String,
    val rootLabel: String,
    val shizukuLabel: String,
    val settingsLabel: String,
    val lastRunLabel: String,
    val residueLabel: String,
    val preflightLabel: String?,
    /** The last lines of the app log, oldest first. */
    val logTail: List<String>,
)

/** How many log lines a report carries, which is a tail and not the log. */
internal const val REPORT_LOG_TAIL_LINES = 60

/**
 * The report, as the block that gets pasted.
 *
 * One block rather than a checklist to be assembled by hand, because the question it answers - "why did it
 * not work on my phone" - is answered badly by a screenshot of a log and well by this. It is also the only
 * place that says the app's build and the phone's identity *together*: a run that failed on a build nobody
 * remembers installing, on a firmware nobody wrote down, is a thread that goes nowhere.
 *
 * Section headings in plain words and no punctuation to speak of, so it survives being pasted into a forum,
 * a GitHub issue, or a chat. The log tail is last because it is the longest thing here and the part a reader
 * skips until the summary has told them what to look for.
 */
internal fun diagnosticReport(facts: DiagnosticFacts): String = buildString {
    appendLine(REPORT_HEADING)
    appendLine("App: ${facts.appLabel}")
    appendLine("Device: ${facts.deviceLabel}")
    appendLine("Kernel: ${facts.kernelLabel}")
    appendLine("Boot: ${facts.bootLabel}")
    appendLine("Root: ${facts.rootLabel}")
    appendLine("Shizuku: ${facts.shizukuLabel}")
    appendLine("Settings: ${facts.settingsLabel}")
    appendLine("Last run: ${facts.lastRunLabel}")
    appendLine("Shared temp dir: ${facts.residueLabel}")
    facts.preflightLabel?.let { preflight ->
        // The check is the part that says what a run would meet, so it goes before the log rather than after
        // it: it is a summary of the same facts the log contains, one line per thing.
        appendLine()
        append(preflight)
    }
    appendLine()
    appendLine(REPORT_LOG_HEADING)
    if (facts.logTail.isEmpty()) {
        appendLine("(nothing logged yet)")
    } else {
        facts.logTail.forEach { line -> appendLine(line) }
    }
}

/**
 * Where a report ends: the newest [limit] lines, with the count of what was left out.
 *
 * Truncated from the front rather than the back, and said out loud - a tail with no marker reads as the whole
 * log, and the whole log of a failed boot is not what anyone pastes into a thread.
 */
internal fun reportLogTail(
    lines: List<String>,
    limit: Int = REPORT_LOG_TAIL_LINES,
): List<String> {
    if (lines.size <= limit) return lines
    return listOf("(${lines.size - limit} earlier lines not shown)") + lines.takeLast(limit)
}

private const val REPORT_HEADING = "Root-My-Galaxy report"
private const val REPORT_LOG_HEADING = "App log (newest last):"

/**
 * The report for right now, as the text that goes on the clipboard.
 *
 * Reads the log file back first: the buffer in this process is not the log, and a report that left out what
 * the boot service wrote would omit exactly the run nobody was watching.
 */
internal suspend fun collectDiagnosticReport(context: Context, preflight: String? = null): String {
    withContext(Dispatchers.IO) { AppLog.reload() }
    return diagnosticReport(
        diagnosticFacts(
            context = context,
            preflight = preflight,
            logLines = AppLog.log.value.map(AppLogFormat::line),
        ),
    )
}

/**
 * Puts [report] on the clipboard, and says so.
 *
 * The toast is not decoration: the button is a tap that changes nothing on screen, and this app's clipboard
 * writes are otherwise only visible by pasting somewhere. Same fault as its own confirmation.
 */
internal fun copyReportToClipboard(context: Context, report: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("RootMyGalaxyReport", report))
    Toast.makeText(context, context.getString(R.string.report_copied), Toast.LENGTH_SHORT).show()
}

/**
 * The readings a report is made of.
 *
 * Blocking on purpose and off the main thread by the caller: reading KernelSU's version starts `su`, reading
 * the residue means stat'ing the shared temp directory, and neither belongs on a frame.
 */
internal suspend fun diagnosticFacts(
    context: Context,
    preflight: String? = null,
    logLines: List<String>,
): DiagnosticFacts = withContext(Dispatchers.IO) {
    val snapshot = DeviceSnapshot.current()
    val reading = KernelSuVersionProbe.read(context)
    val flavour = AppPreferences.kernelsuFlavor(context)
    val managers = ManagerPresence.of(KernelSuManager.installedManagers(context))
    val residue = runCatching { StagedResidue.read() }.getOrNull()

    DiagnosticFacts(
        appLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        deviceLabel = TargetGap.describe(snapshot),
        kernelLabel = snapshot.kernelVersionInfo,
        bootLabel = kernelBootToken() ?: "not readable",
        rootLabel = buildString {
            append(
                when (KernelSuRuntime.status()) {
                    KernelSuStatus.Active -> "KernelSU active"
                    KernelSuStatus.NotLoaded -> "KernelSU not loaded in this boot"
                    KernelSuStatus.Unreadable -> "KernelSU could not be read"
                },
            )
            append(", flavour ${flavour.label}")
            append(", manager ${managers.summary()}")
            val daemon = reading.daemon
            append(", daemon ${daemon ?: "unknown"}")
            val loaded = AppPreferences.loadedFlavor(context)
            if (loaded != null) append(", loaded in this kernel: ${loaded.label}")
        },
        shizukuLabel = buildString {
            append("use ${if (AppPreferences.shizukuMode(context)) "on" else "off"}")
            append(", ")
            append(
                when (ShizukuController.availability()) {
                    ShizukuAvailability.Ready -> "running and granted"
                    ShizukuAvailability.WithoutPermission -> "running, not granted"
                    ShizukuAvailability.NotRunning -> "not running"
                },
            )
            append(", wireless ADB ${if (AppPreferences.adbPaired(context)) "paired" else "not paired"}")
        },
        settingsLabel = buildString {
            append("payload mode ${AppPreferences.payloadMode(context).name.lowercase()}")
            append(", image guard ${if (AppPreferences.partitionReadOnlyMode(context)) "on" else "off"}")
            append(", root on boot ${if (AppPreferences.bootRootMode(context)) "on" else "off"}")
            append(", auto root settle ${AppPreferences.autoRootSettleSeconds(context)} s")
            append(", battery ${if (isBatteryUnrestricted(context)) "unrestricted" else "restricted"}")
        },
        lastRunLabel = lastRunLabel(InstallHistoryStore(context).load().firstOrNull()),
        residueLabel = residue?.logLine(context)
            ?: context.getString(R.string.preflight_staging_unknown),
        preflightLabel = preflight,
        logTail = reportLogTail(logLines),
    )
}

/**
 * The most recent run as one line.
 *
 * The stage and the reason, because that pair is the whole question: a run that failed during the exploit and
 * one that failed at the download are different problems, and "it did not work" is what a report without this
 * line reduces to.
 */
private fun lastRunLabel(entry: InstallHistoryEntry?): String {
    if (entry == null) return "no runs recorded yet"
    val when_ = AppLogFormat.stamp(entry.startedAtMillis)
    val result = entry.result.name.lowercase()
    val stage = entry.failureStage?.name?.lowercase() ?: "finished"
    val reason = entry.failureReason?.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()
    val payload = entry.profileId?.let { " - $it" }.orEmpty()
    return "$when_ $result at $stage$reason$payload"
}

/** Whether Android is still allowed to hold this app back, which an unattended run cannot afford. */
internal fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName)
        ?: true

/**
 * Which manager apps are on the phone.
 *
 * Both flavours rather than the configured one, because the two are different apps and one being installed
 * for the *other* flavour is exactly what an attempt to open "the manager" then fails to find.
 */
private fun ManagerPresence.summary(): String = when {
    kernelsu && kernelsuNext -> "KernelSU and KernelSU-Next both installed"
    kernelsu -> "KernelSU installed"
    kernelsuNext -> "KernelSU-Next installed"
    else -> "no manager installed"
}
