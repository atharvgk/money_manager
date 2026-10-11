package app.moneymanager.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPrefs(
    val onboardingDone: Boolean,
    val initialScanDone: Boolean,
    val lastSync: Long,
    val appLock: Boolean,
    val theme: ThemeMode,
    val notifyOnTransaction: Boolean,
    /** Monthly spending budget in paise; 0 = not set. */
    val monthlyBudget: Long,
)

class SettingsStore(private val context: Context) {
    private object K {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val INITIAL_SCAN = booleanPreferencesKey("initial_scan_done")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val THEME = stringPreferencesKey("theme")
        val NOTIFY = booleanPreferencesKey("notify")
        val BUDGET = longPreferencesKey("monthly_budget")
        val DATA_VERSION = intPreferencesKey("data_version")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            onboardingDone = p[K.ONBOARDING] ?: false,
            initialScanDone = p[K.INITIAL_SCAN] ?: false,
            lastSync = p[K.LAST_SYNC] ?: 0L,
            appLock = p[K.APP_LOCK] ?: false,
            theme = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            notifyOnTransaction = p[K.NOTIFY] ?: true,
            monthlyBudget = p[K.BUDGET] ?: 0L,
        )
    }

    suspend fun current(): AppPrefs = prefs.first()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setOnboardingDone(v: Boolean) = set(K.ONBOARDING, v)
    suspend fun setInitialScanDone(v: Boolean) = set(K.INITIAL_SCAN, v)
    suspend fun setLastSync(v: Long) = set(K.LAST_SYNC, v)
    suspend fun setAppLock(v: Boolean) = set(K.APP_LOCK, v)
    suspend fun setTheme(v: ThemeMode) = set(K.THEME, v.name)
    suspend fun setNotify(v: Boolean) = set(K.NOTIFY, v)
    suspend fun setMonthlyBudget(paise: Long) = set(K.BUDGET, paise.coerceAtLeast(0))

    /** One-time upgrades of stored data (see [app.moneymanager.data.Repository.upgradeData]). */
    suspend fun dataVersion(): Int = context.dataStore.data.first()[K.DATA_VERSION] ?: 0
    suspend fun setDataVersion(v: Int) = set(K.DATA_VERSION, v)
}
