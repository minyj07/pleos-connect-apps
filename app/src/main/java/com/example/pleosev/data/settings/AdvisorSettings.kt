package com.example.pleosev.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Driver settings for the charging advisor. */
data class AdvisorPrefs(
    /** The driver agreed to send the car's position and route to the advisor server (Pleos privacy policy). */
    val locationConsent: Boolean = false,
    val reserveSocPct: Int = 10,
    val minKw: Int = 50,
    val maxDetourMin: Int = 15,
    /** Keep watching while the navi covers the app (foreground service with a status notification). */
    val backgroundWatch: Boolean = true,
)

private val Context.advisorStore by preferencesDataStore(name = "advisor")

class AdvisorSettings(context: Context) {

    private val store = context.applicationContext.advisorStore

    val prefs: Flow<AdvisorPrefs> = store.data.map { p ->
        AdvisorPrefs(
            locationConsent = p[CONSENT] ?: false,
            reserveSocPct = p[RESERVE_SOC] ?: 10,
            minKw = p[MIN_KW] ?: 50,
            maxDetourMin = p[MAX_DETOUR] ?: 15,
            backgroundWatch = p[BACKGROUND_WATCH] ?: true,
        )
    }

    suspend fun setLocationConsent(given: Boolean) {
        store.edit { it[CONSENT] = given }
    }

    suspend fun setReserveSocPct(pct: Int) {
        store.edit { it[RESERVE_SOC] = pct.coerceIn(5, 30) }
    }

    suspend fun setMinKw(kw: Int) {
        store.edit { it[MIN_KW] = kw.coerceIn(0, 350) }
    }

    suspend fun setBackgroundWatch(on: Boolean) {
        store.edit { it[BACKGROUND_WATCH] = on }
    }

    private companion object {
        val CONSENT = booleanPreferencesKey("location_consent")
        val RESERVE_SOC = intPreferencesKey("reserve_soc_pct")
        val MIN_KW = intPreferencesKey("min_kw")
        val MAX_DETOUR = intPreferencesKey("max_detour_min")
        val BACKGROUND_WATCH = booleanPreferencesKey("background_watch")
    }
}
