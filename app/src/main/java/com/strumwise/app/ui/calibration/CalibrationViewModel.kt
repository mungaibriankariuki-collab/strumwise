package com.strumwise.app.ui.calibration

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strumwise.audio.GuitarEngine
import com.strumwise.data.settings.SettingsRepository
import com.strumwise.game.Calibration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalibrationState(
    val savedOffsetMs: Int = 0,
    val running: Boolean = false,
    val tapsCounted: Int = 0,
    val measuredOffsetMs: Int? = null,
    val consistencyMs: Double? = null,
    val failed: Boolean = false,
)

/**
 * Plays a click track on the audio engine and records taps on the same audio clock. The median
 * tap-minus-click difference becomes the input offset used by the game's judgment.
 */
@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val engine: GuitarEngine,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state

    private var clicks: List<Long> = emptyList()
    private val taps = ArrayList<Long>()
    private var run: Job? = null

    init {
        viewModelScope.launch { _state.update { it.copy(savedOffsetMs = settings.settings.first().calibrationOffsetMs) } }
    }

    fun start() {
        run?.cancel()
        taps.clear()
        val interval = 60_000_000_000L / BPM
        val first = engine.getAudioTimeNanos() + START_DELAY_NANOS
        clicks = List(LEAD_IN + COUNTED) { first + it * interval }
        engine.muteAll()
        engine.setMetronome(BPM.toFloat(), first, 4)
        _state.update { it.copy(running = true, tapsCounted = 0, measuredOffsetMs = null, consistencyMs = null, failed = false) }
        run = viewModelScope.launch {
            // Wait (on the audio clock) until half a beat after the last click.
            while (engine.getAudioTimeNanos() < clicks.last() + interval / 2) delay(50)
            finish()
        }
    }

    /** A tap that happened at [uptimeMs] (the touch event's time). */
    fun onTap(uptimeMs: Long) {
        if (!_state.value.running) return
        val age = (SystemClock.uptimeMillis() - uptimeMs).coerceAtLeast(0L) * 1_000_000
        val at = engine.getAudioTimeNanos() - age
        val interval = clicks[1] - clicks[0]
        // Taps during the lead-in bar only help the player find the beat; they aren't measured.
        if (at < clicks[LEAD_IN] - interval / 2) return
        taps += at
        _state.update { it.copy(tapsCounted = taps.size) }
    }

    /** 0..1 position within the current beat while running (for the pulsing beat light), else null. */
    fun beatPhase(): Float? {
        if (!_state.value.running || clicks.size < 2) return null
        val interval = clicks[1] - clicks[0]
        val since = engine.getAudioTimeNanos() - clicks[0]
        if (since < 0) return null
        return (since % interval).toFloat() / interval
    }

    fun save() {
        val offset = _state.value.measuredOffsetMs ?: return
        viewModelScope.launch {
            settings.setCalibrationOffset(offset)
            _state.update { it.copy(savedOffsetMs = offset) }
        }
    }

    fun reset() {
        viewModelScope.launch {
            settings.setCalibrationOffset(0)
            _state.update { it.copy(savedOffsetMs = 0) }
        }
    }

    private fun finish() {
        engine.setMetronome(0f, 0L)
        val clickMs = clicks.drop(LEAD_IN).map { it / 1e6 }
        val tapMs = taps.map { it / 1e6 }
        val offset = Calibration.computeOffsetMs(clickMs, tapMs)
        _state.update {
            it.copy(
                running = false,
                measuredOffsetMs = offset,
                consistencyMs = Calibration.consistencyMs(clickMs, tapMs),
                failed = offset == null,
            )
        }
    }

    override fun onCleared() {
        engine.setMetronome(0f, 0L)
    }

    companion object {
        const val BPM = 100
        const val LEAD_IN = 4
        const val COUNTED = 16
        private const val START_DELAY_NANOS = 800_000_000L
    }
}
