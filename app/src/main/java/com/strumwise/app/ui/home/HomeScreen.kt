package com.strumwise.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.strumwise.app.ui.theme.StrumColors

@Composable
fun HomeScreen(onPlay: () -> Unit, onSongs: () -> Unit, onSettings: () -> Unit, onDevMenu: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(StrumColors.EspressoHigh, StrumColors.Espresso)))
            .drawBehind {
                // Six faint strings sweeping across the background.
                for (i in 0 until 6) {
                    val y = size.height * (0.2f + i * 0.12f)
                    drawLine(StrumColors.SpruceMid.copy(alpha = 0.08f + i * 0.015f), Offset(0f, y), Offset(size.width, y - size.height * 0.08f), strokeWidth = (3 - i * 0.35f) * density)
                }
                drawCircle(StrumColors.Lagoon.copy(alpha = 0.12f), size.height * 0.42f, Offset(size.width * 0.82f, size.height * 0.5f), style = Stroke(10 * density))
            },
    ) {
        Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                var titleTaps by remember { mutableIntStateOf(0) }
                Text(
                    "Strumwise",
                    style = MaterialTheme.typography.displayLarge,
                    color = StrumColors.Cream,
                    modifier = Modifier
                        .semantics { heading() }
                        // Hidden developer menu: tap the title seven times (like Android's build number).
                        .pointerInput(Unit) {
                            detectTapGestures {
                                titleTaps++
                                if (titleTaps >= 7) {
                                    titleTaps = 0
                                    onDevMenu()
                                }
                            }
                        },
                )
                Text("Pick it up. Strum along.", style = MaterialTheme.typography.titleMedium, color = StrumColors.Lagoon)
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(260.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.height(56.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StrumColors.Lagoon, contentColor = StrumColors.Espresso),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Text("  Play guitar", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onSongs,
                    modifier = Modifier.height(56.dp).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StrumColors.Ember, contentColor = StrumColors.Espresso),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                    Text("  Songs", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.height(52.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = StrumColors.Cream)
                    Text("  Settings", color = StrumColors.Cream)
                }
            }
        }
    }
}
