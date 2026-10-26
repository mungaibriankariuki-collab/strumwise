package com.strumwise.app.fakes

import com.strumwise.audio.EngineStats
import com.strumwise.audio.GuitarEngine
import com.strumwise.audio.SoundType

/** Records engine calls; its clock is set by the test. */
class FakeGuitarEngine : GuitarEngine {
    data class Note(val string: Int, val fret: Int, val velocity: Float, val delayMs: Float)

    val notes = mutableListOf<Note>()
    val mutedStrings = mutableListOf<Int>()
    var mutedAll = 0
    var volume = 1f
    var tuning: FloatArray? = null
    var soundType = SoundType.SYNTH
    var metronome: Pair<Float, Long>? = null
    var audioTimeNanos = 0L
    override var isRunning = false

    override fun start(): Boolean { isRunning = true; return true }
    override fun stop() { isRunning = false }
    override fun noteOn(string: Int, fret: Int, velocity: Float, delayMs: Float) { notes += Note(string, fret, velocity, delayMs) }
    override fun muteString(string: Int) { mutedStrings += string }
    override fun muteAll() { mutedAll++ }
    override fun setMasterVolume(volume: Float) { this.volume = volume }
    override fun setTuning(openStringHz: FloatArray) { tuning = openStringHz }
    override fun setSoundType(type: SoundType) { soundType = type }
    override fun setMetronome(bpm: Float, firstClickNanos: Long) { metronome = bpm to firstClickNanos }
    override fun getAudioTimeNanos(): Long = audioTimeNanos
    override fun stats() = EngineStats(isRunning, 48000, 10.0, 0)
    override fun pauseForBackground() {}
    override fun resumeFromForeground() {}
}
