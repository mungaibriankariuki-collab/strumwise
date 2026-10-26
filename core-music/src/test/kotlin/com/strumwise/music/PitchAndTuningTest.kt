package com.strumwise.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PitchAndTuningTest {
    @Test fun names() {
        assertThat(Pitch.name(40)).isEqualTo("E2")
        assertThat(Pitch.name(60)).isEqualTo("C4")
        assertThat(Pitch.name(61)).isEqualTo("C#4")
        assertThat(Pitch.name(69)).isEqualTo("A4")
    }

    @Test fun parseRoundTrips() {
        for (midi in 24..96) assertThat(Pitch.parse(Pitch.name(midi))).isEqualTo(midi)
        assertThat(Pitch.parse("Bb3")).isEqualTo(58)
        assertThat(Pitch.parse("H2")).isNull()
    }

    @Test fun hzToMidi() {
        assertThat(Pitch.hzToMidi(440.0)).isEqualTo(69)
        assertThat(Pitch.hzToMidi(82.41)).isEqualTo(40)
    }

    @Test fun tuningNotes() {
        assertThat(Tuning.STANDARD.openMidi.map(Pitch::name)).containsExactly("E2", "A2", "D3", "G3", "B3", "E4").inOrder()
        assertThat(Tuning.DROP_D.openMidi.map(Pitch::name)).containsExactly("D2", "A2", "D3", "G3", "B3", "E4").inOrder()
        assertThat(Tuning.OPEN_G.openMidi.map(Pitch::name)).containsExactly("D2", "G2", "D3", "G3", "B3", "D4").inOrder()
        assertThat(Tuning.HALF_STEP_DOWN.openMidi.map(Pitch::name))
            .containsExactly("D#2", "G#2", "C#3", "F#3", "A#3", "D#4").inOrder()
        assertThat(Tuning.STANDARD.noteNames()).isEqualTo("E A D G B E")
    }

    @Test fun openGStrumsAGMajorChord() {
        val pcs = Tuning.OPEN_G.openMidi.map(Pitch::pitchClass).toSet()
        assertThat(pcs).containsExactly(7, 11, 2) // G B D
    }

    @Test fun byIdFallsBackToStandard() {
        assertThat(Tuning.byId("drop_d")).isEqualTo(Tuning.DROP_D)
        assertThat(Tuning.byId("nope")).isEqualTo(Tuning.STANDARD)
        assertThat(Tuning.ALL.map { it.id }).containsNoDuplicates()
    }

    @Test fun fretPositions() {
        assertThat(Fretboard.fretPosition(0)).isEqualTo(0.0)
        assertThat(Fretboard.fretPosition(12)).isWithin(1e-12).of(0.5)
        assertThat(Fretboard.fretPosition(24)).isWithin(1e-12).of(0.75)
        assertThat(Fretboard.fretPositionInWindow(5, 5)).isWithin(1e-12).of(1.0)
        // Frets get closer together going up the neck.
        for (f in 1 until MAX_FRET) {
            val gap = Fretboard.fretPosition(f) - Fretboard.fretPosition(f - 1)
            val next = Fretboard.fretPosition(f + 1) - Fretboard.fretPosition(f)
            assertThat(next).isLessThan(gap)
        }
    }
}
