package com.strumwise.game

import com.strumwise.music.ChordLibrary
import com.strumwise.music.STRING_COUNT
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ChartException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Parses and validates song chart JSON. Invalid charts fail loudly with a readable reason. */
object ChartParser {
    private val json = Json {
        ignoreUnknownKeys = true   // forward compatible: newer charts may carry extra fields
        isLenient = false
    }

    fun parse(text: String): SongChart {
        val chart = try {
            json.decodeFromString(SongChart.serializer(), text)
        } catch (e: SerializationException) {
            throw ChartException("Malformed chart: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ChartException("Malformed chart: ${e.message}", e)
        }
        validate(chart)
        return chart
    }

    fun toJson(chart: SongChart): String = json.encodeToString(SongChart.serializer(), chart)

    fun validate(chart: SongChart) {
        fun fail(reason: String): Nothing = throw ChartException("Chart '${chart.id}': $reason")
        if (chart.id.isBlank()) fail("id is blank")
        if (chart.title.isBlank()) fail("title is blank")
        if (chart.bpm !in 20f..400f) fail("bpm ${chart.bpm} out of range")
        if (chart.beatsPerBar !in 1..12) fail("beatsPerBar ${chart.beatsPerBar} out of range")
        if (chart.offsetMs < 0) fail("offsetMs is negative")
        if (chart.events.isEmpty()) fail("no events")
        chart.events.forEachIndexed { i, e ->
            if (e.timeMs < 0) fail("event $i has negative time")
            if (i > 0 && e.timeMs < chart.events[i - 1].timeMs) fail("event $i is out of order")
            if (e.chord != null && ChordLibrary[e.chord] == null) fail("event $i uses unknown chord '${e.chord}'")
            when (e.type) {
                EventType.STRUM -> if (e.chord == null) fail("strum event $i has no chord")
                EventType.PLUCK -> {
                    val s = e.string ?: fail("pluck event $i has no string")
                    if (s !in 0 until STRING_COUNT) fail("pluck event $i string $s out of range")
                    e.chord?.let { id ->
                        if (ChordLibrary.getValue(id).isMuted(s)) fail("pluck event $i plays string $s, muted in $id")
                    }
                }
            }
        }
    }
}
