package com.fhdfh.launcher.sample

import com.example.nb.api.DPMKLauncherHostActivity as LauncherHostActivity
import com.example.nb.api.DPMKLauncherHostPlugin as LauncherHostPlugin

/**
 * 应用列表搜索框下方原生广告槽。
 * [LauncherHostActivity.allAppsNativeAdContainer] 拿容器，[LauncherHostActivity.setNativeAdVisible] 控制显隐。
 * 列表打开时展示，关掉时隐藏；占位 View 只加一次。
 */
class AllAppsSlotAdPlugin : LauncherHostPlugin {

    override fun onAttach(host: LauncherHostActivity) {
        if (host.isAllAppsOpen()) bind(host, visible = true)
    }

    override fun onAllAppsOpened(host: LauncherHostActivity) {
        bind(host, visible = true)
    }

    override fun onAllAppsClosed(host: LauncherHostActivity) {
        host.setNativeAdVisible(false)
    }

    private fun bind(host: LauncherHostActivity, visible: Boolean) {
        val container = host.allAppsNativeAdContainer() ?: return
        val height = (72 * host.resources.displayMetrics.density).toInt()
        attachPlaceholderAd(container, host.getString(R.string.placeholder_all_apps_ad), height)
        host.setNativeAdVisible(visible)
    }
}
