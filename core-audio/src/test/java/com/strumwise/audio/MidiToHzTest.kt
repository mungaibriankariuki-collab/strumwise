package com.strumwise.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MidiToHzTest {
    @Test fun a4IsConcertPitch() = assertThat(OboeGuitarEngine.midiToHz(69)).isWithin(1e-3f).of(440f)

    @Test fun lowE() = assertThat(OboeGuitarEngine.midiToHz(40)).isWithin(0.01f).of(82.41f)

    @Test fun octaveDoubles() =
        assertThat(OboeGuitarEngine.midiToHz(52) / OboeGuitarEngine.midiToHz(40)).isWithin(1e-4f).of(2f)
}
