package com.strumwise.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strumwise.audio.GuitarEngine
import com.strumwise.data.settings.SettingsRepository
import com.strumwise.data.settings.SoundPreference
import com.strumwise.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val engine: GuitarEngine,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val samplesInstalled: Boolean get() = engine.loadedSampleCount > 0

    fun setVolume(v: Float) = launch { repo.setMasterVolume(v) }
    fun setTuning(id: String) = launch { repo.setTuning(id) }
    fun setLeftHanded(on: Boolean) = launch { repo.setLeftHanded(on) }
    fun setHaptics(on: Boolean) = launch { repo.setHaptics(on) }
    fun setVibration(on: Boolean) = launch { repo.setStringVibration(on) }
    fun setSoundType(type: SoundPreference) = launch { repo.setSoundType(type) }
    fun setCalibration(ms: Int) = launch { repo.setCalibrationOffset(ms) }
    fun setDebugOverlay(on: Boolean) = launch { repo.setDebugOverlay(on) }

    /** Plays an open G chord so volume and sound changes can be heard straight away. */
    fun preview() {
        intArrayOf(3, 2, 0, 0, 0, 3).forEachIndexed { s, f -> engine.noteOn(s, f, 0.7f, s * 9f) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
