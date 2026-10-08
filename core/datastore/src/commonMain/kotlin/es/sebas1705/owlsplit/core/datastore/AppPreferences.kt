package es.sebas1705.owlsplit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The user's profile as stored on this device. */
data class StoredProfile(val name: String, val avatar: String, val currency: String)

/** Light/dark preference, separate so screens can be tested without a DataStore. */
interface ThemeSettings {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

/** Whether the "protect your groups" suggestion was dismissed. */
interface LinkNudgeSettings {
    val linkNudgeDismissed: Flow<Boolean>
    suspend fun dismissLinkNudge()
}

/** Small values the app can rebuild (exchange rates): kept on the device, never synced. */
interface KeyValueCache {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

class AppPreferences(private val dataStore: DataStore<Preferences>) : ThemeSettings, LinkNudgeSettings, KeyValueCache {

    // ── Theme ─────────────────────────────────────────────────────────────────
    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }
    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[Keys.THEME] = mode.name } }

    // ── Locale (P11) ──────────────────────────────────────────────────────────
    val language: Flow<String?> = dataStore.data.map { it[Keys.LANGUAGE] }
    suspend fun setLanguage(code: String) { dataStore.edit { it[Keys.LANGUAGE] = code } }

    // ── Profile: null until onboarding is finished ────────────────────────────
    val profile: Flow<StoredProfile?> = dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME] ?: return@map null
        StoredProfile(name, prefs[Keys.AVATAR].orEmpty(), prefs[Keys.CURRENCY] ?: DEFAULT_CURRENCY)
    }
    suspend fun setProfile(profile: StoredProfile) {
        dataStore.edit {
            it[Keys.NAME] = profile.name
            it[Keys.AVATAR] = profile.avatar
            it[Keys.CURRENCY] = profile.currency
        }
    }

    // ── Link nudge ────────────────────────────────────────────────────────────
    override val linkNudgeDismissed: Flow<Boolean> = dataStore.data.map { it[Keys.LINK_NUDGE_DISMISSED] ?: false }
    override suspend fun dismissLinkNudge() { dataStore.edit { it[Keys.LINK_NUDGE_DISMISSED] = true } }

    // ── Cache ─────────────────────────────────────────────────────────────────
    override suspend fun get(key: String): String? = dataStore.data.first()[stringPreferencesKey("cache:$key")]
    override suspend fun put(
        key: String,
        value: String
    ) { dataStore.edit { it[stringPreferencesKey("cache:$key")] = value } }

    /** Back to a fresh install (sign out, account deletion). */
    suspend fun clear() { dataStore.edit { it.clear() } }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NAME = stringPreferencesKey("profile_name")
        val AVATAR = stringPreferencesKey("profile_avatar")
        val CURRENCY = stringPreferencesKey("profile_currency")
        val LINK_NUDGE_DISMISSED = booleanPreferencesKey("link_nudge_dismissed")
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

expect fun createDataStore(): DataStore<Preferences>
