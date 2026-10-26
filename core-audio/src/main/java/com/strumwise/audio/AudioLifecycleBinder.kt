package com.strumwise.audio

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Releases the audio stream when the whole app goes to the background and reopens it on return.
 * Uses the process lifecycle so moving between screens/activities never interrupts audio.
 */
class AudioLifecycleBinder(private val engine: GuitarEngine) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = engine.resumeFromForeground()

    override fun onStop(owner: LifecycleOwner) = engine.pauseForBackground()

    fun bind() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)
}
