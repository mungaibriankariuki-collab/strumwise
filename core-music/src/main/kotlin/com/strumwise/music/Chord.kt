package com.strumwise.music

/**
 * A chord shape: what each string does, low string first. `null` means the string is muted
 * (not played); a number is the fret (0 = open).
 *
 * [id] is the stable key used in song charts, settings and the database.
 */
data class Chord(val id: String, val displayName: String, val frets: List<Int?>) {
    init {
        require(frets.size == STRING_COUNT) { "Chord $id needs $STRING_COUNT entries" }
        require(frets.all { it == null || it in 0..MAX_FRET }) { "Chord $id has an invalid fret" }
        require(frets.any { it != null }) { "Chord $id mutes every string" }
    }

    fun isMuted(string: Int): Boolean = frets[string] == null

    /** Strings that sound, in low-to-high order. */
    val playedStrings: List<Int> get() = frets.indices.filter { frets[it] != null }

    /** MIDI notes of the sounding strings (low to high) in a tuning. */
    fun midiNotes(tuning: Tuning = Tuning.STANDARD): List<Int> =
        playedStrings.map { tuning.midiFor(it, frets[it]!!) }

    fun pitchClasses(tuning: Tuning = Tuning.STANDARD): Set<Int> =
        midiNotes(tuning).map(Pitch::pitchClass).toSet()

    /** Lowest fretted position, for drawing a "barre at fret N" hint. Open strings are ignored. */
    val baseFret: Int get() = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0

    /** Guitar-tab style shape, e.g. "x32010". Frets above 9 are written in parentheses. */
    fun shape(): String = frets.joinToString("") { f ->
        when {
            f == null -> "x"
            f > 9 -> "($f)"
            else -> f.toString()
        }
    }

    companion object {
        /** Parses "x32010" (one character per string, low first; 'x' = muted). */
        fun fromShape(id: String, displayName: String, shape: String): Chord {
            require(shape.length == STRING_COUNT) { "Shape '$shape' must have $STRING_COUNT characters" }
            return Chord(id, displayName, shape.map { c -> if (c == 'x' || c == 'X') null else c.digitToInt() })
        }
    }
}
