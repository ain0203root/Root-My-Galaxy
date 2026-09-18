package dev.busung.s25uroot

import android.content.Context
import androidx.annotation.StringRes

/**
 * How one thing a run depends on came out.
 *
 * Four states rather than two, because the useful answers are not pass and fail. A three-part kernel match is
 * neither: the entry is the right model and the right release line, the native layer is what decides, and the
 * person about to spend this boot's one attempt deserves to know which of those they are holding. And a
 * preference that changes what a run does - the partition guard, offline mode - is not a finding about the
 * device at all, which is what [Note] is for.
 */
internal enum class PreflightState(@StringRes val label: Int) {
    /** Checked, and nothing about it stands in the way. */
    Ready(R.string.preflight_state_ready),

    /** Not a finding about the device: a setting that changes what the run will do. */
    Note(R.string.preflight_state_note),

    /** Checked, and the exploit may still refuse - with the reason named. */
    Warn(R.string.preflight_state_warn),

    /** Checked, and the run cannot succeed as things stand. */
    Fail(R.string.preflight_state_fail),
}

/**
 * The things a pre-flight looks at, in the order they are worth reading.
 *
 * The order is the run's own: what it would install, whether this firmware is the one the entry was written
 * for, which KernelSU that entry brings, what would carry it, the payload itself, and then the three facts
 * about the device that decide whether an attempt can work at all. Each of them is already known somewhere
 * else in the app - the run checks every one of them as it goes - and the point of doing it here is that an
 * attempt is spent finding out, so the check is worth having *before* the one attempt a boot gets.
 */
internal enum class PreflightItemId(@StringRes val label: Int) {
    Target(R.string.preflight_target),
    Match(R.string.preflight_match),
    Flavor(R.string.preflight_flavor),
    Transport(R.string.preflight_transport),
    Payload(R.string.preflight_payload),
    Budget(R.string.preflight_budget),
    Partitions(R.string.preflight_partitions),
    Staging(R.string.preflight_staging),
}

/** One line of the check: what was looked at, how it came out, and what was found. */
internal data class PreflightItem(
    val id: PreflightItemId,
    val state: PreflightState,
    /** What was found, in the app's own words, already resolved. */
    val detail: String,
)

/**
 * What the check adds up to.
 *
 * Three answers, because there are three: a run that cannot work should not be started, a run that may be
 * refused is worth starting anyway - that is what the attempt is for - and everything else is a run with
 * nothing left to weigh. The verdict is derived from the items rather than set beside them, so a row and the
 * headline above it cannot come apart.
 */
internal enum class PreflightVerdict(
    @StringRes val headline: Int,
    @StringRes val body: Int,
) {
    Ready(R.string.preflight_verdict_ready, R.string.preflight_verdict_ready_body),
    MayBeRefused(R.string.preflight_verdict_maybe, R.string.preflight_verdict_maybe_body),
    Blocked(R.string.preflight_verdict_blocked, R.string.preflight_verdict_blocked_body),
}

/** The device and the entry the check was about, and every line it produced. */
internal data class PreflightReport(
    val deviceLabel: String,
    val targetLabel: String,
    val items: List<PreflightItem>,
) {
    val blockers: List<PreflightItem> get() = items.filter { it.state == PreflightState.Fail }

    val warnings: List<PreflightItem> get() = items.filter { it.state == PreflightState.Warn }

    val verdict: PreflightVerdict get() = preflightVerdict(items)
}

/**
 * What the findings add up to.
 *
 * A failure outranks a warning rather than being listed beside it: "may still be refused" over a run that
 * cannot start is a sentence someone would be right to act on and wrong to believe. Pure, so the ordering
 * can be checked without a device.
 */
internal fun preflightVerdict(items: List<PreflightItem>): PreflightVerdict = when {
    items.any { it.state == PreflightState.Fail } -> PreflightVerdict.Blocked
    items.any { it.state == PreflightState.Warn } -> PreflightVerdict.MayBeRefused
    else -> PreflightVerdict.Ready
}

/**
 * The check as one block of text, for pasting into a thread.
 *
 * Everything a reader needs to answer "why did it not work" without asking: what the phone is, which entry
 * the app would pick for it, and every line of the check with its state. The states are written in words
 * rather than as the screen's colours, because this text has no colours and "ready" against a line that is
 * not ready is what a report has to avoid.
 */
internal fun preflightText(context: Context, report: PreflightReport): String = buildString {
    appendLine("${context.getString(R.string.preflight_title)}: ${context.getString(report.verdict.headline)}")
    appendLine("${context.getString(R.string.preflight_device)}: ${report.deviceLabel}")
    appendLine("${context.getString(R.string.preflight_target)}: ${report.targetLabel}")
    report.items.forEach { item ->
        appendLine(
            "- ${context.getString(item.id.label)}: " +
                "${context.getString(item.state.label)} - ${item.detail}",
        )
    }
}
