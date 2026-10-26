package com.strumwise.app.ui.results

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strumwise.app.nav.ResultsRoute
import com.strumwise.app.ui.theme.StrumColors
import java.util.Locale
import kotlin.random.Random

@Composable
fun ResultsScreen(result: ResultsRoute, songTitle: String, onRetry: () -> Unit, onSongs: () -> Unit) {
    val scoreAnim = remember { Animatable(0f) }
    LaunchedEffect(result) { scoreAnim.animateTo(1f, tween(1200, easing = LinearEasing)) }

    Box(Modifier.fillMaxSize().background(StrumColors.Espresso)) {
        if (result.newHighScore) Confetti(Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(songTitle, style = MaterialTheme.typography.headlineMedium, color = StrumColors.Cream)
                if (result.practice) {
                    Text("Practice run · ${(result.speed * 100).toInt()}% — not saved as a best", color = StrumColors.CreamMuted)
                }
                Stars(result.stars)
                Text(
                    String.format(Locale.US, "%,d", (result.score * scoreAnim.value).toLong()),
                    style = MaterialTheme.typography.displayMedium,
                    color = StrumColors.Cream,
                    modifier = Modifier.semantics { contentDescription = "Score ${result.score}" },
                )
                if (result.newHighScore) {
                    Text(
                        "New high score!",
                        color = StrumColors.Espresso,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.background(StrumColors.Gold, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Accuracy", String.format(Locale.US, "%.1f%%", result.accuracy))
                Stat("Max combo", result.maxCombo.toString())
                Stat("Perfect", result.perfect.toString(), StrumColors.Gold)
                Stat("Good", result.good.toString(), StrumColors.Lagoon)
                Stat("Miss", result.miss.toString(), StrumColors.Miss)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onRetry) { Text("Play again") }
                    OutlinedButton(onClick = onSongs) { Text("Song list") }
                }
            }
        }
    }
}

@Composable
private fun Stars(count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "$count of 3 stars" },
    ) {
        for (i in 0 until 3) {
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(250L + i * 250L)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            Icon(
                if (i < count) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i < count) StrumColors.Gold else StrumColors.CreamMuted,
                modifier = Modifier.size(56.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color = StrumColors.Cream) {
    Row(
        Modifier.background(StrumColors.EspressoRaised, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StrumColors.CreamMuted, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Celebration for a new high score: paper pieces falling and tumbling. */
@Composable
private fun Confetti(modifier: Modifier) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(4500, easing = LinearEasing)) }
    val pieces = remember {
        val random = Random(42)
        val colors = listOf(StrumColors.Gold, StrumColors.Lagoon, StrumColors.Ember, StrumColors.Cream)
        List(90) {
            Piece(random.nextFloat(), random.nextFloat() * 0.6f, 0.25f + random.nextFloat() * 0.5f,
                random.nextFloat() * 360f, 180f + random.nextFloat() * 540f, colors[it % colors.size])
        }
    }
    Canvas(modifier) {
        val t = time.value
        for (p in pieces) {
            val y = (-0.1f - p.delay + t * (1.4f + p.speed)) * size.height
            if (y < -20f || y > size.height + 20f) continue
            val x = (p.x + 0.04f * kotlin.math.sin((t * 12f + p.x * 6f).toDouble()).toFloat()) * size.width
            rotate(p.spin0 + p.spin * t, Offset(x, y)) {
                drawRect(p.color, Offset(x - 5f, y - 3f), Size(10f, 6f))
            }
        }
    }
}

private data class Piece(val x: Float, val delay: Float, val speed: Float, val spin0: Float, val spin: Float, val color: androidx.compose.ui.graphics.Color)
