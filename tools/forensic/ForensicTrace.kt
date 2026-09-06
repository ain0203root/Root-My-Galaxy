package dev.busung.s25uroot

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Persistent forensic recorder used by the diagnostic build only. */
class ForensicTrace(private val context: Context) {
    private val seq = AtomicLong(0)
    private val session = UUID.randomUUID().toString()
    private val dir = File(context.filesDir, "forensic-trace").apply { mkdirs() }
    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private val logFile = File(dir, "rmg-$stamp-$session.log")
    private val jsonlFile = File(dir, "rmg-$stamp-$session.jsonl")

    @Synchronized
    fun event(category: String, name: String, message: String = "", fields: Map<String, Any?> = emptyMap()) {
        val n = seq.incrementAndGet()
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtimeNanos()
        val tid = Thread.currentThread().id
        val prefix = "#$n wall=$wall mono_ns=$mono pid=${android.os.Process.myPid()} tid=$tid session=$session category=$category event=$name"
        val fieldText = fields.entries.joinToString(" ") { "${it.key}=${clean(it.value?.toString() ?: "null")}" }
        val line = listOf(prefix, message.takeIf(String::isNotBlank), fieldText.takeIf(String::isNotBlank))
            .filterNotNull().filter(String::isNotBlank).joinToString(" ")
        logFile.appendText(line + "\n")
        jsonlFile.appendText(buildJson(n, wall, mono, tid, category, name, message, fields) + "\n")
    }

    fun raw(text: String) {
        if (text.isBlank()) return
        event("native", "raw", fields = mapOf("length" to text.length, "sha256" to text.hashCode()))
        logFile.appendText(text.trimEnd() + "\n")
    }

    fun decision(line: String) {
        val lower = line.lowercase(Locale.US)
        if (listOf("reject", "accepted", "candidate", "timeout", "success", "failed", "miss").any(lower::contains)) {
            event("decision", "observed", line)
        }
    }

    fun fileForHuman(): File = logFile
    fun fileForJsonl(): File = jsonlFile

    private fun buildJson(
        n: Long, wall: Long, mono: Long, tid: Long,
        category: String, name: String, message: String,
        fields: Map<String, Any?>,
    ): String {
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        val extras = fields.entries.joinToString(",") { q(it.key) + ":" + jsonValue(it.value) }
        return "{" + listOf(
            "\"seq\":$n",
            "\"wall_ms\":$wall",
            "\"mono_ns\":$mono",
            "\"pid\":${android.os.Process.myPid()}",
            "\"tid\":$tid",
            "\"build\":" + q(Build.FINGERPRINT),
            "\"category\":" + q(category),
            "\"event\":" + q(name),
            "\"message\":" + q(message),
            extras.takeIf(String::isNotBlank) ?: "",
        ).filter(String::isNotBlank).joinToString(",") + "}"
    }

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun clean(v: String): String = v.replace("\n", "\\n").replace("\r", "\\r").replace(" ", "_")
}
