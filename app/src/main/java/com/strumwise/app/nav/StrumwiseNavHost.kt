package com.strumwise.app.nav

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.strumwise.app.monetization.Ads
import com.strumwise.app.monetization.InterstitialSlot
import com.strumwise.app.ui.calibration.CalibrationScreen
import com.strumwise.app.ui.dev.DevMenuDialog
import com.strumwise.app.ui.game.GameScreen
import com.strumwise.app.ui.home.HomeScreen
import com.strumwise.app.ui.play.PlayScreen
import com.strumwise.app.ui.results.ResultsScreen
import com.strumwise.app.ui.settings.SettingsScreen
import com.strumwise.app.ui.songs.SongListScreen

@Composable
fun StrumwiseNavHost(nav: NavHostController = rememberNavController()) {
    var devMenu by remember { mutableStateOf(false) }

    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onPlay = { nav.navigate(FreePlayRoute) },
                onSongs = { nav.navigate(SongsRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
                onDevMenu = { devMenu = true },
            )
        }
        composable<FreePlayRoute> { PlayScreen(onBack = nav::popBackStack) }
        composable<SongsRoute> {
            SongListScreen(onBack = nav::popBackStack, onPlay = { nav.navigate(it) })
        }
        composable<GameRoute> {
            GameScreen(
                onFinished = { results ->
                    nav.navigate(results) { popUpTo<GameRoute> { inclusive = true } }
                },
                onQuitToSongs = { nav.popBackStack(SongsRoute, inclusive = false) },
            )
        }
        composable<ResultsRoute> { entry ->
            val results = entry.toRoute<ResultsRoute>()
            val activity = LocalActivity.current
            // TODO hook: interstitial after a song (no-op until ads are implemented).
            LaunchedEffect(results) { Ads.maybeShowInterstitial(activity, InterstitialSlot.AFTER_RESULTS) }
            ResultsScreen(
                result = results,
                songTitle = results.title,
                onRetry = {
                    nav.navigate(GameRoute(results.songId, results.speed)) { popUpTo<ResultsRoute> { inclusive = true } }
                },
                onSongs = { nav.popBackStack(SongsRoute, inclusive = false) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = nav::popBackStack, onCalibrate = { nav.navigate(CalibrationRoute) })
        }
        composable<CalibrationRoute> { CalibrationScreen(onBack = nav::popBackStack) }
    }

    if (devMenu) DevMenuDialog(onDismiss = { devMenu = false })
}
