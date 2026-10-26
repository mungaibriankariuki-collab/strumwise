package com.strumwise.app.ui.guitar

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.strumwise.music.Chord

/** Callbacks from touch on the guitar. Positions are already converted to strings and frets. */
interface GuitarTouchListener {
    /** Strings were plucked or strummed. */
    fun onStringHits(hits: List<StringHit>)

    /** Free-fret mode: a finger pressed [fret] on [string]. */
    fun onFretDown(pointerId: Long, string: Int, fret: Int)

    fun onFretUp(pointerId: Long)
}

/**
 * The playable guitar: drawn entirely in one canvas and driven by raw multi-touch.
 *
 * Performance: all static art is built in drawWithCache; the per-frame work only reads
 * [StringVibrations.frameNanos] in the draw phase, so string animation never recomposes.
 *
 * @param overlay extra drawing on top of the strings (game-mode note markers), in right-handed coordinates.
 */
@Composable
fun GuitarSurface(
    chord: Chord?,
    freeFret: Boolean,
    leftHanded: Boolean,
    vibrations: StringVibrations?,
    listener: GuitarTouchListener,
    modifier: Modifier = Modifier,
    overlay: (DrawScope.(GuitarLayout) -> Unit)? = null,
) {
    val currentListener by rememberUpdatedState(listener)
    val currentChord by rememberUpdatedState(chord)
    val currentOverlay by rememberUpdatedState(overlay)
    val detector = remember { StrumDetector() }

    // Animation loop: runs only while some string is still moving.
    if (vibrations != null) {
        LaunchedEffect(vibrations, vibrations.generation) {
            do {
                withFrameNanos { vibrations.frameNanos = it }
            } while (vibrations.anyActive(vibrations.frameNanos))
        }
    }

    Box(
        modifier
            .semantics { contentDescription = "Guitar strings. Swipe across to strum, tap a string to pluck." }
            .pointerInput(freeFret, leftHanded) {
                val fretPointers = HashSet<Long>()
                var layout = GuitarLayout(size.width.toFloat(), size.height.toFloat())
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (layout.width != size.width.toFloat() || layout.height != size.height.toFloat()) {
                            layout = GuitarLayout(size.width.toFloat(), size.height.toFloat())
                        }
                        detector.stringYs = layout.stringYs
                        detector.density = density
                        for (change in event.changes) {
                            val id = change.id.value
                            val x = layout.mirrorX(change.position.x, leftHanded)
                            val y = change.position.y
                            when {
                                change.pressed && !change.previousPressed -> {
                                    val nearest = layout.stringYs.indices.minBy { kotlin.math.abs(layout.stringYs[it] - y) }
                                    if (freeFret && layout.isOnFretboard(x)) {
                                        fretPointers += id
                                        currentListener.onFretDown(id, nearest, layout.fretAt(x) ?: 0)
                                    } else {
                                        val hits = detector.down(id, y, change.uptimeMillis)
                                        if (hits.isNotEmpty()) currentListener.onStringHits(hits)
                                    }
                                }
                                change.pressed -> if (id !in fretPointers) {
                                    // Feed every historical sample so fast swipes keep their exact path.
                                    val hits = ArrayList<StringHit>()
                                    for (h in change.historical) hits += detector.move(id, h.position.y, h.uptimeMillis)
                                    hits += detector.move(id, y, change.uptimeMillis)
                                    if (hits.isNotEmpty()) currentListener.onStringHits(respace(hits))
                                }
                                !change.pressed && change.previousPressed -> {
                                    if (fretPointers.remove(id)) currentListener.onFretUp(id) else detector.up(id)
                                }
                            }
                            change.consume()
                        }
                        if (event.type == PointerEventType.Exit && event.changes.none { it.pressed }) detector.cancelAll()
                    }
                }
            }
            .drawWithCache {
                val art = GuitarArt(GuitarLayout(size.width, size.height), density)
                val path = Path()
                onDrawBehind {
                    val now = vibrations?.frameNanos ?: 0L
                    val c = currentChord
                    val draw: DrawScope.() -> Unit = {
                        art.drawBody(this)
                        art.drawSoundHole(this)
                        art.drawFretboard(this, c, highlightFrets = freeFret)
                        art.drawBridge(this)
                        art.drawStrings(this, vibrations, now, { s -> c?.isMuted(s) == true }, path)
                        currentOverlay?.invoke(this, art.layout)
                    }
                    if (leftHanded) scale(-1f, 1f) { draw() } else draw()
                }
            },
    )
}

/**
 * Historical samples are processed one by one, so a fast swipe split over several of them yields
 * hits with delays restarting at 0. Re-space them as one continuous strum.
 */
internal fun respace(hits: List<StringHit>): List<StringHit> {
    if (hits.size < 2) return hits
    val step = hits.firstOrNull { it.delayMs > 0f }?.delayMs ?: 8f
    return hits.mapIndexed { i, h -> h.copy(delayMs = i * step) }
}
