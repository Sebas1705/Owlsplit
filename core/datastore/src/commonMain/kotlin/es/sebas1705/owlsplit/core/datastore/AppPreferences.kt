package es.sebas1705.owlsplit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The app's language; [tag] null follows the device. */
enum class AppLanguage(val tag: String?) { SYSTEM(null), SPANISH("es"), ENGLISH("en") }

/** The user's profile as stored on this device; the pay handles are null when not set. */
data class StoredProfile(
    val name: String,
    val avatar: String,
    val currency: String,
    val bizum: String? = null,
    val paypal: String? = null,
    val revolut: String? = null,
)

/** How the app looks and reads (theme, language); an interface so screens can be tested without a DataStore. */
interface DisplaySettings {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val language: Flow<AppLanguage>
    suspend fun setLanguage(language: AppLanguage)
}

/** Whether the "protect your groups" suggestion was dismissed. */
interface LinkNudgeSettings {
    val linkNudgeDismissed: Flow<Boolean>
    suspend fun dismissLinkNudge()
}

/** The release whose update the user put off (its versionCode): not offered again, a newer one is. */
interface UpdateSettings {
    val dismissedUpdate: Flow<Long?>
    suspend fun dismissUpdate(versionCode: Long)
}

/** Small values the app can rebuild (exchange rates): kept on the device, never synced. */
interface KeyValueCache {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

class AppPreferences(private val dataStore: DataStore<Preferences>) : DisplaySettings, LinkNudgeSettings, UpdateSettings, KeyValueCache {

    // ── Theme ─────────────────────────────────────────────────────────────────
    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }
    override suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[Keys.THEME] = mode.name } }

    // ── Language ──────────────────────────────────────────────────────────────
    override val language: Flow<AppLanguage> = dataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE]?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } } ?: AppLanguage.SYSTEM
    }
    override suspend fun setLanguage(language: AppLanguage) { dataStore.edit { it[Keys.LANGUAGE] = language.name } }

    // ── Profile: null until onboarding is finished ────────────────────────────
    val profile: Flow<StoredProfile?> = dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME] ?: return@map null
        StoredProfile(
            name,
            prefs[Keys.AVATAR].orEmpty(),
            prefs[Keys.CURRENCY] ?: DEFAULT_CURRENCY,
            prefs[Keys.BIZUM],
            prefs[Keys.PAYPAL],
            prefs[Keys.REVOLUT],
        )
    }
    suspend fun setProfile(profile: StoredProfile) {
        dataStore.edit {
            it[Keys.NAME] = profile.name
            it[Keys.AVATAR] = profile.avatar
            it[Keys.CURRENCY] = profile.currency
            it.setOrRemove(Keys.BIZUM, profile.bizum)
            it.setOrRemove(Keys.PAYPAL, profile.paypal)
            it.setOrRemove(Keys.REVOLUT, profile.revolut)
        }
    }

    // ── Link nudge ────────────────────────────────────────────────────────────
    override val linkNudgeDismissed: Flow<Boolean> = dataStore.data.map { it[Keys.LINK_NUDGE_DISMISSED] ?: false }
    override suspend fun dismissLinkNudge() { dataStore.edit { it[Keys.LINK_NUDGE_DISMISSED] = true } }

    // ── Updates ───────────────────────────────────────────────────────────────
    override val dismissedUpdate: Flow<Long?> = dataStore.data.map { it[Keys.DISMISSED_UPDATE] }
    override suspend fun dismissUpdate(versionCode: Long) { dataStore.edit { it[Keys.DISMISSED_UPDATE] = versionCode } }

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
        val BIZUM = stringPreferencesKey("profile_bizum")
        val PAYPAL = stringPreferencesKey("profile_paypal")
        val REVOLUT = stringPreferencesKey("profile_revolut")
        val LINK_NUDGE_DISMISSED = booleanPreferencesKey("link_nudge_dismissed")
        val DISMISSED_UPDATE = longPreferencesKey("dismissed_update")
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value.isNullOrBlank()) remove(key) else this[key] = value
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

expect fun createDataStore(): DataStore<Preferences>
