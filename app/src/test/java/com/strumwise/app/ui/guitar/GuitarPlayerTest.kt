package com.strumwise.app.ui.guitar

import com.google.common.truth.Truth.assertThat
import com.strumwise.app.fakes.FakeGuitarEngine
import com.strumwise.music.ChordLibrary
import org.junit.Test

class GuitarPlayerTest {
    private val engine = FakeGuitarEngine()
    private val player = GuitarPlayer(engine)
    private fun downStrum() = (0 until 6).map { StringHit(it, 0.8f, it * 8f, StrumDirection.DOWN) }

    @Test fun openStringsWithoutChord() {
        val played = player.strum(downStrum())
        assertThat(played.map { it.fret }).containsExactly(0, 0, 0, 0, 0, 0)
        assertThat(engine.notes.map { it.string }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test fun chordShapeDecidesFretsAndMutes() {
        player.chord = ChordLibrary.getValue("D") // xx0232
        val played = player.strum(downStrum())
        assertThat(played.map { it.string }).containsExactly(2, 3, 4, 5).inOrder()
        assertThat(played.map { it.fret }).containsExactly(0, 2, 3, 2).inOrder()
        assertThat(engine.mutedStrings).containsExactly(0, 1)
    }

    @Test fun strumKeepsSpacingAndVelocity() {
        player.chord = ChordLibrary.getValue("G")
        player.strum(downStrum())
        assertThat(engine.notes.map { it.delayMs }).containsExactly(0f, 8f, 16f, 24f, 32f, 40f).inOrder()
        assertThat(engine.notes.all { it.velocity == 0.8f }).isTrue()
    }

    @Test fun freeFretHoldsFretForStrums() {
        val note = player.fretDown(pointerId = 7, string = 4, fret = 3)
        assertThat(note.fret).isEqualTo(3)
        assertThat(engine.notes.last()).isEqualTo(FakeGuitarEngine.Note(4, 3, 0.75f, 0f))
        val played = player.strum(listOf(StringHit(4, 0.6f, 0f, StrumDirection.DOWN)))
        assertThat(played.single().fret).isEqualTo(3)
        player.fretUp(7)
        assertThat(player.strum(listOf(StringHit(4, 0.6f, 0f, null))).single().fret).isEqualTo(0)
    }

    @Test fun highestHeldFretWins() {
        player.fretDown(1, 2, 2)
        player.fretDown(2, 2, 5)
        assertThat(player.fretFor(2)).isEqualTo(5)
        player.fretUp(2)
        assertThat(player.fretFor(2)).isEqualTo(2)
        player.releaseAll()
        assertThat(player.fretFor(2)).isEqualTo(0)
    }
}
