package com.strumwise.music

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.pow

class FrequencyTest {
    private val tolerance = 0.01

    @Test fun standardOpenStrings() {
        val expected = listOf(82.41, 110.00, 146.83, 196.00, 246.94, 329.63)
        expected.forEachIndexed { string, hz ->
            assertThat(frequencyFor(string, 0, Tuning.STANDARD)).isWithin(tolerance).of(hz)
        }
    }

    @Test fun everyStringAndFretFollowsEqualTemperament() {
        for (tuning in Tuning.ALL) {
            for (string in 0 until STRING_COUNT) {
                val open = frequencyFor(string, 0, tuning)
                for (fret in 0..MAX_FRET) {
                    assertThat(frequencyFor(string, fret, tuning))
                        .isWithin(1e-9 * open * 4)
                        .of(open * 2.0.pow(fret / 12.0))
                }
            }
        }
    }

    @Test fun twelfthFretIsAnOctave() {
        for (string in 0 until STRING_COUNT) {
            assertThat(frequencyFor(string, 12) / frequencyFor(string, 0)).isWithin(1e-9).of(2.0)
        }
    }

    @Test fun fifthFretMatchesNextOpenStringExceptG() {
        // Standard tuning: fret 5 = next string open, except G->B which is fret 4.
        for (string in 0 until 5) {
            val fret = if (string == 3) 4 else 5
            assertThat(frequencyFor(string, fret)).isWithin(1e-9).of(frequencyFor(string + 1, 0))
        }
    }

    @Test fun knownFrettedNotes() {
        assertThat(frequencyFor(5, 5)).isWithin(tolerance).of(440.0)      // A4 on high E
        assertThat(frequencyFor(0, 3)).isWithin(tolerance).of(98.00)      // G2
        assertThat(frequencyFor(1, 3)).isWithin(tolerance).of(130.81)     // C3
        assertThat(frequencyFor(5, 24)).isWithin(tolerance).of(1318.51)   // E6
    }

    @Test fun alternateTunings() {
        assertThat(frequencyFor(0, 0, Tuning.DROP_D)).isWithin(tolerance).of(73.42)
        assertThat(frequencyFor(1, 0, Tuning.OPEN_G)).isWithin(tolerance).of(98.00)
        assertThat(frequencyFor(5, 0, Tuning.OPEN_G)).isWithin(tolerance).of(293.66)
        assertThat(frequencyFor(0, 0, Tuning.HALF_STEP_DOWN)).isWithin(tolerance).of(77.78)
        assertThat(frequencyFor(5, 0, Tuning.HALF_STEP_DOWN)).isWithin(tolerance).of(311.13)
    }

    @Test fun halfStepDownIsEverythingOneSemitoneLower() {
        for (string in 0 until STRING_COUNT) {
            assertThat(frequencyFor(string, 1, Tuning.HALF_STEP_DOWN))
                .isWithin(1e-9).of(frequencyFor(string, 0, Tuning.STANDARD))
        }
    }

    @Test fun openStringHzMatchesFrequencyFor() {
        for (tuning in Tuning.ALL) {
            val hz = tuning.openStringHz()
            for (s in 0 until STRING_COUNT) {
                assertThat(hz[s].toDouble()).isWithin(1e-3).of(frequencyFor(s, 0, tuning))
            }
        }
    }

    @Test fun outOfRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { frequencyFor(6, 0) }
        assertThrows(IllegalArgumentException::class.java) { frequencyFor(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { frequencyFor(0, 25) }
        assertThrows(IllegalArgumentException::class.java) { frequencyFor(0, -1) }
    }

    @Test fun everyChordToneFrequencyIsInGuitarRange() {
        for (tuning in Tuning.ALL) for (chord in ChordLibrary.ALL) {
            chord.playedStrings.forEach { s ->
                assertThat(frequencyFor(s, chord.frets[s]!!, tuning)).isIn(com.google.common.collect.Range.closed(70.0, 700.0))
            }
        }
    }
}
