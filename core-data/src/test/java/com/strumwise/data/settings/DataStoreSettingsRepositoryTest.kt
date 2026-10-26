package com.strumwise.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.strumwise.music.ChordLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo(): DataStoreSettingsRepository {
        val file = File(tmp.newFolder(), "test.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return DataStoreSettingsRepository(store)
    }

    @Test fun defaults() = runTest(UnconfinedTestDispatcher()) {
        val s = repo().settings.first()
        assertThat(s).isEqualTo(UserSettings())
        assertThat(s.chordSlots).isEqualTo(ChordLibrary.DEFAULT_SLOTS)
    }

    @Test fun valuesPersist() = runTest(UnconfinedTestDispatcher()) {
        val r = repo()
        r.setMasterVolume(0.3f)
        r.setTuning("drop_d")
        r.setLeftHanded(true)
        r.setHaptics(false)
        r.setStringVibration(false)
        r.setCalibrationOffset(42)
        r.setSoundType(SoundPreference.SAMPLES)
        r.setDebugOverlay(true)
        val s = r.settings.first()
        assertThat(s.masterVolume).isEqualTo(0.3f)
        assertThat(s.tuningId).isEqualTo("drop_d")
        assertThat(s.leftHanded).isTrue()
        assertThat(s.hapticsEnabled).isFalse()
        assertThat(s.stringVibration).isFalse()
        assertThat(s.calibrationOffsetMs).isEqualTo(42)
        assertThat(s.soundType).isEqualTo(SoundPreference.SAMPLES)
        assertThat(s.debugOverlay).isTrue()
    }

    @Test fun valuesAreClamped() = runTest(UnconfinedTestDispatcher()) {
        val r = repo()
        r.setMasterVolume(3f)
        r.setCalibrationOffset(-9999)
        val s = r.settings.first()
        assertThat(s.masterVolume).isEqualTo(1f)
        assertThat(s.calibrationOffsetMs).isEqualTo(-300)
    }

    @Test fun chordSlotSwap() = runTest(UnconfinedTestDispatcher()) {
        val r = repo()
        r.setChordSlot(2, "Bm")
        r.setChordSlot(0, "NotAChord")   // ignored
        r.setChordSlot(99, "C")          // ignored
        val slots = r.settings.first().chordSlots
        assertThat(slots[2]).isEqualTo("Bm")
        assertThat(slots[0]).isEqualTo(ChordLibrary.DEFAULT_SLOTS[0])
        assertThat(slots.size).isEqualTo(ChordLibrary.DEFAULT_SLOTS.size)
    }

    @Test fun decodeSlotsRepairsBadData() {
        val decoded = DataStoreSettingsRepository.decodeSlots("G,??,D")
        assertThat(decoded[0]).isEqualTo("G")
        assertThat(decoded[1]).isEqualTo(ChordLibrary.DEFAULT_SLOTS[1])
        assertThat(decoded[2]).isEqualTo("D")
        assertThat(decoded.size).isEqualTo(ChordLibrary.DEFAULT_SLOTS.size)
    }
}
