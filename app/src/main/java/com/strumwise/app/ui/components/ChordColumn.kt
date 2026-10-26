package com.strumwise.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strumwise.app.ui.theme.StrumColors
import com.strumwise.music.Chord

/**
 * The column of chord buttons beside the guitar. Tap selects (like holding the shape), tap again
 * releases, long-press swaps the chord in that slot. In game mode, [upcomingChordId] pulses.
 */
@Composable
fun ChordColumn(
    slots: List<Chord>,
    selectedSlot: Int?,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    upcomingChordId: String? = null,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(StrumColors.Espresso)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        slots.forEachIndexed { index, chord ->
            ChordButton(
                chord = chord,
                selected = index == selectedSlot,
                upcoming = chord.id == upcomingChordId,
                onTap = { onTap(index) },
                onLongPress = { onLongPress(index) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChordButton(
    chord: Chord,
    selected: Boolean,
    upcoming: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) StrumColors.Lagoon else StrumColors.EspressoHigh, label = "chordBg",
    )
    val content = if (selected) StrumColors.Espresso else StrumColors.Cream
    val pulse = if (upcoming) {
        rememberInfiniteTransition(label = "upcoming").animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "pulse",
        ).value
    } else {
        1f
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(if (upcoming) 3.dp else 1.dp, if (upcoming) StrumColors.Gold.copy(alpha = pulse) else Color(0x33FFFFFF), shape)
            .semantics {
                contentDescription = "Chord ${chord.displayName}"
                stateDescription = buildString {
                    append(if (selected) "selected" else "not selected")
                    if (upcoming) append(", next in song")
                }
            }
            .combinedClickable(
                onClickLabel = if (selected) "Release chord" else "Hold chord",
                onLongClickLabel = "Swap chord",
                role = Role.Button,
                onLongClick = onLongPress,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                chord.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
            Text(chord.shape(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = content.copy(alpha = 0.7f))
        }
    }
}
