package com.example.ads

object AdConstants {
    // AdMob App ID
    const val APP_ID = "ca-app-pub-1976234535616337~2071349271"

    // Ad Unit IDs
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1976234535616337/1375496077"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-1976234535616337/4543645543"
    const val NATIVE_ADVANCED_AD_UNIT_ID = "ca-app-pub-1976234535616337/1471698990"
    const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1976234535616337/5136496311"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1976234535616337/8253614249"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-1976234535616337/5627450904"

    // Cooldown timings for policy compliance (prevent spamming fullscreen ads)
    const val INTERSTITIAL_COOLDOWN_MS = 45_000L // 45 seconds between interstitials
    const val APP_OPEN_EXPIRATION_MS = 4 * 3600 * 1000L // 4 hours valid cache window
}
