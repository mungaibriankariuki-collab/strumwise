package com.strumwise.music

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

class ChordLibraryTest {
    private fun pcs(vararg names: String) = names.map { n ->
        Pitch.pitchClass(Pitch.parse("${n}4")!!)
    }.toSet()

    /** Chord id -> (root, exact set of pitch classes the voicing must contain). */
    private val expected = mapOf(
        "C" to ("C" to pcs("C", "E", "G")),
        "D" to ("D" to pcs("D", "F#", "A")),
        "E" to ("E" to pcs("E", "G#", "B")),
        "F" to ("F" to pcs("F", "A", "C")),
        "G" to ("G" to pcs("G", "B", "D")),
        "A" to ("A" to pcs("A", "C#", "E")),
        "Am" to ("A" to pcs("A", "C", "E")),
        "Dm" to ("D" to pcs("D", "F", "A")),
        "Em" to ("E" to pcs("E", "G", "B")),
        "A7" to ("A" to pcs("A", "C#", "E", "G")),
        "D7" to ("D" to pcs("D", "F#", "A", "C")),
        "E7" to ("E" to pcs("E", "G#", "B", "D")),
        "G7" to ("G" to pcs("G", "B", "D", "F")),
        // The common open C7 shape (x32310) omits the fifth (G), as most open seventh voicings can.
        "C7" to ("C" to pcs("C", "E", "A#")),
        "B7" to ("B" to pcs("B", "D#", "F#", "A")),
        "Fmaj7" to ("F" to pcs("F", "A", "C", "E")),
        "Cadd9" to ("C" to pcs("C", "E", "G", "D")),
        "Dsus2" to ("D" to pcs("D", "E", "A")),
        "Dsus4" to ("D" to pcs("D", "G", "A")),
        "Asus2" to ("A" to pcs("A", "B", "E")),
        "F#m" to ("F#" to pcs("F#", "A", "C#")),
        "Bm" to ("B" to pcs("B", "D", "F#")),
        "Bb" to ("A#" to pcs("A#", "D", "F")),
        "Fbarre" to ("F" to pcs("F", "A", "C")),
    )

    @Test fun libraryHasEveryRequiredChord() {
        assertThat(ChordLibrary.ALL.map { it.id }).containsExactlyElementsIn(expected.keys)
    }

    @Test fun idsAreUnique() {
        val ids = ChordLibrary.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test fun everyChordContainsExactlyItsChordTones() {
        for (chord in ChordLibrary.ALL) {
            val (_, tones) = expected.getValue(chord.id)
            assertWithMessage(chord.id).that(chord.pitchClasses()).isEqualTo(tones)
        }
    }

    @Test fun everyChordHasItsRootInTheBass() {
        for (chord in ChordLibrary.ALL) {
            val (root, _) = expected.getValue(chord.id)
            val bass = chord.midiNotes().first()
            assertWithMessage(chord.id).that(Pitch.pitchClassName(Pitch.pitchClass(bass))).isEqualTo(root)
        }
    }

    @Test fun everyChordSoundsAtLeastFourStrings() {
        for (chord in ChordLibrary.ALL) assertWithMessage(chord.id).that(chord.playedStrings.size).isAtLeast(4)
    }

    @Test fun mutedStringsAreReported() {
        val c = ChordLibrary.getValue("C")
        assertThat(c.isMuted(0)).isTrue()
        assertThat((1..5).none { c.isMuted(it) }).isTrue()
        assertThat(ChordLibrary.getValue("D").playedStrings).containsExactly(2, 3, 4, 5).inOrder()
    }

    @Test fun exactShapes() {
        assertThat(ChordLibrary.getValue("G").frets).containsExactly(3, 2, 0, 0, 0, 3).inOrder()
        assertThat(ChordLibrary.getValue("Bm").frets).containsExactly(null, 2, 4, 4, 3, 2).inOrder()
        assertThat(ChordLibrary.getValue("Fbarre").frets).containsExactly(1, 3, 3, 2, 1, 1).inOrder()
        assertThat(ChordLibrary.getValue("F#m").shape()).isEqualTo("244222")
    }

    @Test fun midiNotesOfC() {
        // x32010 -> C3 E3 G3 C4 E4
        assertThat(ChordLibrary.getValue("C").midiNotes()).containsExactly(48, 52, 55, 60, 64).inOrder()
    }

    @Test fun baseFret() {
        assertThat(ChordLibrary.getValue("Bm").baseFret).isEqualTo(2)
        assertThat(ChordLibrary.getValue("Em").baseFret).isEqualTo(2)
        assertThat(ChordLibrary.getValue("Bb").baseFret).isEqualTo(1)
    }

    @Test fun lookup() {
        assertThat(ChordLibrary["Am"]?.displayName).isEqualTo("Am")
        assertThat(ChordLibrary["H7"]).isNull()
        assertThrows(IllegalArgumentException::class.java) { ChordLibrary.getValue("H7") }
    }

    @Test fun defaultSlotsAreAllInTheLibrary() {
        assertThat(ChordLibrary.DEFAULT_SLOTS.size).isAtLeast(6)
        assertThat(ChordLibrary.DEFAULT_SLOTS.size).isAtMost(8)
        for (id in ChordLibrary.DEFAULT_SLOTS) assertWithMessage(id).that(ChordLibrary[id]).isNotNull()
    }

    @Test fun shapeRoundTrip() {
        for (chord in ChordLibrary.ALL) {
            assertThat(Chord.fromShape(chord.id, chord.displayName, chord.shape())).isEqualTo(chord)
        }
    }

    @Test fun invalidChordsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Chord.fromShape("bad", "bad", "x3201") }
        assertThrows(IllegalArgumentException::class.java) { Chord.fromShape("none", "none", "xxxxxx") }
        assertThrows(IllegalArgumentException::class.java) { Chord("hi", "hi", listOf(0, 0, 0, 0, 0, 25)) }
    }
}
