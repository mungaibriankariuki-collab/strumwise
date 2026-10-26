package com.strumwise.app.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strumwise.app.ui.guitar.GuitarPlayer
import com.strumwise.app.ui.guitar.PlayedNote
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.audio.GuitarEngine
import com.strumwise.data.settings.SettingsRepository
import com.strumwise.data.settings.UserSettings
import com.strumwise.music.Chord
import com.strumwise.music.ChordLibrary
import com.strumwise.music.Tuning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayUiState(
    val slots: List<Chord> = ChordLibrary.DEFAULT_SLOTS.map(ChordLibrary::getValue),
    val selectedSlot: Int? = null,
    val freeFret: Boolean = false,
    val leftHanded: Boolean = false,
    val haptics: Boolean = true,
    val stringVibration: Boolean = true,
    val tuning: Tuning = Tuning.STANDARD,
    /** Slot whose chord picker is open, if any. */
    val pickerSlot: Int? = null,
) {
    val selectedChord: Chord? get() = selectedSlot?.let { slots.getOrNull(it) }
}

private data class LocalState(val selectedSlot: Int? = null, val freeFret: Boolean = false, val pickerSlot: Int? = null)

@HiltViewModel
class PlayViewModel @Inject constructor(
    engine: GuitarEngine,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val player = GuitarPlayer(engine)
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<PlayUiState> = combine(settings.settings, local) { s: UserSettings, l: LocalState ->
        val ui = PlayUiState(
            slots = s.chordSlots.map(ChordLibrary::getValue),
            selectedSlot = l.selectedSlot,
            freeFret = l.freeFret,
            leftHanded = s.leftHanded,
            haptics = s.hapticsEnabled,
            stringVibration = s.stringVibration,
            tuning = Tuning.byId(s.tuningId),
            pickerSlot = l.pickerSlot,
        )
        player.chord = ui.selectedChord
        ui
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayUiState())

    /** Tapping the selected chord again releases it (open strings). Choosing a chord leaves free-fret mode. */
    fun onChordTapped(slot: Int) {
        local.update { it.copy(selectedSlot = if (it.selectedSlot == slot) null else slot, freeFret = false) }
        syncChord()
    }

    fun onChordLongPressed(slot: Int) = local.update { it.copy(pickerSlot = slot) }

    fun onPickerDismissed() = local.update { it.copy(pickerSlot = null) }

    fun onChordPicked(chordId: String) {
        val slot = local.value.pickerSlot ?: return
        local.update { it.copy(pickerSlot = null) }
        viewModelScope.launch { settings.setChordSlot(slot, chordId) }
    }

    fun toggleFreeFret() {
        local.update { it.copy(freeFret = !it.freeFret, selectedSlot = null) }
        player.releaseAll()
        syncChord()
    }

    fun onStringHits(hits: List<StringHit>): List<PlayedNote> = player.strum(hits)

    fun onFretDown(pointerId: Long, string: Int, fret: Int): PlayedNote = player.fretDown(pointerId, string, fret)

    fun onFretUp(pointerId: Long) = player.fretUp(pointerId)

    /** Keep the player's chord in step immediately, without waiting for the combined flow. */
    private fun syncChord() {
        val l = local.value
        player.chord = l.selectedSlot?.let { state.value.slots.getOrNull(it) }
    }
}
