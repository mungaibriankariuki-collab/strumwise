package com.strumwise.audio

object NativeInfo {
    init {
        System.loadLibrary("strumwise_audio")
    }

    fun oboeVersion(): String = nativeOboeVersion()

    @JvmStatic
    private external fun nativeOboeVersion(): String
}
