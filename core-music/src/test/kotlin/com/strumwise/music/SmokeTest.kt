package com.strumwise.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmokeTest {
    @Test fun sixStrings() = assertThat(STRING_COUNT).isEqualTo(6)
}
