package com.strumwise.app.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strumwise.app.songs.SongCatalog
import com.strumwise.data.scores.ScoreRepository
import com.strumwise.data.scores.SongBest
import com.strumwise.game.SongChart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SongItem(val chart: SongChart, val best: SongBest?) {
    /** Whole bars in the chart, for the loop-section picker. */
    val bars: Int get() = kotlin.math.ceil((chart.events.last().timeMs + 1) / (chart.beatMs * chart.beatsPerBar)).toInt()
}

data class SongListState(val loading: Boolean = true, val songs: List<SongItem> = emptyList())

@HiltViewModel
class SongListViewModel @Inject constructor(catalog: SongCatalog, scores: ScoreRepository) : ViewModel() {
    val state: StateFlow<SongListState> = combine(flow { emit(catalog.all()) }, scores.bests) { charts, bests ->
        SongListState(loading = false, songs = charts.map { SongItem(it, bests[it.id]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongListState())
}
