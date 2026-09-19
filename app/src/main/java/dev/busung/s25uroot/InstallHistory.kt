package dev.busung.s25uroot

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class InstallRunResult {
    Running,
    Succeeded,

    /**
     * The exploit got root and the run was told not to load KernelSU, so root was all it produced.
     *
     * Its own result rather than Succeeded, because the two mean different things to whoever reads the
     * history later: one says the device is rooted with KernelSU, the other says the payload worked and
     * nothing was loaded on top of it.
     */
    RootOnly,
    Failed,

    /**
     * The user stopped the run before it finished.
     *
     * Not [Failed]: nothing about the run went wrong, and whoever reads the history later is asking
     * which runs did not complete rather than which ones broke. The stage it was stopped in is recorded
     * with it, because that is the part with consequences - a payload stopped mid-exploit may still be
     * running on the device.
     */
    Stopped,
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

/**
 * The entries a launch closes, given the run that is still in flight.
 *
 * Pure, so the rule can be checked without a device or a filesystem - and the rule is worth checking, because
 * getting it wrong in one direction leaves a list claiming an install that is not happening and in the other
 * marks a run that is going perfectly well as failed.
 *
 * [holder] is the record two processes share. An entry still marked [InstallRunResult.Running] is a run that
 * died with its process - unless it is the entry that record names, which is the run that is still writing it.
 */
internal fun List<InstallHistoryEntry>.closingInterruptedRuns(
    holder: RunHolder?,
): List<InstallHistoryEntry> = map { entry ->
    if (entry.result == InstallRunResult.Running && entry.id != holder?.entryId) {
        entry.copy(
            completedAtMillis = System.currentTimeMillis(),
            result = InstallRunResult.Failed,
        )
    } else {
        entry
    }
}

class InstallHistoryStore(private val context: Context) {
    private val directory = File(context.filesDir, "install-history").apply { mkdirs() }

    fun load(): List<InstallHistoryEntry> = directory
        .listFiles { file -> file.extension == "json" }
        .orEmpty()
        .mapNotNull(::decodeOrQuarantine)
        .sortedByDescending(InstallHistoryEntry::startedAtMillis)

    /**
     * Closes the runs that were interrupted, and leaves the one that is still going alone.
     *
     * An entry still marked [InstallRunResult.Running] when nothing is running is a run that died with its
     * process: closing it is what keeps the list from claiming an install that is not happening.
     *
     * [holder] is what keeps that from being wrong in the other direction. A run can be started by this
     * app's other process - the boot gate installs from `:autoroot_gate` - so opening the app while one is
     * in flight used to close *that* run's entry and mark it failed, on a run that was still going and
     * about to write its own result. The record names the entry its owner is writing, so the entry a live
     * run owns is the one entry this must not touch; when the record names no entry, nothing is spared and
     * every unfinished one is closed as before.
     */
    internal fun closeInterruptedRuns(holder: RunHolder? = null): List<InstallHistoryEntry> {
        val stored = load()
        val closed = stored.closingInterruptedRuns(holder)
        closed.forEachIndexed { index, entry -> if (entry != stored[index]) save(entry) }
        return closed
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
