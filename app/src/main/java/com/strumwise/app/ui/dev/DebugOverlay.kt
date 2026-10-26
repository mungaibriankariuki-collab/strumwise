package com.strumwise.app.ui.dev

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.strumwise.app.BuildConfig
import com.strumwise.audio.EngineStats
import com.strumwise.audio.OboeGuitarEngine
import com.strumwise.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    settings: SettingsRepository,
    private val engine: OboeGuitarEngine,
) : ViewModel() {
    val enabled = settings.settings.map { it.debugOverlay }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var stats by mutableStateOf<EngineStats?>(null)
        private set
    var roundTripMs by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            while (true) {
                if (enabled.value) {
                    stats = withContext(Dispatchers.Default) { engine.stats() }
                    Log.d(TAG, "engine $stats")
                }
                delay(1000)
            }
        }
    }

    fun measureRoundTrip() {
        roundTripMs = "measuring…"
        viewModelScope.launch {
            val ms = withContext(Dispatchers.Default) { engine.measureRoundTripMillis() }
            roundTripMs = ms?.let { String.format(Locale.US, "%.1f ms", it) } ?: "no echo heard (use the speaker, quiet room)"
            Log.i(TAG, "round trip = $roundTripMs")
        }
    }

    private companion object {
        const val TAG = "StrumwiseLatency"
    }
}

/** Small always-on-top panel: fps, audio stream stats and (debug builds) measured round-trip latency. */
@Composable
fun DebugOverlay(modifier: Modifier = Modifier, viewModel: DebugViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    if (!enabled) return

    var fps by remember { mutableFloatStateOf(0f) }
    var janky by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L
        var frames = 0
        var windowStart = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && now - last > 20_000_000) janky++ // missed a 60 Hz frame
                if (windowStart == 0L) windowStart = now
                frames++
                if (now - windowStart >= 500_000_000) {
                    fps = frames * 1e9f / (now - windowStart)
                    frames = 0
                    windowStart = now
                }
                last = now
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.measureRoundTrip()
    }

    val mono = FontFamily.Monospace
    Column(modifier.background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(String.format(Locale.US, "%.0f fps · %d janky", fps, janky), color = Color.White, fontFamily = mono, fontSize = 11.sp)
        viewModel.stats?.let { s ->
            Text(
                String.format(Locale.US, "%d Hz · out %.1f ms · xruns %d", s.sampleRate, s.outputLatencyMillis, s.underruns),
                color = Color.White, fontFamily = mono, fontSize = 11.sp,
            )
        }
        viewModel.roundTripMs?.let { Text("round trip: $it", color = Color(0xFF2CCFC0), fontFamily = mono, fontSize = 11.sp) }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Measure round trip", fontSize = 11.sp)
            }
        }
    }
}
