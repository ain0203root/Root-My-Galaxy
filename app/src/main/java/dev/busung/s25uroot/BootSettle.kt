package dev.busung.s25uroot

import android.os.SystemClock
import kotlin.math.abs

/**
 * How long after a boot a run waits before the exploit starts.
 *
 * The wait is measured from the boot, not from the moment the run was asked for: a device that has
 * already been up longer than the requirement waits not at all, and one that was rebooted ten seconds
 * ago waits the rest. That is the point of the gate, because what it is protecting is the state of a
 * freshly booted device, and a race attempted while the system is still settling fails for reasons the
 * payload cannot fix.
 *
 * The default is not zero. The exploit this app runs has a racy stage that a cold device makes worse,
 * and the wait costs two minutes once per boot against a failed attempt that costs the whole run. It
 * is a floor rather than a hard block: [InstallViewModel.skipBootSettle] ends the wait on the user's
 * word, because someone who knows their device just booted cleanly is better informed than a constant.
 */
internal object BootSettle {
    /** What a run waits for unless it is told otherwise. */
    const val DEFAULT_SECONDS = 120

    /**
     * What the setting offers. Rounded to these rather than free-form: a value nobody tested is not a
     * better one, and a round number is what makes the choice reviewable.
     */
    val allowedSeconds = listOf(0, 30, 60, 90, 120, 180, 300, 600)

    /** The offered value nearest to [seconds], so a stored number is always one of them. */
    fun normalize(seconds: Int): Int =
        allowedSeconds.minByOrNull { abs(it - seconds) } ?: DEFAULT_SECONDS

    /**
     * Milliseconds still to wait, given the boot's elapsed time.
     *
     * Returns zero rather than a negative number once the boot is old enough, so a caller can test the
     * result rather than having to know which side of the subtraction it is on.
     */
    fun remainingMillis(requiredSeconds: Int, elapsedRealtimeMillis: Long): Long =
        (normalize(requiredSeconds) * 1_000L - elapsedRealtimeMillis).coerceAtLeast(0L)

    /** Time since boot, which is what the gate is measured against and what survives a deep sleep. */
    fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    /**
     * `1:42` for a countdown.
     *
     * Rounded up, so a wait never reads `0:00` while it is still waiting: the last second of a
     * countdown is a second, and showing zero during it would say the run had started when it had not.
     */
    fun formatRemaining(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** The setting's own label for a value, as the chooser and the run plan show it. */
    fun label(seconds: Int): String {
        val normalized = normalize(seconds)
        val minutes = normalized / 60
        val rest = normalized % 60
        return when {
            normalized == 0 -> "Off"
            minutes == 0 -> "$rest s"
            rest == 0 -> "$minutes min"
            else -> "$minutes min $rest s"
        }
    }
}
