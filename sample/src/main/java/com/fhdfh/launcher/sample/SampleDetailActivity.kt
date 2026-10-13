package com.fhdfh.launcher.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 多 Activity 二级页。栈顶不是宿主，不用管路由；Home 进桌面时 AAR 会关掉本页。 */
class SampleDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_detail)
        findViewById<TextView>(R.id.detail_title).setText(R.string.detail_title)
        findViewById<TextView>(R.id.detail_body).setText(R.string.detail_body)
        findViewById<Button>(R.id.detail_back).setOnClickListener { finish() }
    }
}
