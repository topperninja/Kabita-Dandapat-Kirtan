package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.AppOpenAdManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scale = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                delayMillis = 150
            )
        )
        delay(400L) // Brief hold on splash graphic

        if (activity != null) {
            AppOpenAdManager.getInstance(context).showOnAppLaunch(
                activity = activity,
                maxWaitMs = 2500L
            ) {
                onNavigateToHome()
            }
        } else {
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1128)), // Match the splash background color
        contentAlignment = Alignment.Center
    ) {
        // Use the generated splash image
        Image(
            painter = painterResource(id = R.drawable.classmasti_splash_1787650917463),
            contentDescription = "Classmasti Logo",
            modifier = Modifier
                .size(200.dp)
                .scale(scale.value)
        )
    }
}

