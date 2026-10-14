package com.fhdfh.launcher.sample

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.jeepaenc.bemngjop.api.DPMKLauncherHostActivity as LauncherHostActivity
import com.jeepaenc.bemngjop.api.DPMKLauncherHostPlugin as LauncherHostPlugin

/**
 * 桌面 Smartspace 下方通用槽：正式 App 在这里放 Banner / 原生广告。
 * 只通过 [LauncherHostActivity.desktopSlotContainer] 拿容器，不要碰 Lawnchair 内部类型。
 * 槽在 2 行 Smartspace 格子里 wrap_content，网格不会变高；超出 2 行格子的部分默认被裁掉。
 */
class DesktopSlotAdPlugin : LauncherHostPlugin {

    override fun onAttach(host: LauncherHostActivity) {
        host.postOnWorkspace { bind(host) }
    }

    override fun onWorkspaceReady(host: LauncherHostActivity) {
        host.postOnWorkspace { bind(host) }
    }

    private fun bind(host: LauncherHostActivity) {
        if (host.isDestroyed || host.isFinishing) return
        val container = host.desktopSlotContainer() ?: return
        val height = (56 * host.resources.displayMetrics.density).toInt()
        attachPlaceholderAd(container, host.getString(R.string.placeholder_desktop_ad), height)
        logAvailableHeight(container)
    }

    private fun logAvailableHeight(container: ViewGroup) {
        val parent = container.parent as? View ?: return
        parent.post {
            val density = container.resources.displayMetrics.density
            val available = parent.height - parent.paddingBottom - container.top
            val (leftGap, pageWidth) = offsetInDesktopPage(container)
            val rightGap = pageWidth - leftGap - container.width
            Log.d(
                TAG,
                "slot available=${available}px (${(available / density).toInt()}dp), " +
                    "smartspaceArea=${parent.height}px (${(parent.height / density).toInt()}dp), " +
                    "slotTop=${container.top}px, slotWidth=${container.width}px, " +
                    "leftGap=${leftGap}px (${(leftGap / density).toInt()}dp), " +
                    "rightGap=${rightGap}px (${(rightGap / density).toInt()}dp)",
            )
        }
    }

    /** Left offset of [view] inside the desktop page and that page's width, ignoring outer pager scroll. */
    private fun offsetInDesktopPage(view: View): Pair<Int, Int> {
        var x = 0f
        var current: View = view
        while (true) {
            val parent = current.parent as? View ?: break
            x += current.left + current.translationX - parent.scrollX
            if (parent.parent?.javaClass?.name?.endsWith("RecyclerView") == true) {
                return x.toInt() to parent.width
            }
            current = parent
        }
        return x.toInt() to view.resources.displayMetrics.widthPixels
    }

    private companion object {
        const val TAG = "DesktopSlot"
    }
}
