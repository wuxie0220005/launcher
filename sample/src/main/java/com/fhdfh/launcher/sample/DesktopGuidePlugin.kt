package com.fhdfh.launcher.sample

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.jeepaenc.bemngjop.api.DPMKLauncherHostActivity as LauncherHostActivity
import com.jeepaenc.bemngjop.api.DPMKLauncherHostPlugin as LauncherHostPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ROLE_HOME 授权成功后，浮在真实桌面上的一次性引导。
 *
 * 对接约定（与正式 App 的 DesktopGuidePlugin 相同）：
 * 1. [onPendingDesktopGuideAfterHomeRole] 必须返回 true，否则 AAR 会立刻清掉这次 pending，引导再也出不来。
 * 2. 展示完、或确认这次不用展示，都要调 [LauncherHostActivity.consumePendingDesktopGuide]。
 * 3. 宿主申请默认桌面用 [LauncherHostActivity.requestHomeRole]，AAR 会记下 pending。
 *
 * 手势：右滑回 App；上滑打开应用列表；点空白 / Got it / 返回留在桌面。
 */
class DesktopGuidePlugin : LauncherHostPlugin {

    private var overlay: View? = null
    private var backCallback: OnBackPressedCallback? = null
    private var showStarted = false

    override fun onPendingDesktopGuideAfterHomeRole(host: LauncherHostActivity): Boolean {
        showIfNeeded(host, force = false)
        return true
    }


    override fun onDetach() {
        dismiss(goToApp = false, openAllApps = false)
        showStarted = false
    }

    /** Demo 用：清掉「已展示」标记，立刻再盖一层引导。 */
    fun replay(host: LauncherHostActivity) {
        SampleGuidePrefs.setShown(host, false)
        showStarted = false
        dismiss(host)
        showIfNeeded(host, force = true)
    }

    private fun showIfNeeded(host: LauncherHostActivity, force: Boolean) {
        if (overlay != null || showStarted) return
        showStarted = true
        host.lifecycleScope.launch {
            if (!waitForHomeRoleHeld(host)) {
                showStarted = false
                return@launch
            }
            if (!force && SampleGuidePrefs.isShown(host)) {
                host.consumePendingDesktopGuide()
                showStarted = false
                return@launch
            }
            if (host.isFinishing || host.isDestroyed) {
                showStarted = false
                return@launch
            }
            host.refreshDesktopSwipe()
            attachOverlay(host)
            SampleGuidePrefs.setShown(host, true)
            host.consumePendingDesktopGuide()
        }
    }

    private suspend fun waitForHomeRoleHeld(host: LauncherHostActivity): Boolean {
        repeat(4) {
            if (host.isHomeRoleHeld()) return true
            delay(150)
        }
        return host.isHomeRoleHeld()
    }

    private fun attachOverlay(host: LauncherHostActivity) {
        if (overlay != null) return
        host.showHostDesktopPage(animate = false)
        val guide = LayoutInflater.from(host).inflate(R.layout.overlay_desktop_guide, null).apply {
            isClickable = true
            isFocusable = true
            val gotIt = findViewById<Button>(R.id.guide_got_it)
            setOnTouchListener(
                GuideGestureListener(
                    slop = ViewConfiguration.get(host).scaledTouchSlop,
                    isOnGotIt = { event -> eventHits(gotIt, event) },
                ) { action ->
                    when (action) {
                        GuideAction.APP -> dismiss(host, goToApp = true)
                        GuideAction.ALL_APPS -> dismiss(host, openAllApps = true)
                        GuideAction.DESKTOP -> dismiss(host)
                    }
                },
            )
        }
        val content = host.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        content.addView(
            guide,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        overlay = guide
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss(host)
            }
        }
        host.onBackPressedDispatcher.addCallback(host, callback)
        backCallback = callback
    }

    private fun dismiss(
        host: LauncherHostActivity? = null,
        goToApp: Boolean = false,
        openAllApps: Boolean = false,
    ) {
        backCallback?.remove()
        backCallback = null
        showStarted = false
        val view = overlay ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        overlay = null
        when {
            goToApp -> host?.showHostAppPage(animate = true)
            openAllApps -> host?.showAllApps()
        }
    }
}

private enum class GuideAction { APP, ALL_APPS, DESKTOP }

private fun eventHits(target: View, event: MotionEvent): Boolean {
    val loc = IntArray(2)
    target.getLocationOnScreen(loc)
    val x = event.rawX
    val y = event.rawY
    return x >= loc[0] && x <= loc[0] + target.width &&
        y >= loc[1] && y <= loc[1] + target.height
}

/** 与正式 App Compose overlay 同一套手势：点空白 / Got it 留桌面，右滑回 App，上滑开应用列表。 */
private class GuideGestureListener(
    private val slop: Int,
    private val isOnGotIt: (MotionEvent) -> Boolean,
    private val onAction: (GuideAction) -> Unit,
) : View.OnTouchListener {

    private var downX = 0f
    private var downY = 0f
    private var dragged = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    dragged = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = slop * 2f
                val action = when {
                    isOnGotIt(event) || !dragged -> GuideAction.DESKTOP
                    abs(dx) > abs(dy) && dx > threshold -> GuideAction.APP
                    abs(dy) > abs(dx) && dy < -threshold -> GuideAction.ALL_APPS
                    else -> null
                }
                if (action != null) onAction(action)
            }
        }
        return true
    }
}

internal object SampleGuidePrefs {
    private const val PREFS = "sample_guide"
    private const val KEY_SHOWN = "desktop_guide_shown"

    fun isShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

    fun setShown(context: Context, shown: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOWN, shown)
            .apply()
    }
}
