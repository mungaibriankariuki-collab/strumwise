package com.strumwise.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.strumwise.music.ChordLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data.map { p ->
        val defaults = UserSettings()
        UserSettings(
            masterVolume = p[VOLUME] ?: defaults.masterVolume,
            tuningId = p[TUNING] ?: defaults.tuningId,
            leftHanded = p[LEFT_HANDED] ?: defaults.leftHanded,
            hapticsEnabled = p[HAPTICS] ?: defaults.hapticsEnabled,
            stringVibration = p[VIBRATION] ?: defaults.stringVibration,
            calibrationOffsetMs = p[CALIBRATION] ?: defaults.calibrationOffsetMs,
            soundType = p[SOUND]?.let { name -> SoundPreference.entries.firstOrNull { it.name == name } }
                ?: defaults.soundType,
            chordSlots = decodeSlots(p[SLOTS]),
            debugOverlay = p[DEBUG_OVERLAY] ?: defaults.debugOverlay,
        )
    }.distinctUntilChanged()

    override suspend fun setMasterVolume(volume: Float) = put(VOLUME, volume.coerceIn(0f, 1f))
    override suspend fun setTuning(tuningId: String) = put(TUNING, tuningId)
    override suspend fun setLeftHanded(enabled: Boolean) = put(LEFT_HANDED, enabled)
    override suspend fun setHaptics(enabled: Boolean) = put(HAPTICS, enabled)
    override suspend fun setStringVibration(enabled: Boolean) = put(VIBRATION, enabled)
    override suspend fun setCalibrationOffset(offsetMs: Int) = put(CALIBRATION, offsetMs.coerceIn(-300, 300))
    override suspend fun setSoundType(type: SoundPreference) = put(SOUND, type.name)
    override suspend fun setDebugOverlay(enabled: Boolean) = put(DEBUG_OVERLAY, enabled)

    override suspend fun setChordSlot(index: Int, chordId: String) {
        if (ChordLibrary[chordId] == null) return
        store.edit { p ->
            val slots = decodeSlots(p[SLOTS]).toMutableList()
            if (index in slots.indices) {
                slots[index] = chordId
                p[SLOTS] = slots.joinToString(SEPARATOR)
            }
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    internal companion object {
        private const val SEPARATOR = ","
        val VOLUME = floatPreferencesKey("master_volume")
        val TUNING = stringPreferencesKey("tuning")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val HAPTICS = booleanPreferencesKey("haptics")
        val VIBRATION = booleanPreferencesKey("string_vibration")
        val CALIBRATION = intPreferencesKey("calibration_offset_ms")
        val SOUND = stringPreferencesKey("sound_type")
        val SLOTS = stringPreferencesKey("chord_slots")
        val DEBUG_OVERLAY = booleanPreferencesKey("debug_overlay")

        /** Unknown chord ids (e.g. from an older version) fall back to the default for that slot. */
        fun decodeSlots(raw: String?): List<String> {
            val defaults = ChordLibrary.DEFAULT_SLOTS
            val stored = raw?.split(SEPARATOR).orEmpty()
            return defaults.indices.map { i -> stored.getOrNull(i)?.takeIf { ChordLibrary[it] != null } ?: defaults[i] }
        }
    }
}
