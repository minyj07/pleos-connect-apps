package com.example.drivecoach.data

import android.content.Context
import com.example.drivecoach.parking.CarPresets
import com.example.drivecoach.parking.VehicleGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * App preferences. Only non-sensitive values live here (SharedPreferences is not encrypted);
 * driving data goes to [TripStore].
 */
class Settings(context: Context) {

    data class Values(
        /** Version of the consent text the user accepted; 0 = not accepted. */
        val consentVersion: Int = 0,
        val consentAtEpochMs: Long = 0L,
        val consentDeclined: Boolean = false,
        val recording: Boolean = true,
        val autoStartOnBoot: Boolean = true,
        val retentionDays: Int = 90,
        /** "auto" (vehicle dimensions), a preset id or "custom". */
        val vehicleProfile: String = PROFILE_AUTO,
        val customGeometry: VehicleGeometry? = null,
        /** Steering value measured at full lock in the calibration, raw SDK units. */
        val steeringFullLockRaw: Float? = null,
        /** Separate, optional consent for "폰으로 보기" (uploading encrypted copies); 0 = not given. */
        val shareConsentVersion: Int = 0,
        val shareConsentAtEpochMs: Long = 0L,
    ) {
        val consented: Boolean get() = consentVersion >= CONSENT_VERSION
        val needsConsentDecision: Boolean get() = !consented && !consentDeclined
        val shareConsented: Boolean get() = shareConsentVersion >= SHARE_CONSENT_VERSION
    }

    private val prefs = context.getSharedPreferences("drivecoach", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<Values> = _values.asStateFlow()

    @Synchronized
    fun update(transform: Values.() -> Values) {
        val v = _values.value.transform()
        prefs.edit()
            .putInt(K_CONSENT_VERSION, v.consentVersion)
            .putLong(K_CONSENT_AT, v.consentAtEpochMs)
            .putBoolean(K_DECLINED, v.consentDeclined)
            .putBoolean(K_RECORDING, v.recording)
            .putBoolean(K_AUTOSTART, v.autoStartOnBoot)
            .putInt(K_RETENTION, v.retentionDays)
            .putString(K_PROFILE, v.vehicleProfile)
            .putString(K_CUSTOM, v.customGeometry?.let { json.encodeToString(VehicleGeometry.serializer(), it) })
            .putFloat(K_STEER_LOCK, v.steeringFullLockRaw ?: -1f)
            .putInt(K_SHARE_CONSENT_VERSION, v.shareConsentVersion)
            .putLong(K_SHARE_CONSENT_AT, v.shareConsentAtEpochMs)
            .apply()
        _values.value = v
    }

    private fun read() = Values(
        consentVersion = prefs.getInt(K_CONSENT_VERSION, 0),
        consentAtEpochMs = prefs.getLong(K_CONSENT_AT, 0L),
        consentDeclined = prefs.getBoolean(K_DECLINED, false),
        recording = prefs.getBoolean(K_RECORDING, true),
        autoStartOnBoot = prefs.getBoolean(K_AUTOSTART, true),
        retentionDays = prefs.getInt(K_RETENTION, 90),
        vehicleProfile = prefs.getString(K_PROFILE, PROFILE_AUTO) ?: PROFILE_AUTO,
        customGeometry = prefs.getString(K_CUSTOM, null)?.let { runCatching { json.decodeFromString(VehicleGeometry.serializer(), it) }.getOrNull() },
        steeringFullLockRaw = prefs.getFloat(K_STEER_LOCK, -1f).takeIf { it > 0f },
        shareConsentVersion = prefs.getInt(K_SHARE_CONSENT_VERSION, 0),
        shareConsentAtEpochMs = prefs.getLong(K_SHARE_CONSENT_AT, 0L),
    )

    /** Geometry for the parking simulation: profile choice, falling back to the default preset. */
    fun geometry(v: Values, dimensionsMm: List<Int>?): VehicleGeometry = when (v.vehicleProfile) {
        PROFILE_AUTO -> dimensionsMm?.let { VehicleGeometry.fromAaosDimensions(it.toIntArray()) } ?: CarPresets.default
        PROFILE_CUSTOM -> v.customGeometry ?: CarPresets.default
        else -> CarPresets.byId(v.vehicleProfile) ?: CarPresets.default
    }

    companion object {
        /** Bump when the consent text changes materially; users are asked again. */
        const val CONSENT_VERSION = 1

        /** Bump when the "폰으로 보기" consent text changes materially. */
        const val SHARE_CONSENT_VERSION = 1
        const val PROFILE_AUTO = "auto"
        const val PROFILE_CUSTOM = "custom"

        private const val K_CONSENT_VERSION = "consent_version"
        private const val K_CONSENT_AT = "consent_at"
        private const val K_DECLINED = "consent_declined"
        private const val K_RECORDING = "recording"
        private const val K_AUTOSTART = "autostart"
        private const val K_RETENTION = "retention_days"
        private const val K_PROFILE = "vehicle_profile"
        private const val K_CUSTOM = "custom_geometry"
        private const val K_STEER_LOCK = "steer_full_lock"
        private const val K_SHARE_CONSENT_VERSION = "share_consent_version"
        private const val K_SHARE_CONSENT_AT = "share_consent_at"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
