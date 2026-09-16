package dev.busung.s25uroot

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class InstallRunResult {
    Running,
    Succeeded,
    Failed,
}

data class InstallHistoryEntry(
    val id: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val result: InstallRunResult,
    val log: String,
    val profileId: String? = null,
    /**
     * The catalog a run took its payload from, and the commit it was read at. Recorded because the
     * same payload id can be offered by several sources: without this a result can say which
     * payload ran but not which catalog defined it.
     */
    val sourceId: String? = null,
    val sourceLabel: String? = null,
    val sourceCommit: String? = null,
    val usedShizuku: Boolean = false,
    /** Where a failed run stopped, so the detail screen can say more than "Failed". */
    val failureStage: RunStage? = null,
    val failureReason: String? = null,
)

class InstallHistoryStore(private val context: Context) {
    private val directory = File(context.filesDir, "install-history").apply { mkdirs() }

    fun load(): List<InstallHistoryEntry> = directory
        .listFiles { file -> file.extension == "json" }
        .orEmpty()
        .mapNotNull(::decodeOrQuarantine)
        .sortedByDescending(InstallHistoryEntry::startedAtMillis)

    fun closeInterruptedRuns(): List<InstallHistoryEntry> = load().map { entry ->
        if (entry.result == InstallRunResult.Running) {
            entry.copy(
                completedAtMillis = System.currentTimeMillis(),
                result = InstallRunResult.Failed,
            ).also(::save)
        } else {
            entry
        }
    }

    fun create(): InstallHistoryEntry = InstallHistoryEntry(
        id = UUID.randomUUID().toString(),
        startedAtMillis = System.currentTimeMillis(),
        completedAtMillis = null,
        result = InstallRunResult.Running,
        log = "",
        usedShizuku = AppPreferences.shizukuMode(context),
    ).also(::save)

    fun save(entry: InstallHistoryEntry) {
        val target = File(directory, "${entry.id}.json")
        val atomicFile = AtomicFile(target)
        val output = atomicFile.startWrite()
        try {
            output.write(encode(entry).toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun delete(id: String) {
        File(directory, "$id.json").delete()
    }

    private fun encode(entry: InstallHistoryEntry) = JSONObject()
        .put("id", entry.id)
        .put("startedAtMillis", entry.startedAtMillis)
        .put("completedAtMillis", entry.completedAtMillis ?: JSONObject.NULL)
        .put("result", entry.result.name)
        .put("log", entry.log)
        .put("profileId", entry.profileId ?: JSONObject.NULL)
        .put("sourceId", entry.sourceId ?: JSONObject.NULL)
        .put("sourceLabel", entry.sourceLabel ?: JSONObject.NULL)
        .put("sourceCommit", entry.sourceCommit ?: JSONObject.NULL)
        .put("usedShizuku", entry.usedShizuku)
        .put("failureStage", entry.failureStage?.name ?: JSONObject.NULL)
        .put("failureReason", entry.failureReason ?: JSONObject.NULL)

    private fun decodeOrQuarantine(file: File): InstallHistoryEntry? = try {
        decode(AtomicFile(file).openRead().use { it.readBytes() })
    } catch (_: Throwable) {
        val quarantined = File(directory, "${file.name}.corrupt")
        quarantined.delete()
        file.renameTo(quarantined)
        null
    }

    private fun decode(bytes: ByteArray): InstallHistoryEntry {
        val value = JSONObject(bytes.toString(Charsets.UTF_8))
        return InstallHistoryEntry(
            id = value.getString("id"),
            startedAtMillis = value.getLong("startedAtMillis"),
            completedAtMillis = if (value.isNull("completedAtMillis")) {
                null
            } else {
                value.getLong("completedAtMillis")
            },
            result = InstallRunResult.valueOf(value.getString("result")),
            log = value.getString("log"),
            profileId = value.optionalString("profileId"),
            sourceId = value.optionalString("sourceId"),
            sourceLabel = value.optionalString("sourceLabel"),
            sourceCommit = value.optionalString("sourceCommit"),
            usedShizuku = value.optBoolean("usedShizuku", false),
            failureStage = value.optionalString("failureStage")
                ?.let { name -> RunStage.entries.firstOrNull { it.name == name } },
            failureReason = value.optionalString("failureReason"),
        )
    }

    /**
     * A key that this store writes as JSON null when it has no value. `optString` would hand back
     * the text "null" for those, which is a value the rest of the app would then try to display.
     */
    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
}
