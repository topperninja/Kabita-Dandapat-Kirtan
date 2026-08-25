package com.example.ads

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = AdConstants.NATIVE_ADVANCED_AD_UNIT_ID
) {
    val isInPreview = LocalInspectionMode.current
    if (isInPreview) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            androidx.compose.material3.Text("AdMob Native Advanced Ad Preview", modifier = Modifier.padding(16.dp))
        }
        return
    }

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isFailed by remember { mutableStateOf(false) }

    DisposableEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isFailed = true
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
        }
    }

    if (nativeAd != null && !isFailed) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(12.dp),
                factory = { ctx ->
                    createNativeAdLayout(ctx)
                },
                update = { nativeAdView ->
                    val ad = nativeAd ?: return@AndroidView
                    populateNativeAdView(nativeAdView, ad)
                }
            )
        }
    }
}

private fun createNativeAdLayout(context: Context): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val rootLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Top row: [Ad Badge] + Headline + Advertiser
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        layoutParams = LinearLayout.LayoutParams(dpToPx(context, 40), dpToPx(context, 40)).apply {
            marginEnd = dpToPx(context, 10)
        }
    }
    headerRow.addView(iconView)
    nativeAdView.iconView = iconView

    val titleColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val badgeAndTitleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.BLACK)
        setBackgroundColor(android.graphics.Color.parseColor("#FFD54F"))
        setPadding(dpToPx(context, 4), dpToPx(context, 1), dpToPx(context, 4), dpToPx(context, 1))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dpToPx(context, 6)
        }
    }
    badgeAndTitleRow.addView(adBadge)

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    badgeAndTitleRow.addView(headlineView)
    nativeAdView.headlineView = headlineView

    titleColumn.addView(badgeAndTitleRow)

    val advertiserView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 12f
        setTextColor(android.graphics.Color.LTGRAY)
        maxLines = 1
    }
    titleColumn.addView(advertiserView)
    nativeAdView.advertiserView = advertiserView

    headerRow.addView(titleColumn)
    rootLayout.addView(headerRow)

    // Body Text
    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 13f
        setTextColor(android.graphics.Color.WHITE)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 4), 0, dpToPx(context, 4))
    }
    rootLayout.addView(bodyView)
    nativeAdView.bodyView = bodyView

    // MediaView for Main Images / Video Assets (Required by AdMob Native Policies)
    val mediaView = MediaView(context).apply {
        id = View.generateViewId()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 160)
        ).apply {
            topMargin = dpToPx(context, 4)
            bottomMargin = dpToPx(context, 4)
        }
    }
    rootLayout.addView(mediaView)
    nativeAdView.mediaView = mediaView

    // Call to Action Button
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(android.graphics.Color.BLACK)
        setBackgroundColor(android.graphics.Color.parseColor("#FFD54F"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(context, 40)
        ).apply {
            topMargin = dpToPx(context, 6)
        }
    }
    rootLayout.addView(ctaButton)
    nativeAdView.callToActionView = ctaButton

    nativeAdView.addView(rootLayout)
    return nativeAdView
}

private fun populateNativeAdView(nativeAdView: NativeAdView, nativeAd: NativeAd) {
    (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline

    if (nativeAd.body == null) {
        nativeAdView.bodyView?.visibility = View.GONE
    } else {
        nativeAdView.bodyView?.visibility = View.VISIBLE
        (nativeAdView.bodyView as? TextView)?.text = nativeAd.body
    }

    if (nativeAd.callToAction == null) {
        nativeAdView.callToActionView?.visibility = View.GONE
    } else {
        nativeAdView.callToActionView?.visibility = View.VISIBLE
        (nativeAdView.callToActionView as? Button)?.text = nativeAd.callToAction
    }

    if (nativeAd.icon == null) {
        nativeAdView.iconView?.visibility = View.GONE
    } else {
        (nativeAdView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        nativeAdView.iconView?.visibility = View.VISIBLE
    }

    if (nativeAd.advertiser == null) {
        nativeAdView.advertiserView?.visibility = View.GONE
    } else {
        (nativeAdView.advertiserView as? TextView)?.text = nativeAd.advertiser
        nativeAdView.advertiserView?.visibility = View.VISIBLE
    }

    if (nativeAd.mediaContent != null) {
        nativeAdView.mediaView?.setMediaContent(nativeAd.mediaContent!!)
        nativeAdView.mediaView?.visibility = View.VISIBLE
    } else {
        nativeAdView.mediaView?.visibility = View.GONE
    }

    nativeAdView.setNativeAd(nativeAd)
}

private fun dpToPx(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
