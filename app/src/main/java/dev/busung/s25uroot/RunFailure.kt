package dev.busung.s25uroot

import androidx.annotation.StringRes

/**
 * Where a run was when it stopped.
 *
 * A message on its own rarely answers "what now?": the same wording can come out of a download,
 * the exploit, or the KernelSU load, and the stage is what tells them apart. It is recorded with
 * every failure, so a run that ended an hour ago still says where it ended.
 */
enum class RunStage(@StringRes val label: Int) {
    Transport(R.string.stage_transport),
    Target(R.string.stage_target),
    Download(R.string.stage_download),
    Exploit(R.string.stage_exploit),
    KernelSu(R.string.stage_kernel_su),
    Verify(R.string.stage_verify),
}

/**
 * A run that stopped: the stage it stopped in, the reason the app reports, and the tail of what the
 * payload or helper last said. For an exploit failure the last part is usually the only evidence
 * that says *which* part of the payload gave up, since the payload's own lines are the only account
 * of the kernel race.
 */
data class RunFailure(
    val stage: RunStage,
    val reason: String,
    val evidence: List<String> = emptyList(),
)

/**
 * The meaningful tail of a run log. Blank lines are dropped and long lines are clipped, because
 * payload output is printed to a narrow monospace view and an unclipped line there pushes the rest
 * out of sight.
 */
internal fun failureEvidence(log: String, maxLines: Int = 4, maxLength: Int = 160): List<String> =
    log.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
        .takeLast(maxLines)
        .map { line -> if (line.length <= maxLength) line else line.take(maxLength - 1) + "\u2026" }
