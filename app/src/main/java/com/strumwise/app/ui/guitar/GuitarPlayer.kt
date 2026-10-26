package com.strumwise.app.ui.guitar

import com.strumwise.audio.GuitarEngine
import com.strumwise.music.Chord

/** A note that actually sounded (muted strings never produce one). */
data class PlayedNote(val string: Int, val fret: Int, val velocity: Float, val delayMs: Float, val direction: StrumDirection?)

/**
 * Turns touch into sound: decides which fret each string plays (chord shape, a held free-fret
 * finger, or open) and sends it to the engine. Shared by the free-play and game screens.
 */
class GuitarPlayer(private val engine: GuitarEngine) {
    /** Selected chord; null = no chord (open strings / free fret). */
    var chord: Chord? = null

    /** Free-fret fingers: pointer id -> (string, fret). */
    private val held = LinkedHashMap<Long, Pair<Int, Int>>()

    /** Fret a string sounds at right now. Null = muted by the chord shape. */
    fun fretFor(string: Int): Int? {
        chord?.let { return it.frets[string] }
        // With several fingers on one string the highest fret sounds, as on a real fretboard.
        return held.values.filter { it.first == string }.maxOfOrNull { it.second } ?: 0
    }

    fun strum(hits: List<StringHit>): List<PlayedNote> {
        val played = ArrayList<PlayedNote>(hits.size)
        for (hit in hits) {
            val fret = fretFor(hit.string)
            if (fret == null) {
                // Muted string: the strumming hand still touches it, so damp anything ringing on it.
                engine.muteString(hit.string)
                continue
            }
            val note = PlayedNote(hit.string, fret, hit.velocity, hit.delayMs, hit.direction)
            engine.noteOn(note.string, note.fret, note.velocity, note.delayMs)
            played += note
        }
        return played
    }

    /** Free-fret press: sounds the note straight away (a hammer-on) and holds the fret for strums. */
    fun fretDown(pointerId: Long, string: Int, fret: Int, velocity: Float = 0.75f): PlayedNote {
        held[pointerId] = string to fret
        engine.noteOn(string, fret, velocity)
        return PlayedNote(string, fret, velocity, 0f, null)
    }

    fun fretUp(pointerId: Long) {
        held.remove(pointerId)
    }

    fun releaseAll() {
        held.clear()
    }
}
