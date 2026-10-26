package com.strumwise.app.ui.guitar

import com.strumwise.music.Fretboard

/**
 * Geometry of the drawn guitar, in canvas pixels, for a given surface size. Shared by drawing and
 * hit-testing so they always agree. Drawn right-handed; left-handed mode mirrors the canvas and
 * the touch x coordinate (see [mirrorX]).
 *
 *   |nut|  fretboard (frets 1..VISIBLE_FRETS)  |   body / sound hole   | bridge |
 */
class GuitarLayout(val width: Float, val height: Float) {
    val nutX = width * 0.035f
    val fretboardEndX = width * 0.50f
    val soundHoleX = width * 0.66f
    val soundHoleRadius = minOf(height * 0.30f, width * 0.11f)
    val bridgeX = width * 0.90f
    val stringTop = height * 0.17f
    val stringBottom = height * 0.83f
    val stringSpacing = (stringBottom - stringTop) / 5f

    /** String 0 (low E) on top, as the player sees it looking down at the guitar. */
    val stringYs = FloatArray(6) { stringTop + it * stringSpacing }

    /** X of each fret wire, index 0 = the nut. Real (logarithmic) spacing. */
    val fretXs = FloatArray(VISIBLE_FRETS + 1) { f ->
        nutX + (fretboardEndX - nutX) * Fretboard.fretPositionInWindow(f, VISIBLE_FRETS).toFloat()
    }

    /** Where markers/hit line sit in game mode: just before the sound hole. */
    val hitLineX = soundHoleX - soundHoleRadius - width * 0.02f

    /** Fret under [x] (0 = open, at or left of the nut), or null if x is past the fretboard. */
    fun fretAt(x: Float): Int? {
        if (x > fretboardEndX) return null
        if (x <= nutX) return 0
        for (f in 1..VISIBLE_FRETS) if (x <= fretXs[f]) return f
        return null
    }

    /** Centre between two fret wires, where a finger dot is drawn. Fret 0 is drawn just left of the nut. */
    fun fretCenterX(fret: Int): Float =
        if (fret == 0) nutX * 0.5f else (fretXs[fret - 1] + fretXs[fret.coerceAtMost(VISIBLE_FRETS)]) / 2f

    fun isOnFretboard(x: Float): Boolean = x <= fretboardEndX

    fun mirrorX(x: Float, leftHanded: Boolean): Float = if (leftHanded) width - x else x

    companion object {
        /** Frets drawn on screen; free-fret mode can play these. */
        const val VISIBLE_FRETS = 7
    }
}
