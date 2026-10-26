package com.strumwise.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmokeTest {
    @Test fun moduleLoads() = assertThat(DataModule).isNotNull()
}
