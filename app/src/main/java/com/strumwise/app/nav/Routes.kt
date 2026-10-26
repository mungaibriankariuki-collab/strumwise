package com.strumwise.app.nav

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object HomeRoute
@Serializable data object FreePlayRoute
@Serializable data object SongsRoute
@Serializable data object SettingsRoute
@Serializable data object CalibrationRoute

/** loopFromBar/loopToBar < 0 = no loop. */
@Serializable
data class GameRoute(val songId: String, val speed: Float = 1f, val loopFromBar: Int = -1, val loopToBar: Int = -1)

@Serializable
data class ResultsRoute(
    val songId: String,
    val title: String,
    val score: Long,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val accuracy: Float,
    val stars: Int,
    val newHighScore: Boolean,
    val practice: Boolean,
    val speed: Float,
)
