package com.strumwise.data.settings

import com.strumwise.music.ChordLibrary

/** Everything the user can change in Settings, plus the customised chord column. */
data class UserSettings(
    val masterVolume: Float = 0.8f,
    val tuningId: String = "standard",
    val leftHanded: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val stringVibration: Boolean = true,
    /** Added to every tap time before judging (positive = the player's taps register late). */
    val calibrationOffsetMs: Int = 0,
    val soundType: SoundPreference = SoundPreference.SYNTH,
    val chordSlots: List<String> = ChordLibrary.DEFAULT_SLOTS,
    /** Hidden developer menu: shows the latency/fps debug overlay. */
    val debugOverlay: Boolean = false,
)

enum class SoundPreference { SYNTH, SAMPLES }
