package com.strumwise.music

/**
 * Built-in chord shapes, as data. Shapes are written low E string first; 'x' = muted.
 * All shapes are standard voicings for standard tuning.
 */
object ChordLibrary {
    val ALL: List<Chord> = listOf(
        // Major
        Chord.fromShape("C", "C", "x32010"),
        Chord.fromShape("D", "D", "xx0232"),
        Chord.fromShape("E", "E", "022100"),
        Chord.fromShape("F", "F", "xx3211"),
        Chord.fromShape("G", "G", "320003"),
        Chord.fromShape("A", "A", "x02220"),
        // Minor
        Chord.fromShape("Am", "Am", "x02210"),
        Chord.fromShape("Dm", "Dm", "xx0231"),
        Chord.fromShape("Em", "Em", "022000"),
        // Dominant sevenths
        Chord.fromShape("A7", "A7", "x02020"),
        Chord.fromShape("D7", "D7", "xx0212"),
        Chord.fromShape("E7", "E7", "020100"),
        Chord.fromShape("G7", "G7", "320001"),
        Chord.fromShape("C7", "C7", "x32310"),
        Chord.fromShape("B7", "B7", "x21202"),
        // Colour chords
        Chord.fromShape("Fmaj7", "Fmaj7", "xx3210"),
        Chord.fromShape("Cadd9", "Cadd9", "x32030"),
        Chord.fromShape("Dsus2", "Dsus2", "xx0230"),
        Chord.fromShape("Dsus4", "Dsus4", "xx0233"),
        Chord.fromShape("Asus2", "Asus2", "x02200"),
        // Barre chords
        Chord.fromShape("F#m", "F#m", "244222"),
        Chord.fromShape("Bm", "Bm", "x24432"),
        Chord.fromShape("Bb", "Bb", "x13331"),
        Chord.fromShape("Fbarre", "F (barre)", "133211"),
    )

    private val byId: Map<String, Chord> = ALL.associateBy { it.id }

    operator fun get(id: String): Chord? = byId[id]

    fun getValue(id: String): Chord = byId[id] ?: throw IllegalArgumentException("Unknown chord '$id'")

    /** The chord column a new player starts with (user-customizable). */
    val DEFAULT_SLOTS: List<String> = listOf("G", "C", "D", "Em", "Am", "E", "A", "F")
}
