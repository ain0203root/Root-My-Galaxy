package dev.busung.s25uroot

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import java.io.File

/**
 * What this phone can be asked about a payload, read entirely inside the app.
 *
 * A device with no payload in the catalog has no run to fail, so nothing about the app's normal paths
 * tells anyone whether one could be built. The porting procedure answers that on a workstation, from
 * the firmware image, with `vmlinux-to-elf`, `llvm-nm`, `bpftool` and the target's raw kernel - and
 * none of that can happen on a phone. What *can* happen on a phone is the half of it that is about the
 * device itself: every check here is a fact a port has to be built against, and every one of them is
 * readable by an ordinary app with no root, no shell and no network.
 *
 * That boundary is the point, and it is drawn per check rather than stated once. A reading that needs
 * a shell to be made says so instead of reading as a failure, because the two are different answers:
 * one says this device is a port, the other says this device is a port and nobody has looked at this
 * part of it yet.
 *
 * Nothing here decides whether a payload would work - it cannot, and a screen that implied it could
 * would be worse than no screen. What it decides is which of the things a port must supply are already
 * answered by the phone, and it produces the identity block a porter needs to start from that phone's
 * own firmware rather than from a stranger's.
 */
internal enum class CheckOutcome {
    /** Answered the way every published target's header assumes. */
    AsExpected,

    /** Answered, and differently: a port has to derive its own values for this. */
    NeedsPorting,

    /** Could not be read from this app. Not a finding about the device. */
    Unreadable,
}

/**
 * One thing a port depends on, and how this device answered it.
 *
 * [reading] is the device's own answer - a number, a state, or the name of what was found - and it is
 * kept verbatim rather than summarised into the outcome, because it is what a porter copies. The
 * outcome is what a person reads; the reading is what gets pasted.
 */
internal data class DeviceCheck(
    val kind: DeviceCheckKind,
    val reading: String,
    val outcome: CheckOutcome,
)

/**
 * The checks, each one a value a target header or a KernelSU module is built from.
 *
 * Every entry names what it *decides*, because a list of readings with no consequence is a wall of
 * prose: the titles say what was looked at and [decides] says what a port does differently when the
 * answer is not the expected one.
 */
internal enum class DeviceCheckKind(
    @StringRes val title: Int,
    @StringRes val decides: Int,
    /** Stable name for the log and the copied block, never shown on the screen. */
    val tag: String,
) {
    Architecture(
        R.string.device_check_arch,
        R.string.device_check_arch_decides,
        "arch",
    ),
    PageSize(
        R.string.device_check_page_size,
        R.string.device_check_page_size_decides,
        "page_size",
    ),
    Symbols(
        R.string.device_check_symbols,
        R.string.device_check_symbols_decides,
        "symbols",
    ),
    TracefsEvent(
        R.string.device_check_tracefs,
        R.string.device_check_tracefs_decides,
        "tracefs_event",
    ),
    NetfilterLogger(
        R.string.device_check_logger,
        R.string.device_check_logger_decides,
        "netfilter_logger",
    ),
    Ashmem(
        R.string.device_check_ashmem,
        R.string.device_check_ashmem_decides,
        "ashmem",
    ),
    BootIdOracle(
        R.string.device_check_boot_id,
        R.string.device_check_boot_id_decides,
        "boot_id",
    ),
    Selinux(
        R.string.device_check_selinux,
        R.string.device_check_selinux_decides,
        "selinux",
    ),
    AddressVisibility(
        R.string.device_check_visibility,
        R.string.device_check_visibility_decides,
        "address_visibility",
    ),
    KernelInterface(
        R.string.device_check_kmi,
        R.string.device_check_kmi_decides,
        "kmi",
    ),
}

/** The tracefs file whose contents are the event id a header records. */
internal const val TRACEFS_EVENT_PATH =
    "/sys/kernel/tracing/events/sched/sched_blocked_reason/id"

/** Where the registered netfilter loggers are listed, which is where the slide oracle lives. */
internal const val NF_LOGGERS_PATH = "/proc/net/netfilter/nf_log"

/** The oracle's own path, read through the same property the app reads for its boot token. */
internal const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"

internal const val SELINUX_ENFORCE_PATH = "/sys/fs/selinux/enforce"

internal const val KALLSYMS_PATH = "/proc/kallsyms"

internal const val ASHMEM_PATH = "/dev/ashmem"

internal const val KPTR_RESTRICT_PATH = "/proc/sys/kernel/kptr_restrict"

internal const val DMESG_RESTRICT_PATH = "/proc/sys/kernel/dmesg_restrict"

/**
 * How much of a file is read.
 *
 * `/proc/kallsyms` is several megabytes and answers the only question asked of it - whether it is
 * populated at all - in its first line, so this is a head and not a read. Sixteen kilobytes is far
 * more than any of the other files here have.
 */
internal const val CHECK_READ_LIMIT_BYTES = 16 * 1024

/**
 * The event id in the tracefs file, which holds a number and nothing else.
 *
 * A device whose tracefs is not readable by an app answers with an empty string rather than an error -
 * reading a directory that a shell owns gives nothing - so blank is no reading and not a zero.
 */
internal fun parseTraceEventId(text: String): Int? =
    text.trim().lineSequence().firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it > 0 }

/**
 * Whether the file lists a line for `nfnetlink_log`.
 *
 * The oracle is that logger's `name` pointer, so a kernel that does not register it - built without
 * `CONFIG_NETFILTER_NETLINK_LOG`, or with it as a module that was never loaded - has no oracle at all
 * and no slide leak through it. The whole listing is returned to the screen rather than a yes or no,
 * because which loggers *are* there is what a porter needs to decide what a port can use instead.
 */
internal fun nfnetlinkLoggerRegistered(text: String): Boolean =
    text.lineSequence().any { it.contains("nfnetlink_log") }

/** `1` enforcing, `0` permissive, and nothing else is a reading. */
internal fun parseSelinuxEnforcing(text: String): Boolean? = when (text.trim().firstOrNull()) {
    '1' -> true
    '0' -> false
    else -> null
}

/** One of the integer sysctls that decide what a rooted shell may read. */
internal fun parseSysctlInt(text: String): Int? = text.trim().toIntOrNull()

/**
 * Whether `/proc/kallsyms` has anything in it.
 *
 * The kernel's symbol table is what KernelSU's late loader relocates its undefined symbols from, which
 * is why a kernel without `CONFIG_KALLSYMS` cannot carry the module at all. Addresses are zeroed for
 * anyone but root, which is not what this asks about: the names are the part that matters, and they
 * are present for a reader who cannot see the addresses.
 */
internal fun kallsymsHasSymbols(text: String): Boolean =
    text.lineSequence().any { line -> line.isNotBlank() && line.contains(' ') }

/**
 * The KMI a port has to be built against, from the kernel release.
 *
 * Android's kernels carry it in the release itself - `6.1.157-android14-11` is `android14-6.1` - and a
 * port and its KernelSU module both claim it, the module as its `vermagic`. Null when the release
 * carries no `androidNN` token, which is a kernel no published target has been built for.
 */
internal fun kmiOf(kernelRelease: String): String? {
    val android = Regex("""android(\d+)""").find(kernelRelease)?.groupValues?.get(1) ?: return null
    val parts = kernelRelease.split('.').take(2)
    if (parts.size < 2) return null
    return "android$android-${parts[0]}.${parts[1]}"
}

/**
 * Whether the page size is the one every target header's numbers assume.
 *
 * The P0 fingerprint table is generated over page offsets inside a 4 KiB page, and the fake-object
 * offsets in a target header are page-sized strides apart, so a 16 KiB device needs its own table and
 * its own offsets rather than a scaled one.
 */
internal fun pageSizeAsExpected(pageSize: Long): Boolean = pageSize == 4096L

/** True, false or nothing read, in the three outcomes the screen speaks. */
internal fun outcomeOf(reading: Boolean?): CheckOutcome = when (reading) {
    true -> CheckOutcome.AsExpected
    false -> CheckOutcome.NeedsPorting
    null -> CheckOutcome.Unreadable
}

/**
 * The portability report: every check, in a fixed order, with the readings that produced them.
 *
 * The checks are ordered by what a person can act on rather than by how they are implemented: the
 * device's architecture and page size are facts about a port's existence, the kernel interfaces after
 * them are facts about which route it can take, and the KMI last, because it is the one that says
 * which released module would even load.
 */
internal data class PayloadPortability(
    val checks: List<DeviceCheck>,
) {
    /** Whether any check found something a port has to derive for itself. */
    val needsPorting: Boolean
        get() = checks.any { it.outcome == CheckOutcome.NeedsPorting }

    /** The readings that could not be made here, which is what a shell would settle. */
    val unreadable: List<DeviceCheck>
        get() = checks.filter { it.outcome == CheckOutcome.Unreadable }

    fun readingOf(kind: DeviceCheckKind): String? =
        checks.firstOrNull { it.kind == kind }?.reading
}

/**
 * Reads every check from the device.
 *
 * Files read here are all world-readable on a stock Android kernel, and the ones that are not - tracefs
 * behind a root-owned directory, `/proc/net` under a policy that denies it to app domains - come back
 * as [CheckOutcome.Unreadable] rather than as a device without that kernel feature. That distinction
 * is the whole reason this is a report and not a verdict: a port is not refused for a reading the app
 * could not make.
 */
internal object PayloadPortabilityProbe {

    fun read(context: Context, snapshot: DeviceSnapshot): PayloadPortability {
        val kallsyms = readHead(KALLSYMS_PATH, CHECK_READ_LIMIT_BYTES)
        val loggers = readHead(NF_LOGGERS_PATH, CHECK_READ_LIMIT_BYTES)
        val traceEvent = readHead(TRACEFS_EVENT_PATH, 64)
        val enforce = readHead(SELINUX_ENFORCE_PATH, 8)
        val kptr = readHead(KPTR_RESTRICT_PATH, 8)
        val dmesg = readHead(DMESG_RESTRICT_PATH, 8)
        val bootId = kernelBootToken()
        val ashmem = File(ASHMEM_PATH).exists()
        val kmi = kmiOf(snapshot.kernelRelease)

        val checks = listOf(
            DeviceCheck(
                kind = DeviceCheckKind.Architecture,
                reading = snapshot.machine,
                outcome = if (snapshot.machine == "aarch64") {
                    CheckOutcome.AsExpected
                } else {
                    CheckOutcome.NeedsPorting
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.PageSize,
                reading = "${snapshot.pageSize} bytes",
                outcome = if (pageSizeAsExpected(snapshot.pageSize)) {
                    CheckOutcome.AsExpected
                } else {
                    CheckOutcome.NeedsPorting
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.Symbols,
                reading = when {
                    kallsyms == null -> context.getString(R.string.device_check_reading_unreadable)
                    kallsymsHasSymbols(kallsyms) ->
                        context.getString(R.string.device_check_reading_symbols_present)
                    else -> context.getString(R.string.device_check_reading_symbols_empty)
                },
                outcome = when {
                    kallsyms == null -> CheckOutcome.Unreadable
                    else -> outcomeOf(kallsymsHasSymbols(kallsyms))
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.TracefsEvent,
                reading = when (traceEvent) {
                    null -> context.getString(R.string.device_check_reading_unreadable)
                    else -> parseTraceEventId(traceEvent)?.toString()
                        ?: context.getString(R.string.device_check_reading_not_an_id)
                },
                outcome = when {
                    traceEvent == null -> CheckOutcome.Unreadable
                    parseTraceEventId(traceEvent) == null -> CheckOutcome.Unreadable
                    else -> CheckOutcome.AsExpected
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.NetfilterLogger,
                reading = when {
                    loggers == null -> context.getString(R.string.device_check_reading_unreadable)
                    nfnetlinkLoggerRegistered(loggers) -> loggers
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                        .take(200)
                    else -> loggers.lineSequence()
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                        .take(200)
                        .ifEmpty { context.getString(R.string.device_check_reading_none_registered) }
                },
                outcome = when (loggers) {
                    null -> CheckOutcome.Unreadable
                    else -> outcomeOf(nfnetlinkLoggerRegistered(loggers))
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.Ashmem,
                reading = if (ashmem) {
                    ASHMEM_PATH
                } else {
                    context.getString(R.string.device_check_reading_absent)
                },
                outcome = outcomeOf(ashmem),
            ),
            DeviceCheck(
                kind = DeviceCheckKind.BootIdOracle,
                reading = if (bootId != null) {
                    context.getString(R.string.device_check_reading_readable)
                } else {
                    context.getString(R.string.device_check_reading_unreadable)
                },
                outcome = outcomeOf(bootId != null),
            ),
            DeviceCheck(
                kind = DeviceCheckKind.Selinux,
                reading = when (enforce) {
                    null -> context.getString(R.string.device_check_reading_unreadable)
                    else -> when (parseSelinuxEnforcing(enforce)) {
                        true -> context.getString(R.string.device_check_reading_enforcing)
                        false -> context.getString(R.string.device_check_reading_permissive)
                        null -> context.getString(R.string.device_check_reading_unreadable)
                    }
                },
                outcome = when (enforce) {
                    null -> CheckOutcome.Unreadable
                    else -> outcomeOf(parseSelinuxEnforcing(enforce))
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.AddressVisibility,
                reading = listOfNotNull(
                    parseSysctlInt(kptr ?: "")?.let { "kptr_restrict=$it" },
                    parseSysctlInt(dmesg ?: "")?.let { "dmesg_restrict=$it" },
                ).joinToString(", ").ifEmpty {
                    context.getString(R.string.device_check_reading_unreadable)
                },
                outcome = when {
                    kptr == null && dmesg == null -> CheckOutcome.Unreadable
                    // Neither value is a mismatch with anything: they say what a *rooted* shell will be
                    // able to read, which is how the module's late load finds its symbols.
                    else -> CheckOutcome.AsExpected
                },
            ),
            DeviceCheck(
                kind = DeviceCheckKind.KernelInterface,
                reading = kmi ?: context.getString(R.string.device_check_reading_no_kmi),
                outcome = outcomeOf(kmi != null),
            ),
        )
        val portability = PayloadPortability(checks)
        AppLog.info(
            AppLogTags.KERNEL_SU,
            "Read this device for a port: " +
                checks.joinToString(", ") { "${it.kind.tag}=${it.reading}" },
        )
        return portability
    }

    /**
     * The head of a file, or null when it could not be opened at all.
     *
     * Read as a stream with a cap rather than through `File.readText`, because the largest file here is
     * `/proc/kallsyms`: it reports a length of zero like every proc file and is megabytes when read
     * whole, so a reader that trusted either the length or the file would either read nothing or read
     * all of it.
     */
    private fun readHead(path: String, maxBytes: Int): String? = runCatching {
        File(path).inputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buffer, total, maxBytes - total)
                if (read <= 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
    }.getOrNull()
}

/**
 * What the enabled sources say about this device, or why they could not be asked at all.
 *
 * The three answers are kept apart because they mean different things to the person reading them: a
 * catalog that covers the device needs no port, a catalog that does not is the case this screen exists
 * for, and sources that could not be read are a network or a rate limit and say nothing about the
 * device. Folding the last one into "no payload for you" is what would make a phone with a perfectly
 * good payload look unported.
 */
internal data class CatalogVerdict(
    /** The payload a run would pick, when one does. */
    val profileId: String? = null,
    /** How many entries list this device's model and kernel. */
    val matches: Int = 0,
    /** The entries nearest to covering it, when none does. */
    val closest: List<TargetGap.Closest> = emptyList(),
    /** Why the sources could not be read, when they could not. */
    val failure: String? = null,
)

/**
 * Reads what the enabled sources cover, through the same selection a run would use.
 *
 * Blocking and network-touching, so it is called off the main thread. [resolveFor] is deliberately the
 * repository's own rule rather than a second matcher here: a screen that claimed coverage a run would
 * then refuse is worse than no screen at all.
 */
internal fun readCatalogVerdict(context: Context, snapshot: DeviceSnapshot): CatalogVerdict =
    runCatching {
        val catalog = PayloadRepository(context).loadCatalog().targets
        val profile = catalog.resolveFor(snapshot, AppPreferences.kernelsuFlavor(context))
        CatalogVerdict(
            profileId = profile?.profileId,
            matches = catalog.count { it.matches(snapshot) },
            closest = TargetGap.closest(snapshot, catalog),
        )
    }.getOrElse { error ->
        CatalogVerdict(failure = error.message ?: error.javaClass.simpleName)
    }

/**
 * The catalog as one line of the copied block.
 *
 * Plain English and not a resource, like every other label in [portRequestText]: whoever reads this
 * holds the catalog, and the line's job is to say which of the three answers it is without needing the
 * app in front of them.
 */
internal fun catalogSummaryLine(verdict: CatalogVerdict): String = when {
    verdict.failure != null -> "sources could not be read (${verdict.failure})"
    verdict.profileId != null -> "an enabled source covers this device: ${verdict.profileId}"
    verdict.closest.isEmpty() -> "no enabled source covers this model or kernel"
    else -> "no enabled source covers this device; closest: " + verdict.closest.joinToString("; ") {
        "${it.displayName} (kernels ${it.kernels})"
    }
}

/**
 * The block a porter asks for, as one pasteable piece of text.
 *
 * Deliberately not translated, and the labels are plain ASCII: this is a bug-report template whose
 * whole purpose is to be read by whoever holds the firmware, and a block whose labels changed with the
 * phone's language would be harder to answer, not friendlier. The identity half is what a port starts
 * from - a target is built against one firmware, and the device's own is the only one that matters -
 * and the readings half is what saves the porter from asking for them one at a time.
 */
internal fun portRequestText(
    snapshot: DeviceSnapshot,
    portability: PayloadPortability,
    catalog: CatalogVerdict,
): String = buildString {
    appendLine("RootMyGalaxy device check (app ${BuildConfig.BUILD_LABEL})")
    appendLine("model: ${snapshot.model}")
    appendLine("codename: ${snapshot.device}")
    appendLine("manufacturer: ${snapshot.manufacturer}")
    appendLine("firmware: ${snapshot.buildId}")
    appendLine("fingerprint: ${snapshot.fingerprint}")
    appendLine("kernel release: ${snapshot.kernelRelease}")
    appendLine("kernel version: ${snapshot.kernelVersionInfo}")
    appendLine("kmi: ${portability.readingOf(DeviceCheckKind.KernelInterface) ?: "unknown"}")
    appendLine("machine: ${snapshot.machine}")
    appendLine("page size: ${snapshot.pageSize}")
    appendLine("android: ${snapshot.androidRelease} (sdk ${snapshot.sdk})")
    appendLine("abi: ${snapshot.abi}")
    appendLine("security patch: ${Build.VERSION.SECURITY_PATCH}")
    appendLine("soc: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
    appendLine("catalog: ${catalogSummaryLine(catalog)}")
    appendLine("readings:")
    portability.checks.forEach { check ->
        appendLine("  ${check.kind.tag}: ${check.reading.replace('\n', ' ')}")
    }
}
