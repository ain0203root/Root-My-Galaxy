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
    private const val SHIZUKU_MODE = "shizuku_mode"
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
                    .put("enabled", source.enabled),
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
                if (none { it.id == source.id }) add(source)
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

    fun shizukuMode(context: Context): Boolean =
        prefs(context).getBoolean(SHIZUKU_MODE, false)

    fun setShizukuMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(SHIZUKU_MODE, enabled)
            .apply()
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
