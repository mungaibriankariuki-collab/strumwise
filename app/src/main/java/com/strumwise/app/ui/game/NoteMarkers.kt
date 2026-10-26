package com.strumwise.app.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.strumwise.app.ui.guitar.GuitarLayout
import com.strumwise.app.ui.theme.StrumColors
import com.strumwise.game.Direction
import com.strumwise.game.EventType
import com.strumwise.game.GameSession
import com.strumwise.game.Judgment
import com.strumwise.music.ChordLibrary

/** How far ahead (real time) markers appear: they travel from the nut to the hit line in this time. */
const val LOOKAHEAD_MS = 2200.0

/**
 * Draws the hit line and the note markers sliding along the strings toward it. Called from the
 * guitar canvas's draw phase every frame; allocation-light (one reusable Path).
 */
fun DrawScope.drawNoteMarkers(
    layout: GuitarLayout,
    session: GameSession,
    nowMs: Double,
    textMeasurer: TextMeasurer,
    leftHanded: Boolean,
    arrow: Path,
) {
    val speed = session.practice.speed
    val travel = layout.hitLineX - layout.nutX
    val top = layout.stringTop - layout.stringSpacing * 0.6f
    val bottom = layout.stringBottom + layout.stringSpacing * 0.6f
    val d = density

    // Hit line.
    drawLine(StrumColors.Lagoon.copy(alpha = 0.25f), Offset(layout.hitLineX, top), Offset(layout.hitLineX, bottom), 14f * d)
    drawLine(StrumColors.Lagoon, Offset(layout.hitLineX, top), Offset(layout.hitLineX, bottom), 3f * d)

    session.forEachVisibleNote(nowMs, LOOKAHEAD_MS * speed, behindMs = 300.0 * speed) { note ->
        val result = session.resultOf(note)
        if (result == Judgment.PERFECT || result == Judgment.GOOD) return@forEachVisibleNote  // hit notes vanish
        val dtReal = (note.timeMs - nowMs) / speed
        val x = (layout.hitLineX - dtReal / LOOKAHEAD_MS * travel).toFloat()
        if (x < layout.nutX - 20 * d) return@forEachVisibleNote
        // Fade in as markers come over the nut.
        val fade = ((x - layout.nutX) / (travel * 0.15f)).coerceIn(0f, 1f)
        val missed = result == Judgment.MISS
        val alpha = if (missed) 0.35f else fade
        val event = note.event
        when (event.type) {
            EventType.STRUM -> {
                val chord = event.chord?.let(ChordLibrary::get)
                val strings = chord?.playedStrings ?: (0 until 6).toList()
                val y0 = layout.stringYs[strings.first()] - layout.stringSpacing * 0.35f
                val y1 = layout.stringYs[strings.last()] + layout.stringSpacing * 0.35f
                val w = 14f * d
                val color = if (missed) StrumColors.Miss else StrumColors.Lagoon
                drawRoundRect(Color.Black.copy(alpha = 0.3f * alpha), Offset(x - w / 2 + 2 * d, y0 + 3 * d), Size(w, y1 - y0), CornerRadius(w / 2))
                drawRoundRect(color.copy(alpha = alpha), Offset(x - w / 2, y0), Size(w, y1 - y0), CornerRadius(w / 2))
                drawRoundRect(Color.White.copy(alpha = 0.6f * alpha), Offset(x - w / 2, y0), Size(w, y1 - y0), CornerRadius(w / 2), style = Stroke(1.5f * d))
                // Direction arrow at the end the strum travels toward.
                val down = event.direction != Direction.UP
                val tipY = if (down) y1 + 10 * d else y0 - 10 * d
                val baseY = if (down) y1 + 1 * d else y0 - 1 * d
                arrow.reset()
                arrow.moveTo(x - 8 * d, baseY)
                arrow.lineTo(x + 8 * d, baseY)
                arrow.lineTo(x, tipY)
                arrow.close()
                drawPath(arrow, color.copy(alpha = alpha))
                // Chord name above the marker (mirrored back so it reads correctly in left-handed mode).
                event.chord?.let { name ->
                    val text = textMeasurer.measure(name, TextStyle(color = StrumColors.Cream.copy(alpha = alpha), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    val tx = x - text.size.width / 2f
                    val ty = y0 - text.size.height - 12 * d
                    if (leftHanded) {
                        drawContext.transform.scale(-1f, 1f, Offset(x, ty))
                        drawText(text, topLeft = Offset(tx, ty))
                        drawContext.transform.scale(-1f, 1f, Offset(x, ty))
                    } else {
                        drawText(text, topLeft = Offset(tx, ty))
                    }
                }
            }
            EventType.PLUCK -> {
                val s = event.string ?: return@forEachVisibleNote
                val y = layout.stringYs[s]
                val r = layout.stringSpacing * 0.32f
                val color = if (missed) StrumColors.Miss else StrumColors.Ember
                drawCircle(Color.Black.copy(alpha = 0.3f * alpha), r, Offset(x + 2 * d, y + 3 * d))
                drawCircle(color.copy(alpha = alpha), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.7f * alpha), r, Offset(x, y), style = Stroke(1.5f * d))
            }
        }
    }
}
