package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

object AdMobManager : Application.ActivityLifecycleCallbacks {
    private const val TAG = "AdMobManager"
    private val isInitialized = AtomicBoolean(false)

    var currentActivity: Activity? = null
        private set

    /**
     * Set to true whenever ANY fullscreen ad (App Open, Interstitial, Rewarded, Rewarded Interstitial)
     * is currently showing on screen to prevent collision or double presentations.
     */
    var isShowingFullscreenAd: Boolean = false

    fun initialize(context: Context, onInitialized: (() -> Unit)? = null) {
        if (isInitialized.compareAndSet(false, true)) {
            Log.d(TAG, "Initializing Google Mobile Ads SDK...")
            
            // Set general request configuration
            val requestConfiguration = RequestConfiguration.Builder()
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            // Initialize MobileAds on background thread as recommended by Google Mobile Ads SDK
            MobileAds.initialize(context.applicationContext) { initializationStatus ->
                Log.d(TAG, "Google Mobile Ads SDK Initialized: $initializationStatus")
                onInitialized?.invoke()
            }
        }
    }

    fun buildAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }
}
