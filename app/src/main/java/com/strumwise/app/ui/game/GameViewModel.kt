package com.strumwise.app.ui.game

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.strumwise.app.nav.GameRoute
import com.strumwise.app.nav.ResultsRoute
import com.strumwise.app.songs.SongCatalog
import com.strumwise.app.ui.guitar.GuitarPlayer
import com.strumwise.app.ui.guitar.PlayedNote
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.audio.GuitarEngine
import com.strumwise.data.scores.RunRecord
import com.strumwise.data.scores.ScoreRepository
import com.strumwise.data.settings.SettingsRepository
import com.strumwise.game.Feedback
import com.strumwise.game.GameClock
import com.strumwise.game.GameSession
import com.strumwise.game.InputGrouper
import com.strumwise.game.Judgment
import com.strumwise.game.PracticeOptions
import com.strumwise.game.SongChart
import com.strumwise.music.Chord
import com.strumwise.music.ChordLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.floor

enum class GamePhase { LOADING, PLAYING, PAUSED, FINISHED, ERROR }

data class GameUiState(
    val phase: GamePhase = GamePhase.LOADING,
    val title: String = "",
    val slots: List<Chord> = ChordLibrary.DEFAULT_SLOTS.map(ChordLibrary::getValue),
    val selectedSlot: Int? = null,
    val leftHanded: Boolean = false,
    val haptics: Boolean = true,
    val stringVibration: Boolean = true,
    val score: Long = 0,
    val combo: Int = 0,
    val multiplier: Int = 1,
    val countdown: Int? = null,
    val upcomingChordId: String? = null,
    val progress: Float = 0f,
    val practiceLabel: String? = null,
    val pickerSlot: Int? = null,
    val results: ResultsRoute? = null,
    val error: String? = null,
) {
    val selectedChord: Chord? get() = selectedSlot?.let { slots.getOrNull(it) }
}

/** Floating "Perfect" / "x2" text. [string] places it on a string; null = centred. */
data class FloatingText(val id: Long, val text: String, val kind: Kind, val string: Int?) {
    enum class Kind { PERFECT, GOOD, MISS, MULTIPLIER }
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val engine: GuitarEngine,
    private val catalog: SongCatalog,
    private val settings: SettingsRepository,
    private val scores: ScoreRepository,
) : ViewModel() {
    private val route = savedState.toRoute<GameRoute>()
    private val player = GuitarPlayer(engine)
    private val grouper = InputGrouper()
    private val clock = GameClock(engine::getAudioTimeNanos)

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    private val _floating = MutableStateFlow<List<FloatingText>>(emptyList())
    val floating: StateFlow<List<FloatingText>> = _floating
    private var nextFloatingId = 0L

    /** The running session; read by the marker overlay during draw. Null until loaded. */
    var session: GameSession? = null
        private set

    private var chart: SongChart? = null
    private var calibrationMs = 0

    init {
        viewModelScope.launch {
            val chart = catalog.byId(route.songId)
            if (chart == null) {
                _state.update { it.copy(phase = GamePhase.ERROR, error = "Song not found") }
                return@launch
            }
            this@GameViewModel.chart = chart
            val s = settings.settings.first()
            calibrationMs = s.calibrationOffsetMs
            _state.update {
                it.copy(
                    title = chart.title,
                    slots = s.chordSlots.map(ChordLibrary::getValue),
                    leftHanded = s.leftHanded,
                    haptics = s.hapticsEnabled,
                    stringVibration = s.stringVibration,
                    practiceLabel = practiceLabel(),
                )
            }
            startSession()
        }
        // Keep the chord column in sync if the player swaps a chord mid-song.
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(slots = s.chordSlots.map(ChordLibrary::getValue)) } }
        }
    }

    private fun practiceOptions(chart: SongChart): PracticeOptions {
        val loop = if (route.loopFromBar >= 0 && route.loopToBar > route.loopFromBar) {
            PracticeOptions.loopBars(chart, route.loopFromBar, route.loopToBar)
        } else {
            null
        }
        return PracticeOptions(speed = route.speed, loop = loop)
    }

    private fun practiceLabel(): String? {
        val parts = buildList {
            if (route.speed != 1f) add("${(route.speed * 100).toInt()}% speed")
            if (route.loopFromBar >= 0) add("Loop bars ${route.loopFromBar + 1}–${route.loopToBar}")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun startSession() {
        val chart = chart ?: return
        engine.muteAll()
        grouper.reset()
        val s = GameSession(chart, clock, practiceOptions(chart), calibrationMs)
        session = s
        s.start()
        syncMetronome()
        _floating.value = emptyList()
        _state.update { it.copy(phase = GamePhase.PLAYING, score = 0, combo = 0, multiplier = 1, results = null) }
    }

    /** Called by the screen once per frame while visible. */
    fun onFrame() {
        val s = session ?: return
        if (_state.value.phase != GamePhase.PLAYING) return
        val now = s.nowMs()
        val update = s.update(now)
        update.feedback.forEach(::showFeedback)
        if (update.loopedBack) syncMetronome()
        _state.update {
            it.copy(
                score = s.score.score,
                combo = s.score.combo,
                multiplier = s.score.multiplier,
                countdown = s.countdown(now),
                upcomingChordId = s.upcomingChord(now),
                progress = ((now - s.startMs) / (s.chart.lengthMs - s.startMs)).toFloat().coerceIn(0f, 1f),
            )
        }
        if (update.finished) finish()
    }

    fun onStringHits(hits: List<StringHit>): List<PlayedNote> {
        val played = player.strum(hits)
        val s = session
        val first = hits.firstOrNull()
        if (s != null && first != null && _state.value.phase == GamePhase.PLAYING) {
            val isStrum = first.direction != null
            if (grouper.isNewInput(first.timeMs, isStrum)) {
                // Judge the moment the finger moved, not when this code runs.
                val ageNanos = (SystemClock.uptimeMillis() - first.timeMs).coerceAtLeast(0L) * 1_000_000
                val strings = played.map { it.string }.toSet()
                s.onInput(engine.getAudioTimeNanos() - ageNanos, player.chord?.id, strings, isStrum)?.let(::showFeedback)
            }
        }
        return played
    }

    fun onChordTapped(slot: Int) {
        _state.update { it.copy(selectedSlot = if (it.selectedSlot == slot) null else slot) }
        player.chord = _state.value.selectedChord
    }

    fun onChordLongPressed(slot: Int) = _state.update { it.copy(pickerSlot = slot) }
    fun onPickerDismissed() = _state.update { it.copy(pickerSlot = null) }
    fun onChordPicked(chordId: String) {
        val slot = _state.value.pickerSlot ?: return
        _state.update { it.copy(pickerSlot = null) }
        viewModelScope.launch {
            settings.setChordSlot(slot, chordId)
            player.chord = _state.value.selectedChord
        }
    }

    fun pause() {
        if (_state.value.phase != GamePhase.PLAYING) return
        session?.pause()
        engine.setMetronome(0f, 0L)
        engine.muteAll()
        _state.update { it.copy(phase = GamePhase.PAUSED) }
    }

    fun resume() {
        if (_state.value.phase != GamePhase.PAUSED) return
        session?.resume()
        syncMetronome()
        _state.update { it.copy(phase = GamePhase.PLAYING) }
    }

    fun restart() = startSession()

    /** Quit from the pause menu: the run ends and goes to results. */
    fun quit() {
        session?.finishNow()
        finish()
    }

    fun dismissFloating(id: Long) = _floating.update { list -> list.filterNot { it.id == id } }

    override fun onCleared() {
        engine.setMetronome(0f, 0L)
        engine.muteAll()
    }

    private fun finish() {
        val s = session ?: return
        if (_state.value.phase == GamePhase.FINISHED) return
        engine.setMetronome(0f, 0L)
        _state.update { it.copy(phase = GamePhase.FINISHED) }
        viewModelScope.launch {
            val previous = scores.best(s.chart.id)?.highScore
            val result = s.result(previous)
            scores.record(
                s.chart.id,
                RunRecord(result.score, result.stars, result.accuracyPercent, result.maxCombo, countsForBest = !s.practice.isPractice),
            )
            _state.update {
                it.copy(
                    results = ResultsRoute(
                        songId = s.chart.id,
                        title = s.chart.title,
                        score = result.score,
                        maxCombo = result.maxCombo,
                        perfect = result.perfect,
                        good = result.good,
                        miss = result.miss,
                        accuracy = result.accuracyPercent.toFloat(),
                        stars = result.stars,
                        newHighScore = result.isNewHighScore,
                        practice = s.practice.isPractice,
                        speed = s.practice.speed,
                    ),
                )
            }
        }
    }

    /**
     * Click track locked to the song grid: we pass the most recent bar start (possibly in the past);
     * the engine joins the grid with the correct downbeat accent.
     */
    private fun syncMetronome() {
        val s = session ?: return
        val barMs = s.chart.beatMs * s.chart.beatsPerBar
        val now = s.nowMs()
        val barStart = s.startMs + floor((now - s.startMs) / barMs).coerceAtLeast(0.0) * barMs
        engine.setMetronome(s.chart.bpm * s.practice.speed, clock.sourceTimeAt(barStart), s.chart.beatsPerBar)
    }

    private fun showFeedback(f: Feedback) {
        val string = f.result.note.event.string
        val kind = when (f.result.judgment) {
            Judgment.PERFECT -> FloatingText.Kind.PERFECT
            Judgment.GOOD -> FloatingText.Kind.GOOD
            Judgment.MISS -> FloatingText.Kind.MISS
        }
        val label = when {
            f.result.wrongChord -> "Wrong chord"
            else -> kind.name.lowercase().replaceFirstChar(Char::uppercase)
        }
        val added = buildList {
            add(FloatingText(nextFloatingId++, label, kind, string))
            if (f.score.multiplierChanged && f.score.multiplier > 1) {
                add(FloatingText(nextFloatingId++, "x${f.score.multiplier}", FloatingText.Kind.MULTIPLIER, null))
            }
        }
        // Keep the list short: old texts are gone visually anyway.
        _floating.update { (it + added).takeLast(8) }
    }
}
