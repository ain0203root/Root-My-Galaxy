package dev.busung.s25uroot

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import org.json.JSONArray
import org.json.JSONObject

enum class AccentColor(val storedValue: String) {
    Dynamic("dynamic"),
    Blue("blue"),
    Violet("violet"),
    Green("green"),
    Orange("orange");

    companion object {
        fun fromStoredValue(value: String?): AccentColor =
            entries.firstOrNull { it.storedValue == value } ?: Dynamic
    }
}

enum class AppThemeMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

object AppPreferences {
    private const val PREFERENCES = "appearance"
    private const val ACCENT_COLOR = "accent_color"
    private const val THEME_MODE = "theme_mode"
    private const val ADVANCED_MODE = "advanced_mode"
    private const val DISABLE_KSU_MODULES = "disable_ksu_modules"
    private const val LOAD_KERNEL_SU = "load_kernel_su"
    private const val SHIZUKU_MODE = "shizuku_mode"
    private const val BOOT_ROOT_MODE = "boot_root_mode"
    private const val SHIZUKU_BOOT_MODE = "shizuku_boot_mode"
    private const val BOOT_SETTLE_SECONDS = "boot_settle_seconds"
    private const val AUTO_ROOT_SETTLE_SECONDS = "auto_root_settle_seconds"
    private const val SHIZUKU_AUTOMATION_TOKEN = "shizuku_automation_token"
    private const val PARTITION_READ_ONLY_MODE = "partition_read_only_mode"
    private const val ADB_PAIRED = "adb_paired"
    private const val WIRELESS_ADB_OURS = "wireless_adb_owned"
    private const val PAYLOAD_MODE = "payload_mode"
    private const val BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
    private const val LOCAL_PAYLOAD_NAME = "local_payload_name"
    private const val PAYLOAD_SOURCES = "payload_sources"
    // Superseded by the source list; read once so an existing selection survives the upgrade.
    private const val LEGACY_PAYLOAD_REPOSITORY = "payload_repository"
    private const val LEGACY_PAYLOAD_BRANCH = "payload_branch"
    private const val CONSUMED_INSTALL_REQUEST = "consumed_install_request"

    fun payloadSources(context: Context): List<PayloadSource> {
        val stored = prefs(context).getString(PAYLOAD_SOURCES, null)
        if (stored == null) return listOf(legacyPayloadSource(context))
        return decodePayloadSources(stored).ifEmpty { listOf(PayloadSource.DEFAULT) }
    }

    fun setPayloadSources(context: Context, sources: List<PayloadSource>) {
        prefs(context).edit()
            .putString(PAYLOAD_SOURCES, encodePayloadSources(sources))
            .remove(LEGACY_PAYLOAD_REPOSITORY)
            .remove(LEGACY_PAYLOAD_BRANCH)
            .apply()
    }

    private fun legacyPayloadSource(context: Context): PayloadSource {
        val preferences = prefs(context)
        val repository = preferences.getString(LEGACY_PAYLOAD_REPOSITORY, null)
        val branch = preferences.getString(LEGACY_PAYLOAD_BRANCH, null)
        return PayloadSource.create(
            repository = repository ?: PayloadSource.DEFAULT_REPOSITORY,
            branch = branch ?: PayloadSource.DEFAULT_BRANCH,
        ) ?: PayloadSource.DEFAULT
    }

    private fun encodePayloadSources(sources: List<PayloadSource>): String {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(
                JSONObject()
                    .put("repository", source.repository)
                    .put("branch", source.branch)
                    .put("enabled", source.enabled)
                    .put("pinnedCommit", source.pinnedCommit),
            )
        }
        return array.toString()
    }

    private fun decodePayloadSources(stored: String): List<PayloadSource> = try {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val source = PayloadSource.create(
                    repository = item.optString("repository"),
                    branch = item.optString("branch"),
                    enabled = item.optBoolean("enabled", true),
                ) ?: continue
                // A stored pin is trusted only if it is a full commit; anything else would either
                // fail later or silently read the wrong revision.
                val stored = item.optString("pinnedCommit")
                val pinned = if (PayloadSource.isCommitValid(stored)) {
                    source.copy(pinnedCommit = stored.trim())
                } else {
                    source
                }
                if (none { it.id == pinned.id }) add(pinned)
            }
        }
    } catch (error: Throwable) {
        emptyList()
    }

    fun accentColor(context: Context): AccentColor = AccentColor.fromStoredValue(
        prefs(context).getString(ACCENT_COLOR, null),
    )

    fun setAccentColor(context: Context, color: AccentColor) {
        prefs(context).edit()
            .putString(ACCENT_COLOR, color.storedValue)
            .apply()
    }

    fun themeMode(context: Context): AppThemeMode = AppThemeMode.fromStoredValue(
        prefs(context).getString(THEME_MODE, null),
    )

    fun setThemeMode(context: Context, themeMode: AppThemeMode) {
        prefs(context).edit()
            .putString(THEME_MODE, themeMode.storedValue)
            .apply()
    }

    fun advancedMode(context: Context): Boolean =
        prefs(context).getBoolean(ADVANCED_MODE, false)

    fun setAdvancedMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(ADVANCED_MODE, enabled)
            .apply()
    }

    fun disableKsuModules(context: Context): Boolean =
        prefs(context).getBoolean(DISABLE_KSU_MODULES, false)

    fun setDisableKsuModules(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(DISABLE_KSU_MODULES, enabled)
            .apply()
    }

    /**
     * Whether a run loads KernelSU once the exploit has root.
     *
     * On by default, because loading it is what this app is for. Off is for a device where the module
     * is deliberately not wanted - another root solution is doing that job, or the load itself is the
     * thing that misbehaves - and the run then ends at the root the exploit won and says so rather
     * than reporting an install it did not make.
     *
     * A preference rather than a per-run choice: everything that depends on the load (root on boot,
     * the recovery actions, keeping the modules out of the way) reads the same answer, so it has to be
     * the same answer for all of them. A run freezes it at its start, like the transport.
     */
    fun loadKernelSu(context: Context): Boolean = prefs(context).getBoolean(LOAD_KERNEL_SU, true)

    fun setLoadKernelSu(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(LOAD_KERNEL_SU, enabled)
            .apply()
    }

    fun shizukuMode(context: Context): Boolean =
        prefs(context).getBoolean(SHIZUKU_MODE, false)

    fun setShizukuMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(SHIZUKU_MODE, enabled)
            .apply()
    }

    fun bootRootMode(context: Context): Boolean =
        prefs(context).getBoolean(BOOT_ROOT_MODE, false)

    fun setBootRootMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(BOOT_ROOT_MODE, enabled)
            .apply()
    }

    /**
     * Where a run takes its payload from. Online is the default because it is the mode that follows
     * the sources the user configured; Offline is what makes a run possible with no network.
     */
    fun payloadMode(context: Context): PayloadMode {
        val stored = prefs(context).getString(PAYLOAD_MODE, PayloadMode.Online.name)
        return PayloadMode.entries.firstOrNull { it.name == stored } ?: PayloadMode.Online
    }

    fun setPayloadMode(context: Context, mode: PayloadMode) {
        prefs(context).edit()
            .putString(PAYLOAD_MODE, mode.name)
            .apply()
    }

    /**
     * How long a run waits after a boot before the exploit starts. See [BootSettle]: the wait is
     * measured from the boot, so it is a floor on the device's uptime and not a delay per run.
     */
    fun bootSettleSeconds(context: Context): Int = BootSettle.normalize(
        prefs(context).getInt(BOOT_SETTLE_SECONDS, BootSettle.DEFAULT_SECONDS),
    )

    fun setBootSettleSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(BOOT_SETTLE_SECONDS, BootSettle.normalize(seconds))
            .apply()
    }

    /**
     * The same floor for the automatic install, stored separately on purpose.
     *
     * See [BootSettle.AUTO_ROOT_DEFAULT_SECONDS]: an automatic run has already waited out the boot
     * before it can act, so it needs a shorter floor than a manual one - and someone tuning it must not
     * be changing the wait a manual run does.
     */
    fun autoRootSettleSeconds(context: Context): Int = BootSettle.normalize(
        prefs(context).getInt(AUTO_ROOT_SETTLE_SECONDS, BootSettle.AUTO_ROOT_DEFAULT_SECONDS),
    )

    fun setAutoRootSettleSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(AUTO_ROOT_SETTLE_SECONDS, BootSettle.normalize(seconds))
            .apply()
    }

    /**
     * The token a Shizuku build may require before it honours an authenticated start request.
     *
     * Stored only so the app can include it in that one broadcast, and never written to the log or to
     * the run history: it is the credential that lets this app ask for a privileged process to be
     * started, so it is treated like one.
     */
    fun shizukuAutomationToken(context: Context): String =
        prefs(context).getString(SHIZUKU_AUTOMATION_TOKEN, "").orEmpty()

    fun setShizukuAutomationToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(SHIZUKU_AUTOMATION_TOKEN, token.trim())
            .apply()
    }

    /**
     * Whether a run marks the image partitions read-only once bootstrap root is in hand.
     *
     * Off unless asked for: see [PartitionReadOnly] - what it blocks is not only mistakes, so the
     * person who knows what they intend on their own device decides.
     */
    fun partitionReadOnlyMode(context: Context): Boolean =
        prefs(context).getBoolean(PARTITION_READ_ONLY_MODE, false)

    fun setPartitionReadOnlyMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(PARTITION_READ_ONLY_MODE, enabled)
            .apply()
    }

    /**
     * Whether a wireless-debugging pairing has ever succeeded.
     *
     * A record of an event, not a statement about now: adbd can forget this app's key without
     * anything here changing, so anything that depends on the transport asks the device instead of
     * reading this.
     */
    fun adbPaired(context: Context): Boolean = prefs(context).getBoolean(ADB_PAIRED, false)

    fun setAdbPaired(context: Context, paired: Boolean) {
        prefs(context).edit().putBoolean(ADB_PAIRED, paired).apply()
    }

    /**
     * Whether the wireless-debugging switch is on because this app turned it on.
     *
     * Persisted, not held in memory: the process that flipped it is often gone by the time the failsafe
     * alarm turns it back off, and a flag that died with that process would leave the switch on with
     * nobody left to restore it. It is also what keeps the app off a session the user started
     * themselves - the same setting serves a cable-free adb session this app knows nothing about.
     */
    fun wirelessAdbOwnedByApp(context: Context): Boolean =
        prefs(context).getBoolean(WIRELESS_ADB_OURS, false)

    fun setWirelessAdbOwnedByApp(context: Context, owned: Boolean) {
        prefs(context).edit()
            .putBoolean(WIRELESS_ADB_OURS, owned)
            .commit()
    }

    /** Whether Shizuku is started at boot through KernelSU, once the device already has root. */
    fun shizukuBootMode(context: Context): Boolean =
        prefs(context).getBoolean(SHIZUKU_BOOT_MODE, false)

    fun setShizukuBootMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(SHIZUKU_BOOT_MODE, enabled)
            .apply()
    }

    /**
     * Whether the one-shot battery-optimisation prompt has been shown. Persisted so declining it
     * is a decision rather than something the app re-asks on every launch.
     */
    fun batteryPromptShown(context: Context): Boolean =
        prefs(context).getBoolean(BATTERY_PROMPT_SHOWN, false)

    fun setBatteryPromptShown(context: Context, shown: Boolean) {
        prefs(context).edit()
            .putBoolean(BATTERY_PROMPT_SHOWN, shown)
            .apply()
    }

    /** Name of the imported payload, kept for display only; the file itself is in app storage. */
    fun localPayloadName(context: Context): String? =
        prefs(context).getString(LOCAL_PAYLOAD_NAME, null)

    fun setLocalPayloadName(context: Context, name: String?) {
        val editor = prefs(context).edit()
        if (name == null) editor.remove(LOCAL_PAYLOAD_NAME) else editor.putString(LOCAL_PAYLOAD_NAME, name)
        editor.apply()
    }

    @Synchronized
    fun consumeInstallRequest(context: Context, requestId: String?): Boolean {
        if (requestId.isNullOrBlank()) return false
        val preferences = prefs(context)
        if (preferences.getString(CONSUMED_INSTALL_REQUEST, null) == requestId) return false
        return preferences.edit()
            .putString(CONSUMED_INSTALL_REQUEST, requestId)
            .commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun languageTag(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else locales[0].toLanguageTag()
    }

    fun setLanguage(context: Context, languageTag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(languageTag)
    }
}
