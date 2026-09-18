package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The two things the run's notification can do.
 *
 * Both are broadcasts rather than service calls, and both are deliberately tiny: this runs in the app's own
 * process with a time budget the framework decides, so nothing here hashes, walks a directory or downloads.
 * The stop is a record the run reads at its next tick; the copy is a file read of a log the run is already
 * writing.
 */
class RunActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                // Written before anything is said, so the run stops even if the line below cannot be
                // delivered - and named for this process, which is the one whose run posted this
                // notification.
                RunStopSignal.request(context)
                AppLog.warn(AppLogTags.RUN, "Stop requested from the run notification")
                RunNotification.note(context, context.getString(R.string.run_notification_stopping))
            }
            ACTION_COPY_LOG -> {
                val log = activeRunLog(context)
                copyLogToClipboard(context, log)
                AppLog.info(
                    AppLogTags.RUN,
                    "Run log copied from the notification (${log.length} characters)",
                )
                RunNotification.note(context, context.getString(R.string.run_notification_log_copied))
            }
        }
    }

    companion object {
        const val ACTION_STOP = "dev.busung.s25uroot.action.STOP_RUN"
        const val ACTION_COPY_LOG = "dev.busung.s25uroot.action.COPY_RUN_LOG"

        /**
         * What "the log" means here: the run in flight, read from the history entry it is writing.
         *
         * The history store is the only copy of a run's output that another part of the app can reach - the
         * view model that holds it in memory is not something a receiver can be handed - and it is written as
         * the run goes, so it is the current log rather than the last one. The app log is the fallback for a
         * notification that outlived its run's history entry, which is the case where the newest entry is
         * still the most useful thing there is.
         */
        internal fun activeRunLog(context: Context): String {
            val entries = runCatching { InstallHistoryStore(context).load() }.getOrDefault(emptyList())
            val running = entries.firstOrNull { it.result == InstallRunResult.Running }
            val newest = running ?: entries.firstOrNull()
            val log = newest?.log.orEmpty()
            if (log.isNotBlank()) return log
            return AppLog.asText(AppLog.log.value).ifBlank { context.getString(R.string.logs_empty_title) }
        }
    }
}
