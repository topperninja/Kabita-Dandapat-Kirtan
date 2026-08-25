package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class AppOpenAdManager(private val context: Context) {
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var loadTime: Long = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingLaunchCallbacks = mutableListOf<() -> Unit>()

    companion object {
        private const val TAG = "AppOpenAdManager"
        @Volatile
        private var instance: AppOpenAdManager? = null

        fun getInstance(context: Context): AppOpenAdManager {
            return instance ?: synchronized(this) {
                instance ?: AppOpenAdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun isAdAvailable(): Boolean {
        val wasLoadedRecently = (Date().time - loadTime) < AdConstants.APP_OPEN_EXPIRATION_MS
        return appOpenAd != null && wasLoadedRecently
    }

    fun loadAd(onLoadedOrFailed: (() -> Unit)? = null) {
        if (isAdAvailable()) {
            onLoadedOrFailed?.invoke()
            return
        }

        if (isLoadingAd) {
            if (onLoadedOrFailed != null) {
                pendingLaunchCallbacks.add(onLoadedOrFailed)
            }
            return
        }

        if (onLoadedOrFailed != null) {
            pendingLaunchCallbacks.add(onLoadedOrFailed)
        }

        isLoadingAd = true
        val request = AdMobManager.buildAdRequest()

        Log.d(TAG, "Loading App Open Ad with Unit ID: ${AdConstants.APP_OPEN_AD_UNIT_ID}")
        AppOpenAd.load(
            context,
            AdConstants.APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open Ad loaded successfully.")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time

                    val callbacks = ArrayList(pendingLaunchCallbacks)
                    pendingLaunchCallbacks.clear()
                    callbacks.forEach { it.invoke() }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "App Open Ad failed to load (Code ${loadAdError.code}): ${loadAdError.message}. Cause: ${loadAdError.cause}")
                    isLoadingAd = false
                    appOpenAd = null

                    val callbacks = ArrayList(pendingLaunchCallbacks)
                    pendingLaunchCallbacks.clear()
                    callbacks.forEach { it.invoke() }
                }
            }
        )
    }

    /**
     * Called during app launch (e.g. from SplashScreen).
     * If an ad is already loaded, it displays immediately.
     * If it is currently loading, it waits up to maxWaitMs for the ad before proceeding.
     */
    fun showOnAppLaunch(activity: Activity, maxWaitMs: Long = 3000L, onDismissedOrSkipped: () -> Unit) {
        var hasFinished = false
        fun finishOnce() {
            if (!hasFinished) {
                hasFinished = true
                onDismissedOrSkipped()
            }
        }

        if (isAdAvailable()) {
            showAd(activity, onAdDismissed = { finishOnce() })
            return
        }

        val timeoutRunnable = Runnable {
            if (!hasFinished) {
                Log.d(TAG, "App Open Ad launch timeout reached ($maxWaitMs ms). Proceeding to main app.")
                finishOnce()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, maxWaitMs)

        loadAd {
            mainHandler.removeCallbacks(timeoutRunnable)
            if (!hasFinished) {
                if (isAdAvailable()) {
                    showAd(activity, onAdDismissed = { finishOnce() })
                } else {
                    finishOnce()
                }
            }
        }
    }

    fun showAdIfAvailable(activity: Activity, onAdDismissed: (() -> Unit)? = null) {
        if (AdMobManager.isShowingFullscreenAd) {
            Log.d(TAG, "Another fullscreen ad is currently active. Skipping App Open Ad.")
            onAdDismissed?.invoke()
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "App Open Ad is not ready yet. Pre-fetching for next opportunity.")
            onAdDismissed?.invoke()
            loadAd()
            return
        }

        showAd(activity, onAdDismissed)
    }

    private fun showAd(activity: Activity, onAdDismissed: (() -> Unit)? = null) {
        val ad = appOpenAd
        if (ad == null) {
            onAdDismissed?.invoke()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdMobManager.isShowingFullscreenAd = true
                Log.d(TAG, "App Open Ad showed fullscreen content.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed.")
                appOpenAd = null
                AdMobManager.isShowingFullscreenAd = false
                onAdDismissed?.invoke()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "App Open Ad failed to show (Code ${adError.code}): ${adError.message}")
                appOpenAd = null
                AdMobManager.isShowingFullscreenAd = false
                onAdDismissed?.invoke()
                loadAd()
            }
        }

        ad.show(activity)
    }
}

