package com.strumwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strumwise.audio.GuitarEngine
import com.strumwise.audio.OboeGuitarEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var engine: GuitarEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Temporary sound check until the Phase 3 play screen replaces it.
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Strumwise sound check — Oboe ${OboeGuitarEngine.oboeVersion()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (string in 0 until 6) {
                            Button(onClick = { engine.noteOn(string, 0, 0.8f) }) { Text("S${string + 1}") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // G major, strummed down with 8 ms between strings.
                        val g = intArrayOf(3, 2, 0, 0, 0, 3)
                        Button(onClick = { g.forEachIndexed { s, f -> engine.noteOn(s, f, 0.8f, s * 8f) } }) {
                            Text("Strum G")
                        }
                        Button(onClick = { engine.muteAll() }) { Text("Mute") }
                    }
                }
            }
        }
    }
}
