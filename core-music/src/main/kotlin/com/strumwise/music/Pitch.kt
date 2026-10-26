package com.strumwise.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Number of strings on a standard guitar. Strings are indexed 0 (lowest) to 5 (highest). */
const val STRING_COUNT = 6

/** Highest fret the app supports. */
const val MAX_FRET = 24

/** Equal-temperament pitch helpers with A4 = 440 Hz (MIDI 69). */
object Pitch {
    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    const val A4_MIDI = 69
    const val A4_HZ = 440.0

    fun midiToHz(midi: Int): Double = A4_HZ * 2.0.pow((midi - A4_MIDI) / 12.0)

    /** Nearest MIDI note to a frequency. */
    fun hzToMidi(hz: Double): Int = (A4_MIDI + 12 * log2(hz / A4_HZ)).roundToInt()

    /** 0 = C, 1 = C#, ... 11 = B. */
    fun pitchClass(midi: Int): Int = Math.floorMod(midi, 12)

    /** Scientific pitch notation, e.g. 40 -> "E2", 61 -> "C#4". */
    fun name(midi: Int): String = NAMES[pitchClass(midi)] + (Math.floorDiv(midi, 12) - 1)

    fun pitchClassName(pitchClass: Int): String = NAMES[Math.floorMod(pitchClass, 12)]

    /** Parses "E2", "C#4", "Bb3" into a MIDI note number, or null if malformed. */
    fun parse(name: String): Int? {
        val match = Regex("^([A-Ga-g])([#b]?)(-?\\d)$").matchEntire(name.trim()) ?: return null
        val (letter, accidental, octave) = match.destructured
        val base = NAMES.indexOf(letter.uppercase())
        val offset = when (accidental) {
            "#" -> 1
            "b" -> -1
            else -> 0
        }
        return 12 * (octave.toInt() + 1) + base + offset
    }
}
