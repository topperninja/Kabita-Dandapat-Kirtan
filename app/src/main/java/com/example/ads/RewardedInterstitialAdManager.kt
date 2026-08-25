package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class RewardedInterstitialAdManager(private val context: Context) {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    companion object {
        private const val TAG = "RewardedInterstitialAdManager"
        @Volatile
        private var instance: RewardedInterstitialAdManager? = null

        fun getInstance(context: Context): RewardedInterstitialAdManager {
            return instance ?: synchronized(this) {
                instance ?: RewardedInterstitialAdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun loadAd() {
        if (isLoading || rewardedInterstitialAd != null) return

        isLoading = true
        val request = AdMobManager.buildAdRequest()

        RewardedInterstitialAd.load(
            context,
            AdConstants.REWARDED_INTERSTITIAL_AD_UNIT_ID,
            request,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded Interstitial Ad loaded.")
                    rewardedInterstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded Interstitial Ad failed to load: ${loadAdError.message}")
                    rewardedInterstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun isAdReady(): Boolean = rewardedInterstitialAd != null

    fun showAd(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onAdClosed: () -> Unit
    ) {
        if (AdMobManager.isShowingFullscreenAd || rewardedInterstitialAd == null) {
            Log.w(TAG, "Rewarded Interstitial not ready or another ad is showing.")
            onAdClosed()
            loadAd()
            return
        }

        var rewardItemEarned: RewardItem? = null

        rewardedInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdMobManager.isShowingFullscreenAd = true
                Log.d(TAG, "Rewarded Interstitial Ad showed.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded Interstitial Ad dismissed.")
                rewardedInterstitialAd = null
                AdMobManager.isShowingFullscreenAd = false
                rewardItemEarned?.let { onRewardEarned(it) }
                onAdClosed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded Interstitial Ad failed to show: ${adError.message}")
                rewardedInterstitialAd = null
                AdMobManager.isShowingFullscreenAd = false
                onAdClosed()
                loadAd()
            }
        }

        rewardedInterstitialAd?.show(activity) { rewardItem ->
            Log.d(TAG, "User earned rewarded interstitial reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardItemEarned = rewardItem
        }
    }
}
