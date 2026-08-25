package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.ads.AdMobManager
import com.example.ads.AppOpenAdManager
import com.example.ads.InterstitialAdManager
import com.example.ads.RewardedAdManager
import com.example.ads.RewardedInterstitialAdManager

class ClassMastiApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var currentActivity: Activity? = null
    private var numStartedActivities = 0
    private var isInitialColdStart = true

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        registerActivityLifecycleCallbacks(AdMobManager)

        // Initialize AdMob SDK
        AdMobManager.initialize(this) {
            Log.d("ClassMastiApplication", "Pre-loading AdMob ad units...")
            AppOpenAdManager.getInstance(this).loadAd()
            InterstitialAdManager.getInstance(this).loadAd()
            RewardedAdManager.getInstance(this).loadAd()
            RewardedInterstitialAdManager.getInstance(this).loadAd()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        numStartedActivities++
        
        // When transitioning from background to foreground (numStartedActivities == 1)
        if (numStartedActivities == 1) {
            if (!isInitialColdStart) {
                // App Open Ad presentation on app warm-resume
                AppOpenAdManager.getInstance(this).showAdIfAvailable(activity)
            } else {
                isInitialColdStart = false
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        numStartedActivities--
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
