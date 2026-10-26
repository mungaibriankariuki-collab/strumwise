package com.strumwise.music

/**
 * An open-string tuning, low string first. [openMidi] has exactly [STRING_COUNT] entries.
 * [id] is stable and safe to persist.
 */
data class Tuning(val id: String, val displayName: String, val openMidi: List<Int>) {
    init {
        require(openMidi.size == STRING_COUNT) { "A tuning needs $STRING_COUNT strings" }
    }

    fun midiFor(string: Int, fret: Int): Int {
        require(string in 0 until STRING_COUNT) { "string $string out of range" }
        require(fret in 0..MAX_FRET) { "fret $fret out of range" }
        return openMidi[string] + fret
    }

    /** Open-string frequencies in Hz, the form the audio engine wants. */
    fun openStringHz(): FloatArray = FloatArray(STRING_COUNT) { Pitch.midiToHz(openMidi[it]).toFloat() }

    /** e.g. "E A D G B E" */
    fun noteNames(): String = openMidi.joinToString(" ") { Pitch.pitchClassName(Pitch.pitchClass(it)) }

    companion object {
        val STANDARD = Tuning("standard", "Standard (E A D G B E)", listOf(40, 45, 50, 55, 59, 64))
        val DROP_D = Tuning("drop_d", "Drop D (D A D G B E)", listOf(38, 45, 50, 55, 59, 64))
        val OPEN_G = Tuning("open_g", "Open G (D G D G B D)", listOf(38, 43, 50, 55, 59, 62))
        val HALF_STEP_DOWN = Tuning("half_step_down", "Half-step down (Eb Ab Db Gb Bb Eb)", listOf(39, 44, 49, 54, 58, 63))

        val ALL = listOf(STANDARD, DROP_D, OPEN_G, HALF_STEP_DOWN)

        fun byId(id: String): Tuning = ALL.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** Frequency in Hz of [fret] on [string] (0 = lowest string) in [tuning]. */
fun frequencyFor(string: Int, fret: Int, tuning: Tuning = Tuning.STANDARD): Double =
    Pitch.midiToHz(tuning.midiFor(string, fret))
