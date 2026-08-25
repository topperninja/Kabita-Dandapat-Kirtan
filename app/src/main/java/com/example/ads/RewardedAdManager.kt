package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    companion object {
        private const val TAG = "RewardedAdManager"
        @Volatile
        private var instance: RewardedAdManager? = null

        fun getInstance(context: Context): RewardedAdManager {
            return instance ?: synchronized(this) {
                instance ?: RewardedAdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun loadAd() {
        if (isLoading || rewardedAd != null) return

        isLoading = true
        val request = AdMobManager.buildAdRequest()

        RewardedAd.load(
            context,
            AdConstants.REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded Ad loaded.")
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded Ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    fun isAdReady(): Boolean = rewardedAd != null

    fun showAd(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onAdClosed: () -> Unit
    ) {
        if (AdMobManager.isShowingFullscreenAd || rewardedAd == null) {
            Log.w(TAG, "Rewarded Ad not ready or fullscreen active.")
            onAdClosed()
            loadAd()
            return
        }

        var rewardItemEarned: RewardItem? = null

        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdMobManager.isShowingFullscreenAd = true
                Log.d(TAG, "Rewarded Ad showed.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded Ad dismissed.")
                rewardedAd = null
                AdMobManager.isShowingFullscreenAd = false
                rewardItemEarned?.let { onRewardEarned(it) }
                onAdClosed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded Ad failed to show: ${adError.message}")
                rewardedAd = null
                AdMobManager.isShowingFullscreenAd = false
                onAdClosed()
                loadAd()
            }
        }

        rewardedAd?.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardItemEarned = rewardItem
        }
    }
}
