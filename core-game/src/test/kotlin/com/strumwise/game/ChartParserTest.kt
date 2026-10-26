package com.strumwise.game

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ChartParserTest {
    private val valid = """
        {
          "id": "demo", "title": "Demo", "bpm": 120, "offsetMs": 1000, "difficulty": "easy",
          "events": [
            {"timeMs": 0, "type": "strum", "chord": "G", "direction": "down"},
            {"timeMs": 500, "type": "strum", "chord": "C", "direction": "up"},
            {"timeMs": 1000, "type": "pluck", "string": 0},
            {"timeMs": 1000, "type": "pluck", "string": 1, "chord": "Am"}
          ]
        }
    """.trimIndent()

    @Test fun parsesAllFields() {
        val chart = ChartParser.parse(valid)
        assertThat(chart.id).isEqualTo("demo")
        assertThat(chart.title).isEqualTo("Demo")
        assertThat(chart.artist).isEqualTo("Traditional")
        assertThat(chart.bpm).isEqualTo(120f)
        assertThat(chart.beatsPerBar).isEqualTo(4)
        assertThat(chart.offsetMs).isEqualTo(1000)
        assertThat(chart.difficulty).isEqualTo(Difficulty.EASY)
        assertThat(chart.events).hasSize(4)
        assertThat(chart.events[0]).isEqualTo(ChartEvent(0, EventType.STRUM, "G", null, Direction.DOWN))
        assertThat(chart.events[1].direction).isEqualTo(Direction.UP)
        assertThat(chart.events[2]).isEqualTo(ChartEvent(1000, EventType.PLUCK, null, 0, null))
        assertThat(chart.events[3].chord).isEqualTo("Am")
    }

    @Test fun derivedValues() {
        val chart = ChartParser.parse(valid)
        assertThat(chart.lengthMs).isEqualTo(2000)
        assertThat(chart.beatMs).isWithin(1e-9).of(500.0)
        assertThat(chart.chordsUsed).containsExactly("G", "C", "Am").inOrder()
        assertThat(chart.notes().map { it.timeMs }).containsExactly(1000L, 1500L, 2000L, 2000L).inOrder()
    }

    @Test fun ignoresUnknownFields() {
        val chart = ChartParser.parse(valid.replace("\"title\": \"Demo\",", "\"title\": \"Demo\", \"composer\": \"anon\","))
        assertThat(chart.title).isEqualTo("Demo")
    }

    @Test fun roundTrip() {
        val chart = ChartParser.parse(valid)
        assertThat(ChartParser.parse(ChartParser.toJson(chart))).isEqualTo(chart)
    }

    private fun assertRejected(json: String, reason: String) {
        val e = assertThrows(ChartException::class.java) { ChartParser.parse(json) }
        assertThat(e.message).contains(reason)
    }

    @Test fun rejectsMalformedJson() = assertRejected("{ not json", "Malformed")

    @Test fun rejectsMissingRequiredField() = assertRejected(valid.replace("\"bpm\": 120,", ""), "Malformed")

    @Test fun rejectsUnknownEventType() = assertRejected(valid.replace("\"type\": \"strum\", \"chord\": \"G\"", "\"type\": \"slap\", \"chord\": \"G\""), "Malformed")

    @Test fun rejectsUnknownChord() = assertRejected(valid.replace("\"chord\": \"G\"", "\"chord\": \"Gx9\""), "unknown chord")

    @Test fun rejectsStrumWithoutChord() = assertRejected(valid.replace("\"chord\": \"G\", ", ""), "has no chord")

    @Test fun rejectsPluckWithoutString() = assertRejected(valid.replace("\"string\": 0", "\"chord\": \"G\""), "has no string")

    @Test fun rejectsStringOutOfRange() = assertRejected(valid.replace("\"string\": 0", "\"string\": 6"), "out of range")

    @Test fun rejectsPluckOnMutedString() = assertRejected(valid.replace("\"string\": 1, \"chord\": \"Am\"", "\"string\": 0, \"chord\": \"Am\""), "muted")

    @Test fun rejectsOutOfOrderEvents() = assertRejected(valid.replace("\"timeMs\": 500", "\"timeMs\": 5000"), "out of order")

    @Test fun rejectsNegativeTime() = assertRejected(valid.replace("\"timeMs\": 0,", "\"timeMs\": -5,"), "negative")

    @Test fun rejectsBadBpm() = assertRejected(valid.replace("\"bpm\": 120", "\"bpm\": 0"), "bpm")

    @Test fun rejectsEmptyEvents() =
        assertRejected("""{"id":"x","title":"X","bpm":100,"difficulty":"hard","events":[]}""", "no events")

    @Test fun everyBundledChartIsValid() {
        val dir = File("../app/src/main/assets/songs")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertThat(files.size).isAtLeast(8)
        val ids = files.map { file ->
            val chart = ChartParser.parse(file.readText())
            assertThat(file.nameWithoutExtension).isEqualTo(chart.id)
            assertThat(chart.events.size).isAtLeast(16)
            chart.id
        }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsAtLeast("amazing_grace", "house_of_the_rising_sun", "greensleeves", "when_the_saints",
            "practice_g_c_d", "practice_am_f_c_g", "practice_em_c_g_d")
    }

    @Test fun bundledChartsCoverEveryDifficulty() {
        val charts = File("../app/src/main/assets/songs").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { ChartParser.parse(it.readText()) }
        assertThat(charts.map { it.difficulty }.toSet()).containsExactlyElementsIn(Difficulty.entries)
    }
}
