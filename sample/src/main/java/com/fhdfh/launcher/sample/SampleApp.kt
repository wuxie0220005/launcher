package com.fhdfh.launcher.sample

import android.content.Intent
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.jeepaenc.bemngjop.api.DPMKLauncherSdk as LauncherSdk

/**
 * 宿主保留普通 Application，通过 SDK 显式初始化。
 * Manifest `android:name` 指向本类。
 */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LauncherSdk.initialize(this)
        val icons = listOf(R.drawable.ic_desktop_scan, R.drawable.ic_desktop_text,
            R.drawable.ic_desktop_sign, R.drawable.ic_desktop_id)
        val entries = buildList {
            icons.forEachIndexed { index, resource ->
                val icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                ContextCompat.getDrawable(this@SampleApp, resource)!!.apply {
                    setBounds(0, 0, 128, 128)
                    draw(Canvas(icon))
                }
                add(LauncherSdk.InitialDesktopEntry(
                    id = "promotion_${index + 1}", title = "Promotion ${index + 1}", icon = icon,
                    launchIntent = Intent(this@SampleApp, SampleHostActivity::class.java)
                        .putExtra(SampleHostActivity.EXTRA_HOST_JUMP, true),
                    cellX = index, cellY = 3,
                ))
                add(LauncherSdk.InitialDesktopEntry(
                    id = "business_${index + 1}", title = "Shortcut ${index + 1}", icon = icon,
                    launchIntent = Intent(this@SampleApp, SampleHostActivity::class.java)
                        .putExtra(SampleHostActivity.EXTRA_HOST_JUMP, true),
                    cellX = index, cellY = 4,
                ))
            }
        }
        LauncherSdk.configureInitialDesktopEntries(
            context = this,
            entries = entries,
            showDefaultFirstRowApps = false,
        )
    }
}
