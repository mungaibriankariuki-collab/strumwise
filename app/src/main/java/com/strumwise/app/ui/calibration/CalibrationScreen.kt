package com.strumwise.app.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.ui.theme.StrumColors
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(onBack: () -> Unit, viewModel: CalibrationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var beat by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(state.running) {
        while (state.running) withFrameNanos { beat = viewModel.beatPhase() ?: -1f }
        beat = -1f
    }

    Row(Modifier.fillMaxSize().background(StrumColors.Espresso).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StrumColors.Cream) }
                Text("Latency calibration", style = MaterialTheme.typography.headlineMedium, color = StrumColors.Cream)
            }
            Text(
                "Press Start, listen to the clicks and tap the pad exactly on each click. After a one-bar " +
                    "lead-in, ${CalibrationViewModel.COUNTED} clicks are measured. Headphones change latency, so " +
                    "calibrate with the setup you play on.",
                color = StrumColors.CreamMuted,
            )
            Text("Current offset: ${state.savedOffsetMs} ms", color = StrumColors.Cream, fontWeight = FontWeight.Bold)
            when {
                state.running -> Text("Taps measured: ${state.tapsCounted}", color = StrumColors.Lagoon)
                state.failed -> Text("Not enough steady taps — try again.", color = StrumColors.Miss)
                state.measuredOffsetMs != null -> {
                    val offset = state.measuredOffsetMs!!
                    Text(
                        "Measured: $offset ms " + if (offset >= 0) "(you tap late)" else "(you tap early)",
                        color = StrumColors.Gold,
                    )
                    state.consistencyMs?.let { Text("Steadiness: ±${it.roundToInt()} ms", color = StrumColors.CreamMuted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::start, enabled = !state.running) { Text(if (state.measuredOffsetMs == null) "Start" else "Again") }
                Button(onClick = viewModel::save, enabled = !state.running && state.measuredOffsetMs != null) { Text("Save") }
                OutlinedButton(onClick = viewModel::reset, enabled = !state.running) { Text("Reset to 0") }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(StrumColors.EspressoRaised, RoundedCornerShape(24.dp))
                .semantics { contentDescription = "Tap pad. Tap on every click." }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.filter { it.pressed && !it.previousPressed }.forEach {
                                viewModel.onTap(it.uptimeMillis)
                                it.consume()
                            }
                        }
                    }
                }
                .drawBehind {
                    if (beat >= 0f) {
                        // Beat light: flashes on each click and fades through the beat.
                        val glow = (1f - beat).let { it * it }
                        drawCircle(StrumColors.Lagoon.copy(alpha = 0.15f + 0.6f * glow), size.minDimension * (0.25f + 0.1f * glow), Offset(size.width / 2, size.height / 2))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.running) "TAP" else "Tap pad",
                color = StrumColors.Cream,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
