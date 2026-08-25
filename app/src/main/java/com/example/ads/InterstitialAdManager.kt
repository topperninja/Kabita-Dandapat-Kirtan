package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShowTime: Long = 0

    companion object {
        private const val TAG = "InterstitialAdManager"
        @Volatile
        private var instance: InterstitialAdManager? = null

        fun getInstance(context: Context): InterstitialAdManager {
            return instance ?: synchronized(this) {
                instance ?: InterstitialAdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun loadAd() {
        if (isLoading || interstitialAd != null) return

        isLoading = true
        val request = AdMobManager.buildAdRequest()

        InterstitialAd.load(
            context,
            AdConstants.INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial Ad loaded.")
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Show interstitial ad at natural transition points (e.g. after saving/running project).
     * Enforces cooldown rate limit to comply with Google Play & AdMob policies.
     */
    fun showAd(activity: Activity, force: Boolean = false, onDismiss: () -> Unit) {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastShowTime

        if (!force && timeSinceLast < AdConstants.INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Interstitial suppressed by policy cooldown ($timeSinceLast ms < ${AdConstants.INTERSTITIAL_COOLDOWN_MS} ms).")
            onDismiss()
            return
        }

        if (AdMobManager.isShowingFullscreenAd || interstitialAd == null) {
            Log.d(TAG, "Interstitial not ready or another ad is showing.")
            onDismiss()
            loadAd()
            return
        }

        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdMobManager.isShowingFullscreenAd = true
                lastShowTime = System.currentTimeMillis()
                Log.d(TAG, "Interstitial Ad displayed.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad dismissed.")
                interstitialAd = null
                AdMobManager.isShowingFullscreenAd = false
                onDismiss()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial Ad failed to show: ${adError.message}")
                interstitialAd = null
                AdMobManager.isShowingFullscreenAd = false
                onDismiss()
                loadAd()
            }
        }

        interstitialAd?.show(activity)
    }

    fun isAdReady(): Boolean = interstitialAd != null
}
