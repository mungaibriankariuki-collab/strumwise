package com.strumwise.app.ui.guitar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.strumwise.app.ui.theme.StrumColors
import com.strumwise.music.Chord
import com.strumwise.music.Fretboard
import kotlin.random.Random

/**
 * Everything static about the guitar, built once per size inside drawWithCache: wood grain paths,
 * gradients and the rosette. Drawing it is just replaying these objects.
 */
class GuitarArt(val layout: GuitarLayout, private val density: Float) {
    private val w = layout.width
    private val h = layout.height

    private val topBrush = Brush.linearGradient(
        0f to StrumColors.SpruceLight, 0.55f to StrumColors.SpruceMid, 1f to StrumColors.SpruceDark,
        start = Offset(0f, 0f), end = Offset(w * 0.4f, h * 1.3f),
    )

    /** Soft light pool around the sound hole so the top doesn't look flat. */
    private val glowBrush = Brush.radialGradient(
        listOf(Color(0x33FFF3D6), Color.Transparent),
        center = Offset(layout.soundHoleX, h * 0.5f), radius = w * 0.45f,
    )

    /** Spruce grain: long, gently wandering near-horizontal lines with seeded randomness. */
    private val grain: List<Pair<Path, Float>> = run {
        val random = Random(1907)
        List(70) {
            val y0 = random.nextFloat() * h
            val path = Path().apply {
                moveTo(0f, y0)
                var x = 0f
                var y = y0
                while (x < w) {
                    val nx = x + w * (0.08f + random.nextFloat() * 0.1f)
                    val ny = y + (random.nextFloat() - 0.5f) * h * 0.02f
                    quadraticTo((x + nx) / 2f, y + (random.nextFloat() - 0.5f) * h * 0.03f, nx, ny)
                    x = nx
                    y = ny
                }
            }
            path to (0.04f + random.nextFloat() * 0.09f)
        }
    }

    private val boardTop = layout.stringTop - layout.stringSpacing * 0.7f
    private val boardBottom = layout.stringBottom + layout.stringSpacing * 0.7f
    private val boardBrush = Brush.verticalGradient(
        0f to StrumColors.RosewoodLight, 0.5f to StrumColors.RosewoodDark, 1f to StrumColors.RosewoodLight,
        startY = boardTop, endY = boardBottom,
    )
    private val holeBrush = Brush.radialGradient(
        listOf(Color(0xFF050302), Color(0xFF1C0F08)),
        center = Offset(layout.soundHoleX, h / 2f), radius = layout.soundHoleRadius,
    )

    /** Rosette mosaic: a ring of alternating tiles in teal, cream and walnut. */
    private val rosetteTiles = 56
    private val tileColors = listOf(StrumColors.LagoonDeep, StrumColors.Bone, Color(0xFF5B3A22), StrumColors.Bone)

    private val bridgeLeft = layout.bridgeX - layout.stringSpacing * 0.9f
    private val stringWidths = floatArrayOf(3.1f, 2.6f, 2.1f, 1.7f, 1.2f, 0.95f).map { it * density }

    fun drawBody(scope: DrawScope) = with(scope) {
        drawRect(topBrush)
        for ((path, alpha) in grain) drawPath(path, StrumColors.Grain.copy(alpha = alpha), style = Stroke(width = density))
        drawRect(glowBrush)
        // Binding along the top and bottom edges suggests the body's outline.
        drawRect(StrumColors.Binding, size = Size(w, h * 0.025f))
        drawRect(StrumColors.Binding, topLeft = Offset(0f, h * 0.975f), size = Size(w, h * 0.025f))
    }

    fun drawSoundHole(scope: DrawScope) = with(scope) {
        val c = Offset(layout.soundHoleX, h / 2f)
        val r = layout.soundHoleRadius
        val ring = r * 0.16f
        // Outer purfling lines.
        drawCircle(StrumColors.Binding, r + ring * 1.55f, c, style = Stroke(width = density * 1.5f))
        drawCircle(StrumColors.Binding, r + ring * 1.3f, c, style = Stroke(width = density))
        // Mosaic ring.
        val sweep = 360f / rosetteTiles
        for (i in 0 until rosetteTiles) {
            drawArc(
                color = tileColors[i % tileColors.size],
                startAngle = i * sweep, sweepAngle = sweep * 0.82f, useCenter = false,
                topLeft = Offset(c.x - (r + ring * 0.75f), c.y - (r + ring * 0.75f)),
                size = Size((r + ring * 0.75f) * 2, (r + ring * 0.75f) * 2),
                style = Stroke(width = ring * 0.8f),
            )
        }
        drawCircle(StrumColors.Binding, r + ring * 0.25f, c, style = Stroke(width = density * 2f))
        drawCircle(holeBrush, r, c)
    }

    fun drawFretboard(scope: DrawScope, chord: Chord?, highlightFrets: Boolean) = with(scope) {
        drawRect(boardBrush, topLeft = Offset(0f, boardTop), size = Size(layout.fretboardEndX, boardBottom - boardTop))
        // Inlay dots.
        val midY = (boardTop + boardBottom) / 2f
        for (f in 1..GuitarLayout.VISIBLE_FRETS) {
            if (f !in Fretboard.INLAY_FRETS) continue
            val x = layout.fretCenterX(f)
            val r = layout.stringSpacing * 0.16f
            if (f in Fretboard.DOUBLE_INLAY_FRETS) {
                drawCircle(StrumColors.Pearl, r, Offset(x, midY - layout.stringSpacing * 1.5f))
                drawCircle(StrumColors.Pearl, r, Offset(x, midY + layout.stringSpacing * 1.5f))
            } else {
                drawCircle(StrumColors.Pearl, r, Offset(x, midY))
            }
        }
        // Fret wires with a one-pixel highlight.
        for (f in 1..GuitarLayout.VISIBLE_FRETS) {
            val x = layout.fretXs[f]
            drawLine(StrumColors.FretWire, Offset(x, boardTop), Offset(x, boardBottom), strokeWidth = 3f * density)
            drawLine(Color.White.copy(alpha = 0.6f), Offset(x - density, boardTop), Offset(x - density, boardBottom), strokeWidth = density)
        }
        // Nut.
        drawRect(StrumColors.Bone, topLeft = Offset(layout.nutX - 4f * density, boardTop), size = Size(6f * density, boardBottom - boardTop))
        // Free-fret hint: faint lane lines make the tap targets readable.
        if (highlightFrets) {
            for (f in 1..GuitarLayout.VISIBLE_FRETS) {
                drawRect(
                    StrumColors.Lagoon.copy(alpha = if (f % 2 == 0) 0.06f else 0.1f),
                    topLeft = Offset(layout.fretXs[f - 1], boardTop),
                    size = Size(layout.fretXs[f] - layout.fretXs[f - 1], boardBottom - boardTop),
                )
            }
        }
        if (chord != null) drawChordShape(this, chord)
    }

    /** Finger dots for fretted strings, an open ring for open strings and an X for muted ones. */
    private fun drawChordShape(scope: DrawScope, chord: Chord) = with(scope) {
        val r = layout.stringSpacing * 0.3f
        val markX = layout.nutX * 0.5f + 2f * density
        for (s in 0 until 6) {
            val y = layout.stringYs[s]
            when (val fret = chord.frets[s]) {
                null -> {
                    val k = r * 0.6f
                    val stroke = 2.5f * density
                    drawLine(StrumColors.Miss, Offset(markX - k, y - k), Offset(markX + k, y + k), stroke, StrokeCap.Round)
                    drawLine(StrumColors.Miss, Offset(markX - k, y + k), Offset(markX + k, y - k), stroke, StrokeCap.Round)
                }
                0 -> drawCircle(StrumColors.Lagoon, r * 0.55f, Offset(markX, y), style = Stroke(width = 2f * density))
                else -> if (fret <= GuitarLayout.VISIBLE_FRETS) {
                    drawCircle(Color(0x55000000), r, Offset(layout.fretCenterX(fret) + density, y + 2 * density))
                    drawCircle(StrumColors.Lagoon, r, Offset(layout.fretCenterX(fret), y))
                    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.35f, Offset(layout.fretCenterX(fret) - r * 0.3f, y - r * 0.3f))
                }
            }
        }
    }

    fun drawBridge(scope: DrawScope) = with(scope) {
        val top = layout.stringTop - layout.stringSpacing * 0.9f
        val height = layout.stringBottom - layout.stringTop + layout.stringSpacing * 1.8f
        drawRoundRect(
            Brush.horizontalGradient(listOf(StrumColors.RosewoodLight, StrumColors.RosewoodDark), bridgeLeft, w),
            topLeft = Offset(bridgeLeft, top), size = Size(w * 0.97f - bridgeLeft, height),
            cornerRadius = CornerRadius(layout.stringSpacing * 0.5f),
        )
        // Saddle.
        drawRect(StrumColors.Bone, topLeft = Offset(layout.bridgeX - 2f * density, top + height * 0.06f), size = Size(4f * density, height * 0.88f))
        // Bridge pins.
        for (s in 0 until 6) {
            val c = Offset(layout.bridgeX + layout.stringSpacing * 0.55f, layout.stringYs[s])
            drawCircle(StrumColors.Bone, layout.stringSpacing * 0.12f, c)
            drawCircle(Color(0x66000000), layout.stringSpacing * 0.12f, c, style = Stroke(width = density))
        }
    }

    /**
     * The six strings. A vibrating string is drawn as a standing wave from its fret (or the nut) to
     * the saddle, with a translucent envelope that reads as motion blur.
     */
    fun drawStrings(
        scope: DrawScope,
        vibrations: StringVibrations?,
        now: Long,
        muted: (Int) -> Boolean,
        path: Path,
    ) = with(scope) {
        for (s in 0 until 6) {
            val y = layout.stringYs[s]
            val width = stringWidths[s]
            val base = if (s <= 3) StrumColors.BronzeString else StrumColors.SteelString
            val color = if (muted(s)) base.copy(alpha = 0.45f) else base
            val env = vibrations?.envelope(s, now) ?: 0f
            if (env == 0f) {
                drawLine(StrumColors.StringShadow, Offset(0f, y + width + density), Offset(layout.bridgeX, y + width + density), width)
                drawLine(color, Offset(0f, y), Offset(layout.bridgeX, y), width)
                continue
            }
            val fret = vibrations!!.fretOf(s)
            val startX = if (fret == 0) layout.nutX else layout.fretXs[fret.coerceAtMost(GuitarLayout.VISIBLE_FRETS)]
            val span = layout.bridgeX - startX
            val maxAmp = layout.stringSpacing * 0.22f
            // Static part behind the fret.
            drawLine(color, Offset(0f, y), Offset(startX, y), width)
            // Blur envelope.
            val glow = (StrumColors.Lagoon).copy(alpha = 0.18f * env)
            path.reset()
            path.moveTo(startX, y)
            for (i in 1..SEGMENTS) {
                val p = i / SEGMENTS.toFloat()
                path.lineTo(startX + span * p, y - maxAmp * env * kotlin.math.sin(Math.PI * p).toFloat())
            }
            for (i in SEGMENTS downTo 0) {
                val p = i / SEGMENTS.toFloat()
                path.lineTo(startX + span * p, y + maxAmp * env * kotlin.math.sin(Math.PI * p).toFloat())
            }
            path.close()
            drawPath(path, glow)
            // The string itself.
            path.reset()
            path.moveTo(startX, y)
            for (i in 1..SEGMENTS) {
                val p = i / SEGMENTS.toFloat()
                path.lineTo(startX + span * p, y + maxAmp * vibrations.displacement(s, p, now))
            }
            drawPath(path, color, style = Stroke(width = width))
        }
    }

    companion object {
        private const val SEGMENTS = 28
    }
}
