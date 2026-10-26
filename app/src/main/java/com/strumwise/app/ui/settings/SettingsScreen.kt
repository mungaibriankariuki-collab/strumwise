package com.strumwise.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strumwise.app.ui.theme.StrumColors
import com.strumwise.data.settings.SoundPreference
import com.strumwise.data.settings.UserSettings
import com.strumwise.music.Tuning
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onCalibrate: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        s = s,
        samplesInstalled = viewModel.samplesInstalled,
        onBack = onBack,
        onCalibrate = onCalibrate,
        actions = viewModel,
    )
}

@Composable
private fun SettingsContent(
    s: UserSettings,
    samplesInstalled: Boolean,
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    actions: SettingsViewModel,
) {
    Column(Modifier.fillMaxSize().background(StrumColors.Espresso).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StrumColors.Cream) }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = StrumColors.Cream)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("Sound") {
                    Text("Volume ${(s.masterVolume * 100).roundToInt()}%", color = StrumColors.Cream)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.masterVolume,
                            onValueChange = actions::setVolume,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
                        )
                        OutlinedButton(onClick = actions::preview, modifier = Modifier.padding(start = 8.dp)) { Text("Test") }
                    }
                    Text("Sound type", color = StrumColors.Cream)
                    SingleChoiceSegmentedButtonRow {
                        SoundPreference.entries.forEachIndexed { i, type ->
                            SegmentedButton(
                                selected = s.soundType == type,
                                onClick = { actions.setSoundType(type) },
                                shape = SegmentedButtonDefaults.itemShape(i, SoundPreference.entries.size),
                            ) { Text(if (type == SoundPreference.SYNTH) "Synth" else "Samples") }
                        }
                    }
                    if (s.soundType == SoundPreference.SAMPLES && !samplesInstalled) {
                        Text("No recorded samples are installed, so the synth is used. See README to add samples.", color = StrumColors.CreamMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Section("Tuning") {
                    Tuning.ALL.forEach { t ->
                        FilterChip(
                            selected = s.tuningId == t.id,
                            onClick = { actions.setTuning(t.id) },
                            label = { Text(t.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("Playing") {
                    Toggle("Left-handed", s.leftHanded, actions::setLeftHanded)
                    Toggle("Haptic tick on each string", s.hapticsEnabled, actions::setHaptics)
                    Toggle("String vibration visuals", s.stringVibration, actions::setVibration)
                }
                Section("Timing") {
                    Text("Calibration offset: ${s.calibrationOffsetMs} ms", color = StrumColors.Cream)
                    Slider(
                        value = s.calibrationOffsetMs.toFloat(),
                        onValueChange = { actions.setCalibration(it.roundToInt()) },
                        valueRange = -200f..200f,
                        steps = 79,
                        modifier = Modifier.semantics { contentDescription = "Calibration offset in milliseconds" },
                    )
                    OutlinedButton(onClick = onCalibrate) { Text("Calibrate by tapping…") }
                }
                if (s.debugOverlay) {
                    Section("Developer") {
                        Toggle("Debug overlay (latency, fps)", s.debugOverlay, actions::setDebugOverlay)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(StrumColors.EspressoRaised, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = StrumColors.Lagoon)
        content()
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StrumColors.Cream, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.width(52.dp))
    }
}
