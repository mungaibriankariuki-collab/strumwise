package com.strumwise.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.nav.ResultsRoute
import com.strumwise.app.ui.components.ChordColumn
import com.strumwise.app.ui.components.ChordPickerDialog
import com.strumwise.app.ui.guitar.GuitarLayout
import com.strumwise.app.ui.guitar.GuitarSurface
import com.strumwise.app.ui.guitar.GuitarTouchListener
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.app.ui.guitar.StringVibrations
import com.strumwise.app.ui.guitar.tickForNotes
import com.strumwise.app.ui.theme.StrumColors
import java.util.Locale

@Composable
fun GameScreen(
    onFinished: (ResultsRoute) -> Unit,
    onQuitToSongs: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val floating by viewModel.floating.collectAsStateWithLifecycle()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val vibrations = remember { StringVibrations() }
    val textMeasurer = rememberTextMeasurer()
    val arrow = remember { Path() }
    // Frame time drives the markers; written every frame, read only in the draw phase.
    var frameNanos by remember { mutableLongStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }

    LaunchedEffect(state.phase) {
        while (state.phase == GamePhase.PLAYING) {
            withFrameNanos { frameNanos = it }
            viewModel.onFrame()
        }
    }
    LaunchedEffect(state.results) { state.results?.let(onFinished) }

    val listener = object : GuitarTouchListener {
        override fun onStringHits(hits: List<StringHit>) {
            val notes = viewModel.onStringHits(hits)
            if (state.stringVibration) notes.forEach { vibrations.pluck(it.string, it.fret, it.velocity, it.delayMs) }
            if (state.haptics) view.tickForNotes(notes, scope)
        }
        override fun onFretDown(pointerId: Long, string: Int, fret: Int) = Unit
        override fun onFretUp(pointerId: Long) = Unit
    }

    Row(Modifier.fillMaxSize().background(StrumColors.Espresso)) {
        val chords = @Composable { mod: Modifier ->
            ChordColumn(
                slots = state.slots,
                selectedSlot = state.selectedSlot,
                onTap = viewModel::onChordTapped,
                onLongPress = viewModel::onChordLongPressed,
                upcomingChordId = state.upcomingChordId,
                modifier = mod,
            )
        }
        if (state.leftHanded) chords(Modifier.width(104.dp).fillMaxHeight())
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            GuitarSurface(
                chord = state.selectedChord,
                freeFret = false,
                leftHanded = state.leftHanded,
                vibrations = if (state.stringVibration) vibrations else null,
                listener = listener,
                modifier = Modifier.fillMaxSize(),
                overlay = overlay@{ layout ->
                    // Reading the frame time here (draw phase) redraws every frame without recomposing.
                    if (frameNanos < 0L) return@overlay
                    viewModel.session?.let { drawNoteMarkers(layout, it, it.nowMs(), textMeasurer, state.leftHanded, arrow) }
                },
            )
            val layout = GuitarLayout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            for (text in floating) {
                key(text.id) { FloatingFeedback(text, layout, state.leftHanded) { viewModel.dismissFloating(text.id) } }
            }
            Hud(state, onPause = viewModel::pause, modifier = Modifier.fillMaxWidth())
            state.countdown?.let { Countdown(it, Modifier.align(Alignment.Center)) }
        }
        if (!state.leftHanded) chords(Modifier.width(104.dp).fillMaxHeight())
    }

    if (state.phase == GamePhase.PAUSED) {
        PauseMenu(onResume = viewModel::resume, onRestart = viewModel::restart, onQuit = viewModel::quit)
    }
    if (state.phase == GamePhase.ERROR) {
        AlertDialog(
            onDismissRequest = onQuitToSongs,
            title = { Text("Can't play this song") },
            text = { Text(state.error ?: "Unknown error") },
            confirmButton = { TextButton(onClick = onQuitToSongs) { Text("Back to songs") } },
        )
    }
    state.pickerSlot?.let { slot ->
        ChordPickerDialog(state.slots[slot].id, viewModel::onChordPicked, viewModel::onPickerDismissed)
    }
}

@Composable
private fun Hud(state: GameUiState, onPause: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = StrumColors.Lagoon,
            trackColor = Color(0x33000000),
        )
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onPause, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(50))) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = StrumColors.Cream)
            }
            Pill {
                Text(state.title, color = StrumColors.Cream, style = MaterialTheme.typography.titleSmall)
                state.practiceLabel?.let { Text("  $it", color = StrumColors.CreamMuted, style = MaterialTheme.typography.labelSmall) }
            }
            Box(Modifier.weight(1f))
            Pill {
                Text(
                    String.format(Locale.US, "%,d", state.score),
                    color = StrumColors.Cream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Pill {
                Text("${state.combo} combo", color = StrumColors.Cream, fontSize = 14.sp)
                if (state.multiplier > 1) {
                    Text("  x${state.multiplier}", color = StrumColors.Ember, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier.background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Countdown(beats: Int, modifier: Modifier) {
    Text(
        beats.toString(),
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(50))
            .padding(horizontal = 36.dp, vertical = 8.dp),
        color = StrumColors.Gold,
        fontSize = 72.sp,
        fontWeight = FontWeight.Black,
    )
}

/** Text that rises and fades over 700 ms, then removes itself. */
@Composable
private fun FloatingFeedback(text: FloatingText, layout: GuitarLayout, leftHanded: Boolean, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        onDone()
    }
    val color = when (text.kind) {
        FloatingText.Kind.PERFECT -> StrumColors.Gold
        FloatingText.Kind.GOOD -> StrumColors.Lagoon
        FloatingText.Kind.MISS -> StrumColors.Miss
        FloatingText.Kind.MULTIPLIER -> StrumColors.Ember
    }
    val baseX = layout.mirrorX(layout.hitLineX, leftHanded)
    val baseY = text.string?.let { layout.stringYs[it] } ?: (layout.height * if (text.kind == FloatingText.Kind.MULTIPLIER) 0.32f else 0.45f)
    Text(
        text.text,
        color = color,
        fontSize = if (text.kind == FloatingText.Kind.MULTIPLIER) 34.sp else 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .offset { IntOffset((baseX - 50.dp.toPx()).toInt(), (baseY - 60.dp.toPx() * progress.value).toInt()) }
            .graphicsLayer {
                alpha = 1f - progress.value * progress.value
                val s = 0.8f + 0.4f * progress.value
                scaleX = s
                scaleY = s
            },
    )
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text("Paused") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Restart") }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text("End song") }
            }
        },
        confirmButton = {},
    )
}
