package com.strumwise.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty {
    @SerialName("easy") EASY,
    @SerialName("medium") MEDIUM,
    @SerialName("hard") HARD,
}

@Serializable
enum class EventType {
    @SerialName("strum") STRUM,
    @SerialName("pluck") PLUCK,
}

@Serializable
enum class Direction {
    @SerialName("down") DOWN,
    @SerialName("up") UP,
}

/**
 * One thing the player must play. [timeMs] is relative to the chart's offsetMs.
 * - strum: [chord] required, [direction] optional (shown as a hint, not judged).
 * - pluck: [string] required (0 = low E); [chord] optional — if present, that shape must be held.
 */
@Serializable
data class ChartEvent(
    val timeMs: Long,
    val type: EventType,
    val chord: String? = null,
    val string: Int? = null,
    val direction: Direction? = null,
)

/** A song as stored in assets/songs/<id>.json. */
@Serializable
data class SongChart(
    val id: String,
    val title: String,
    val artist: String = "Traditional",
    val bpm: Float,
    val beatsPerBar: Int = 4,
    val offsetMs: Long = 0,
    val difficulty: Difficulty,
    val events: List<ChartEvent>,
) {
    /** Time of the last event on the song clock. */
    val lengthMs: Long get() = offsetMs + (events.lastOrNull()?.timeMs ?: 0)

    val beatMs: Double get() = 60_000.0 / bpm

    /** Chords used, in order of first appearance (for the song card and practice tips). */
    val chordsUsed: List<String> get() = events.mapNotNull { it.chord }.distinct()
}

/** A chart event placed on the absolute song clock, with its index for identity. */
data class Note(val index: Int, val timeMs: Long, val event: ChartEvent)

fun SongChart.notes(): List<Note> = events.mapIndexed { i, e -> Note(i, offsetMs + e.timeMs, e) }
