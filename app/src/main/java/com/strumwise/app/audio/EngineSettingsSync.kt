package com.strumwise.app.audio

import com.strumwise.audio.GuitarEngine
import com.strumwise.audio.SoundType
import com.strumwise.data.settings.SettingsRepository
import com.strumwise.data.settings.SoundPreference
import com.strumwise.music.Tuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the audio engine in step with the user's volume, tuning and sound-type settings. */
@Singleton
class EngineSettingsSync @Inject constructor(
    private val engine: GuitarEngine,
    private val settings: SettingsRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings.map { it.masterVolume }.distinctUntilChanged().collect(engine::setMasterVolume)
        }
        scope.launch {
            settings.settings.map { it.tuningId }.distinctUntilChanged().collect { id ->
                engine.setTuning(Tuning.byId(id).openStringHz())
            }
        }
        scope.launch {
            settings.settings.map { it.soundType }.distinctUntilChanged().collect { type ->
                engine.setSoundType(if (type == SoundPreference.SAMPLES) SoundType.SAMPLES else SoundType.SYNTH)
            }
        }
    }
}
