package dev.busung.s25uroot

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent execution/decision recorder for the diagnostic application build.
 * It records observations made by the app and native payload log without
 * changing exploit/payload decisions.
 */
object ForensicTrace {
    private const val TAG = "RMG-FORENSIC"
    private val initialized = AtomicBoolean(false)
    @Volatile private var sessionId: String = "uninitialized"
    @Volatile private var logFile: File? = null
    @Volatile private var jsonlFile: File? = null

    @Synchronized
    fun init(context: Context, session: String? = null) {
        if (initialized.get()) return
        val root = File(context.filesDir, "forensic-trace").apply { mkdirs() }
        sessionId = session ?: "${System.currentTimeMillis()}-${Process.myPid()}"
        logFile = File(root, "$sessionId.log")
        jsonlFile = File(root, "$sessionId.jsonl")
        initialized.set(true)
        event(
            category = "TRACE",
            name = "session_start",
            fields = mapOf(
                "pid" to Process.myPid(),
                "session" to sessionId,
                "filesDir" to context.filesDir.absolutePath,
            ),
        )
    }

    fun event(category: String, name: String, message: String? = null, fields: Map<String, Any?> = emptyMap()) {
        if (!initialized.get()) return
        val nowWall = System.currentTimeMillis()
        val nowMono = SystemClock.elapsedRealtimeNanos()
        val tid = Process.myTid()
        val extras = fields.entries.filter { it.value != null }
            .joinToString(" ") { "${it.key}=${encodeText(it.value.toString())}" }
        val human = buildString {
            append(nowWall); append(" mono_ns="); append(nowMono)
            append(" pid="); append(Process.myPid()); append(" tid="); append(tid)
            append(" ["); append(category); append("] "); append(name)
            if (!message.isNullOrBlank()) { append(" :: "); append(encodeText(message)) }
            if (extras.isNotBlank()) { append(" :: "); append(extras) }
            append('\n')
        }
        val json = buildString {
            append("{\"wall_ms\":"); append(nowWall)
            append(",\"mono_ns\":"); append(nowMono)
            append(",\"pid\":"); append(Process.myPid())
            append(",\"tid\":"); append(tid)
            append(",\"session\":\""); append(jsonEscape(sessionId)); append("\"")
            append(",\"category\":\""); append(jsonEscape(category)); append("\"")
            append(",\"name\":\""); append(jsonEscape(name)); append("\"")
            if (!message.isNullOrBlank()) { append(",\"message\":\""); append(jsonEscape(message)); append("\"") }
            for ((key, value) in fields) {
                if (value == null) continue
                append(",\""); append(jsonEscape(key)); append("\":\"")
                append(jsonEscape(value.toString())); append("\"")
            }
            append("}\n")
        }
        runCatching {
            logFile?.appendText(human, Charsets.UTF_8)
            jsonlFile?.appendText(json, Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "forensic trace write failed", it) }
        Log.v(TAG, "${category}/${name}${message?.let { ": $it" } ?: ""}${if (extras.isNotBlank()) " $extras" else ""}")
    }

    fun raw(source: String, text: String) {
        if (!initialized.get() || text.isBlank()) return
        text.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, line ->
            event("RAW", "line", line, mapOf("source" to source, "line_index" to index))
            parseDecisionLine(source, line)
        }
    }

    fun command(transport: String, argv: List<String>, env: Map<String, String>? = null, cwd: String? = null) {
        event("PROCESS", "command_start", fields = mapOf(
            "transport" to transport,
            "argv" to argv.joinToString("\u001f"),
            "cwd" to cwd,
            "env" to env?.entries?.joinToString("\u001f") { "${it.key}=${it.value}" },
        ))
    }

    fun commandResult(transport: String, exitCode: Int?, stdout: String, stderr: String, durationMs: Long) {
        event("PROCESS", "command_result", fields = mapOf(
            "transport" to transport,
            "exit_code" to exitCode,
            "duration_ms" to durationMs,
            "stdout_bytes" to stdout.toByteArray(Charsets.UTF_8).size,
            "stderr_bytes" to stderr.toByteArray(Charsets.UTF_8).size,
        ))
        raw("$transport.stdout", stdout)
        raw("$transport.stderr", stderr)
    }

    fun decision(name: String, actual: Any?, expected: Any?, passed: Boolean, reason: String? = null) {
        event("DECISION", name, fields = mapOf(
            "actual" to actual,
            "expected" to expected,
            "result" to if (passed) "PASS" else "REJECT",
            "reason" to reason,
        ))
    }

    private fun parseDecisionLine(source: String, line: String) {
        val lower = line.lowercase(Locale.US)
        val isDecision = lower.contains("rejected") || lower.contains("reject") ||
            lower.contains("accepted") || lower.contains("success") || lower.contains("failed") ||
            lower.contains("miss") || lower.contains("timeout") || lower.contains("candidate")
        if (!isDecision) return
        val pairs = Regex("""([A-Za-z_][A-Za-z0-9_-]*)=([^\s]+)""")
            .findAll(line).take(32).associate { it.groupValues[1] to it.groupValues[2] }
        event("DECISION", "observed", line, mapOf(
            "source" to source,
            "decision_kind" to when {
                lower.contains("reject") || lower.contains("miss") -> "REJECT"
                lower.contains("timeout") -> "TIMEOUT"
                lower.contains("success") || lower.contains("accepted") -> "ACCEPT/PROGRESS"
                lower.contains("candidate") -> "CANDIDATE"
                else -> "OBSERVED"
            },
            "kv" to pairs.entries.joinToString("\u001f") { "${it.key}=${it.value}" },
        ))
    }

    fun close() { if (initialized.get()) event("TRACE", "session_end") }
    fun currentLogFile(): File? = logFile
    fun currentJsonlFile(): File? = jsonlFile

    private fun encodeText(value: String) = value.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    private fun jsonEscape(value: String): String = buildString {
        for (c in value) when (c) {
            '\\' -> append("\\\\"); '"' -> append("\\\"")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
