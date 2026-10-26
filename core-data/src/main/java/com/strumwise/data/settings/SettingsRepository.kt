package com.strumwise.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setMasterVolume(volume: Float)
    suspend fun setTuning(tuningId: String)
    suspend fun setLeftHanded(enabled: Boolean)
    suspend fun setHaptics(enabled: Boolean)
    suspend fun setStringVibration(enabled: Boolean)
    suspend fun setCalibrationOffset(offsetMs: Int)
    suspend fun setSoundType(type: SoundPreference)
    suspend fun setChordSlot(index: Int, chordId: String)
    suspend fun setDebugOverlay(enabled: Boolean)
}
