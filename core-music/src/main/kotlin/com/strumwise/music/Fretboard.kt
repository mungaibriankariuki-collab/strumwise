package com.strumwise.music

import kotlin.math.pow

/** Geometry of a real fretboard, used to draw frets at their true (logarithmic) spacing. */
object Fretboard {
    /** Distance of [fret] from the nut as a fraction of the scale length (0 at the nut, 0.5 at fret 12). */
    fun fretPosition(fret: Int): Double {
        require(fret in 0..MAX_FRET) { "fret $fret out of range" }
        return 1.0 - 2.0.pow(-fret / 12.0)
    }

    /**
     * Normalised position (0..1) of [fret] within a visible window spanning frets 0..[visibleFrets],
     * so the last visible fret lands exactly at 1.0.
     */
    fun fretPositionInWindow(fret: Int, visibleFrets: Int): Double =
        fretPosition(fret) / fretPosition(visibleFrets)

    /** Frets that traditionally carry inlay dots (12 and 24 get double dots). */
    val INLAY_FRETS = setOf(3, 5, 7, 9, 12, 15, 17, 19, 21, 24)
    val DOUBLE_INLAY_FRETS = setOf(12, 24)
}
