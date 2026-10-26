package com.strumwise.app

import android.app.Application
import com.strumwise.app.audio.EngineSettingsSync
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
    @Inject lateinit var engineSettingsSync: EngineSettingsSync

    /** Lives as long as the process; used for settings sync and one-off background work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AudioLifecycleBinder(engine).bind()
        engine.start()
        engineSettingsSync.start(appScope)
        appScope.launch(Dispatchers.IO) { engine.loadSamplesFromAssets(assets) }
    }
}
