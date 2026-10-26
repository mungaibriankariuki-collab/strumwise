package com.strumwise.app.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.BuildConfig
import com.strumwise.app.ui.settings.SettingsViewModel
import com.strumwise.audio.OboeGuitarEngine

/** Hidden developer menu (tap the Home title seven times). */
@Composable
fun DevMenuDialog(onDismiss: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Developer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Debug overlay (latency, fps)", modifier = Modifier.weight(1f))
                    Switch(checked = s.debugOverlay, onCheckedChange = viewModel::setDebugOverlay)
                }
                Text("Version ${BuildConfig.VERSION_NAME} · Oboe ${OboeGuitarEngine.oboeVersion()}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
