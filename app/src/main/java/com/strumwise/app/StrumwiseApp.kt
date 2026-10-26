package com.strumwise.app

import android.app.Application
import com.strumwise.audio.AudioLifecycleBinder
import com.strumwise.audio.OboeGuitarEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StrumwiseApp : Application() {
    @Inject lateinit var engine: OboeGuitarEngine

    /** Lives as long as the process; used for one-off background work like loading samples. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AudioLifecycleBinder(engine).bind()
        engine.start()
        appScope.launch(Dispatchers.IO) { engine.loadSamplesFromAssets(assets) }
    }
}
