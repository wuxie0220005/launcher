package com.fhdfh.launcher.sample

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import com.jeepaenc.bemngjop.api.DPMKLauncherHostActivity as LauncherHostActivity
import com.jeepaenc.bemngjop.api.DPMKLauncherHostPlugin as LauncherHostPlugin
import kotlin.math.abs

/** 接入方只提供 View 和手势；展示时机、挂载、返回键及清理由 SDK 管理。 */
class DesktopGuidePlugin : LauncherHostPlugin {

    override fun createDesktopGuideView(host: LauncherHostActivity): View =
        LayoutInflater.from(host).inflate(R.layout.overlay_desktop_guide, null).apply {
            val gotIt = findViewById<Button>(R.id.guide_got_it)
            setOnTouchListener(
                GuideGestureListener(
                    slop = ViewConfiguration.get(host).scaledTouchSlop,
                    isOnGotIt = { event -> eventHits(gotIt, event) },
                ) { action ->
                    host.dismissDesktopGuide()
                    when (action) {
                        GuideAction.APP -> host.showHostAppPage(animate = true)
                        GuideAction.ALL_APPS -> host.showAllApps()
                        GuideAction.DESKTOP -> Unit
                    }
                },
            )
        }

    /** Demo 用：SDK 忽略首次展示标记并重播。 */
    fun replay(host: LauncherHostActivity) {
        host.dismissDesktopGuide()
        host.showDesktopGuide()
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
