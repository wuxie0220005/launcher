package com.fhdfh.launcher.sample

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Demo 广告占位：正式宿主换成自己的原生广告 View 即可，容器 API 不变。 */
internal fun attachPlaceholderAd(container: ViewGroup, label: String, heightPx: Int) {
    if (container.findViewWithTag<TextView>(TAG) != null) return
    val pad = (12 * container.resources.displayMetrics.density).toInt()
    container.addView(
        TextView(container.context).apply {
            tag = TAG
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF5E78FF.toInt())
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx,
            )
        },
    )
}

private const val TAG = "sample_placeholder_ad"
