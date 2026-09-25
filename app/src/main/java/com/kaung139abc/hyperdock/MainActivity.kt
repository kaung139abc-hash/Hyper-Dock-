package com.kaung139abc.hyperdock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
        root.addView(TextView(this).apply { text = "Hyper Dock"; textSize = 30f })
        root.addView(TextView(this).apply { text = "Floating dock for games and other apps."; textSize = 16f; setPadding(0,12,0,28) })
        val permission = Button(this).apply { text = "Grant floating-window permission" }
        val start = Button(this).apply { text = "Start Hyper Dock" }
        val stop = Button(this).apply { text = "Stop Hyper Dock" }
        root.addView(permission); root.addView(start); root.addView(stop); setContentView(root)
        permission.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        start.setOnClickListener { if (Settings.canDrawOverlays(this)) startService(Intent(this, OverlayService::class.java)) else permission.performClick() }
        stop.setOnClickListener { stopService(Intent(this, OverlayService::class.java)) }
    }
}
