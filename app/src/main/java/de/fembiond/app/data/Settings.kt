package de.fembiond.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.fembiond.app.domain.Phase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class ThemeMode(val label: String) { SYSTEM("Wie System"), LIGHT("Hell"), DARK("Dunkel") }

data class Settings(
    val onboardingDone: Boolean = false,
    val birthYear: Int? = null,
    val typicalCycle: Int = 28,
    val typicalPeriod: Int = 5,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lockEnabled: Boolean = false,
    val phaseOverride: Phase? = null,
    val phaseOverrideCycleStart: LocalDate? = null,
    val gadIntervalDays: Int = 14,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
) {
    val age: Int? get() = birthYear?.let { LocalDate.now().year - it }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding")
        val birthYear = intPreferencesKey("birthYear")
        val cycle = intPreferencesKey("cycle")
        val period = intPreferencesKey("period")
        val theme = stringPreferencesKey("theme")
        val lock = booleanPreferencesKey("lock")
        val override = stringPreferencesKey("phaseOverride")
        val overrideStart = longPreferencesKey("phaseOverrideStart")
        val gad = intPreferencesKey("gadInterval")
        val reminder = booleanPreferencesKey("reminder")
        val reminderHour = intPreferencesKey("reminderHour")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[K.onboarding] ?: false,
            birthYear = p[K.birthYear],
            typicalCycle = p[K.cycle] ?: 28,
            typicalPeriod = p[K.period] ?: 5,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            lockEnabled = p[K.lock] ?: false,
            phaseOverride = p[K.override]?.let { runCatching { Phase.valueOf(it) }.getOrNull() },
            phaseOverrideCycleStart = p[K.overrideStart]?.let(LocalDate::ofEpochDay),
            gadIntervalDays = p[K.gad] ?: 14,
            reminderEnabled = p[K.reminder] ?: true,
            reminderHour = p[K.reminderHour] ?: 20,
        )
    }

    suspend fun update(block: (Settings) -> Settings, current: Settings) {
        val n = block(current)
        context.dataStore.edit { p ->
            p[K.onboarding] = n.onboardingDone
            n.birthYear?.let { p[K.birthYear] = it } ?: p.remove(K.birthYear)
            p[K.cycle] = n.typicalCycle
            p[K.period] = n.typicalPeriod
            p[K.theme] = n.theme.name
            p[K.lock] = n.lockEnabled
            n.phaseOverride?.let { p[K.override] = it.name } ?: p.remove(K.override)
            n.phaseOverrideCycleStart?.let { p[K.overrideStart] = it.toEpochDay() } ?: p.remove(K.overrideStart)
            p[K.gad] = n.gadIntervalDays
            p[K.reminder] = n.reminderEnabled
            p[K.reminderHour] = n.reminderHour
        }
    }

    @Suppress("unused")
    private fun <T> Preferences.getOr(key: Preferences.Key<T>, default: T): T = this[key] ?: default
}
