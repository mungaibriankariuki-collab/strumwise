package com.strumwise.app.monetization

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// =================================================================================================
// MONETIZATION HOOKS — intentionally NOT implemented.
//
// These are the only places the app touches ads or purchases. Every function is a no-op today so
// the app ships ad-free with every song unlocked. To monetize, implement them here; no other code
// needs to change.
// =================================================================================================

/** Where an interstitial may be shown. */
enum class InterstitialSlot { AFTER_RESULTS }

object Ads {
    // TODO(ads): Initialise the Google Mobile Ads SDK (com.google.android.gms:play-services-ads) from
    //  StrumwiseApp.onCreate, add the APPLICATION_ID meta-data to the manifest, and gather consent
    //  (UMP SDK) before loading any ad. Skip everything when Purchases.hasRemovedAds() is true.
    fun initialize() = Unit

    // TODO(ads): Load an interstitial ahead of time and show it here at most every few songs.
    //  Never show it during play or while the audio clock is running a song.
    fun maybeShowInterstitial(activity: Activity?, slot: InterstitialSlot) = Unit
}

/**
 * Banner placeholder. Renders nothing today.
 * TODO(ads): Host an AdView (AdSize.BANNER / adaptive) via AndroidView, only on menu screens — never
 *  on the play or game screens where it would steal touches from the strings.
 */
@Composable
fun AdBannerSlot(modifier: Modifier = Modifier) {
    // Zero-height placeholder marking where the banner goes; see TODO above.
    Spacer(modifier)
}

object Purchases {
    /** Play Billing product ids (to be created in the Play Console). */
    const val PRODUCT_REMOVE_ADS = "remove_ads"
    const val PRODUCT_SONG_PACK = "song_pack_1"

    // TODO(billing): Connect a BillingClient (com.android.billingclient:billing-ktx), query owned
    //  purchases on start, acknowledge new ones, and cache entitlements in DataStore.
    fun initialize() = Unit

    // TODO(billing): Return true once PRODUCT_REMOVE_ADS is owned.
    fun hasRemovedAds(): Boolean = false

    // TODO(billing): Gate premium charts (e.g. a "premium": true field in the chart JSON) on
    //  PRODUCT_SONG_PACK. Today every song is unlocked.
    fun isSongUnlocked(songId: String): Boolean = songId.isNotEmpty()

    // TODO(billing): Launch the purchase flow for [productId].
    fun launchPurchase(activity: Activity?, productId: String) = Unit
}
