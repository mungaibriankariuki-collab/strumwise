package com.strumwise.app.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.ui.components.ChordColumn
import com.strumwise.app.ui.components.ChordPickerDialog
import com.strumwise.app.ui.guitar.GuitarSurface
import com.strumwise.app.ui.guitar.GuitarTouchListener
import com.strumwise.app.ui.guitar.PlayedNote
import com.strumwise.app.ui.guitar.StringHit
import com.strumwise.app.ui.guitar.StringVibrations
import com.strumwise.app.ui.guitar.tickForNotes
import com.strumwise.app.ui.theme.StrumColors

@Composable
fun PlayScreen(onBack: () -> Unit, viewModel: PlayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayContent(
        state = state,
        onBack = onBack,
        onChordTapped = viewModel::onChordTapped,
        onChordLongPressed = viewModel::onChordLongPressed,
        onToggleFreeFret = viewModel::toggleFreeFret,
        onStringHits = viewModel::onStringHits,
        onFretDown = viewModel::onFretDown,
        onFretUp = viewModel::onFretUp,
    )
    state.pickerSlot?.let { slot ->
        ChordPickerDialog(
            currentChordId = state.slots[slot].id,
            onPick = viewModel::onChordPicked,
            onDismiss = viewModel::onPickerDismissed,
        )
    }
}

/** Stateless play screen, so it can be previewed and UI-tested with a fake. */
@Composable
fun PlayContent(
    state: PlayUiState,
    onBack: () -> Unit,
    onChordTapped: (Int) -> Unit,
    onChordLongPressed: (Int) -> Unit,
    onToggleFreeFret: () -> Unit,
    onStringHits: (List<StringHit>) -> List<PlayedNote>,
    onFretDown: (Long, Int, Int) -> PlayedNote,
    onFretUp: (Long) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val vibrations = remember { StringVibrations() }

    fun feedback(notes: List<PlayedNote>) {
        if (state.stringVibration) notes.forEach { vibrations.pluck(it.string, it.fret, it.velocity, it.delayMs) }
        if (state.haptics) view.tickForNotes(notes, scope)
    }

    val listener = object : GuitarTouchListener {
        override fun onStringHits(hits: List<StringHit>) = feedback(onStringHits(hits))
        override fun onFretDown(pointerId: Long, string: Int, fret: Int) = feedback(listOf(onFretDown(pointerId, string, fret)))
        override fun onFretUp(pointerId: Long) = onFretUp(pointerId)
    }

    val guitar: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            GuitarSurface(
                chord = state.selectedChord,
                freeFret = state.freeFret,
                leftHanded = state.leftHanded,
                vibrations = if (state.stringVibration) vibrations else null,
                listener = listener,
                modifier = Modifier.fillMaxSize().testTag("guitar"),
            )
            PlayTopBar(state, onBack, onToggleFreeFret, Modifier.align(if (state.leftHanded) Alignment.TopEnd else Alignment.TopStart))
        }
    }
    val chords: @Composable (Modifier) -> Unit = { mod ->
        ChordColumn(
            slots = state.slots,
            selectedSlot = state.selectedSlot,
            onTap = onChordTapped,
            onLongPress = onChordLongPressed,
            modifier = mod,
        )
    }

    Row(Modifier.fillMaxSize().background(StrumColors.Espresso)) {
        // Left-handed mode mirrors the layout: chords on the left, neck on the right.
        if (state.leftHanded) {
            chords(Modifier.width(104.dp).fillMaxHeight())
            guitar(Modifier.weight(1f).fillMaxHeight())
        } else {
            guitar(Modifier.weight(1f).fillMaxHeight())
            chords(Modifier.width(104.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun PlayTopBar(state: PlayUiState, onBack: () -> Unit, onToggleFreeFret: () -> Unit, modifier: Modifier) {
    Row(
        modifier.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(50)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StrumColors.Cream)
        }
        FilterChip(
            selected = state.freeFret,
            onClick = onToggleFreeFret,
            label = { Text("Free fret") },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0x99000000),
                labelColor = StrumColors.Cream,
                selectedContainerColor = StrumColors.Lagoon,
                selectedLabelColor = StrumColors.Espresso,
            ),
        )
        val label = state.selectedChord?.displayName ?: if (state.freeFret) "Tap the frets" else "Open strings"
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = StrumColors.Cream,
            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
