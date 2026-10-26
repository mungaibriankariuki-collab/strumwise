package com.strumwise.app.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.monetization.AdBannerSlot
import com.strumwise.app.nav.GameRoute
import com.strumwise.app.ui.theme.StrumColors
import com.strumwise.game.Difficulty
import com.strumwise.game.PracticeOptions
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SongListScreen(onBack: () -> Unit, onPlay: (GameRoute) -> Unit, viewModel: SongListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SongListContent(state, onBack, onPlay)
}

@Composable
fun SongListContent(state: SongListState, onBack: () -> Unit, onPlay: (GameRoute) -> Unit) {
    var chosen by remember { mutableStateOf<SongItem?>(null) }
    Column(Modifier.fillMaxSize().background(StrumColors.Espresso).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StrumColors.Cream) }
            Text("Songs", style = MaterialTheme.typography.headlineMedium, color = StrumColors.Cream)
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                // TODO(billing): show a lock and route to Purchases.launchPurchase for songs where
                //  Purchases.isSongUnlocked(id) is false. Every song is unlocked today.
                items(state.songs, key = { it.chart.id }) { item -> SongRow(item) { chosen = item } }
            }
            AdBannerSlot(Modifier.fillMaxWidth())
        }
    }
    chosen?.let { item ->
        PracticeDialog(item, onDismiss = { chosen = null }, onPlay = { chosen = null; onPlay(it) })
    }
}

@Composable
private fun SongRow(item: SongItem, onClick: () -> Unit) {
    val chart = item.chart
    Row(
        Modifier
            .fillMaxWidth()
            .background(StrumColors.EspressoRaised, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Choose ${chart.title}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(chart.title, style = MaterialTheme.typography.titleMedium, color = StrumColors.Cream)
            Text(
                "${chart.artist} · ${chart.bpm.roundToInt()} bpm · ${chart.chordsUsed.joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall,
                color = StrumColors.CreamMuted,
            )
        }
        DifficultyChip(chart.difficulty)
        Column(horizontalAlignment = Alignment.End) {
            BestStars(item.best?.stars ?: 0)
            Text(
                item.best?.highScore?.takeIf { it > 0 }?.let { String.format(Locale.US, "Best %,d", it) } ?: "Not played",
                style = MaterialTheme.typography.labelSmall,
                color = StrumColors.CreamMuted,
            )
        }
    }
}

@Composable
fun DifficultyChip(difficulty: Difficulty) {
    val (label, color) = when (difficulty) {
        Difficulty.EASY -> "Easy" to StrumColors.Lagoon
        Difficulty.MEDIUM -> "Medium" to StrumColors.Gold
        Difficulty.HARD -> "Hard" to StrumColors.Ember
    }
    Text(
        label,
        color = StrumColors.Espresso,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun BestStars(stars: Int) {
    Row(Modifier.semantics { contentDescription = "Best: $stars of 3 stars" }) {
        repeat(3) { i ->
            Icon(
                if (i < stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i < stars) StrumColors.Gold else Color(0x66CDBBA3),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Speed and loop-section options before starting a song. */
@Composable
private fun PracticeDialog(item: SongItem, onDismiss: () -> Unit, onPlay: (GameRoute) -> Unit) {
    var speedIndex by rememberSaveable { mutableStateOf(PracticeOptions.SPEEDS.lastIndex) }
    var loop by rememberSaveable { mutableStateOf(false) }
    val bars = item.bars.coerceAtLeast(2)
    var from by remember { mutableFloatStateOf(0f) }
    var to by remember { mutableFloatStateOf(minOf(4f, bars.toFloat())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.chart.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Speed", color = StrumColors.CreamMuted)
                SingleChoiceSegmentedButtonRow {
                    PracticeOptions.SPEEDS.forEachIndexed { i, speed ->
                        SegmentedButton(
                            selected = i == speedIndex,
                            onClick = { speedIndex = i },
                            shape = SegmentedButtonDefaults.itemShape(i, PracticeOptions.SPEEDS.size),
                        ) { Text("${(speed * 100).toInt()}%") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop a section", modifier = Modifier.weight(1f))
                    Switch(checked = loop, onCheckedChange = { loop = it })
                }
                if (loop) {
                    Text("Bars ${from.roundToInt() + 1}–${to.roundToInt()} of $bars", color = StrumColors.CreamMuted)
                    RangeSlider(
                        value = from..to,
                        onValueChange = { r ->
                            from = r.start.roundToInt().toFloat()
                            to = maxOf(r.endInclusive.roundToInt().toFloat(), from + 1f)
                        },
                        valueRange = 0f..bars.toFloat(),
                        steps = bars - 1,
                    )
                }
                if (PracticeOptions.SPEEDS[speedIndex] != 1f || loop) {
                    Text("Practice runs don't count toward high scores.", style = MaterialTheme.typography.bodySmall, color = StrumColors.CreamMuted)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onPlay(
                    GameRoute(
                        songId = item.chart.id,
                        speed = PracticeOptions.SPEEDS[speedIndex],
                        loopFromBar = if (loop) from.roundToInt() else -1,
                        loopToBar = if (loop) to.roundToInt() else -1,
                    ),
                )
            }) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
