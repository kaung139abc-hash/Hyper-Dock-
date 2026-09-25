package com.kaung139abc.hyperdock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private val windows = mutableListOf<View>()
    private var dock: View? = null

    override fun onCreate() {
        super.onCreate()
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, notification)
        }
        showDock()
    }

    private fun notification(): Notification {
        val channel = "hyper_dock"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channel, "Hyper Dock", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channel)
            .setContentTitle("Hyper Dock")
            .setContentText("Two floating windows are ready")
            .setSmallIcon(R.drawable.ic_hyperdock)
            .build()
    }

    private fun bg(color: Int, radius: Float = 24f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(2, Color.argb(180, 255, 255, 255))
        }

    private fun layoutParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun showDock() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val dock = TextView(this).apply {
            text = "☰  HYPER"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(24, 14, 24, 14)
            background = bg(Color.rgb(25, 25, 32), 32f)
            elevation = 12f
        }

        val params = layoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            24,
            90
        )
        wm?.addView(dock, params)
        this.dock = dock

        var sx = 0f
        var sy = 0f
        var wx = 0
        var wy = 0
        var moved = false

        dock.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY
                    wx = params.x; wy = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - sx) > 8 || kotlin.math.abs(e.rawY - sy) > 8) moved = true
                    params.x = wx + (e.rawX - sx).toInt()
                    params.y = wy + (e.rawY - sy).toInt()
                    wm?.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        showTwoWindows()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun showTwoWindows() {
        if (windows.isNotEmpty()) return
        val screenWidth = resources.displayMetrics.widthPixels
        val width = (screenWidth * 0.46f).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.34f).toInt()

        addMiniWindow("WINDOW 1", 16, 180, width, height)
        addMiniWindow("WINDOW 2", screenWidth - width - 16, 180, width, height)
    }

    private fun addMiniWindow(title: String, x: Int, y: Int, width: Int, height: Int) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 10)
            background = bg(Color.argb(245, 18, 20, 27), 22f)
            elevation = 16f
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val close = Button(this).apply {
            text = "×"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = bg(Color.TRANSPARENT, 12f)
            setOnClickListener { removeWindow(root) }
        }

        bar.addView(label)
        bar.addView(close)
        root.addView(bar)

        val content = TextView(this).apply {
            text = "Mini Window\n\nဒီနေရာမှာ Hyper Dock ရဲ့ floating content ကိုပြမယ်။"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bg(Color.rgb(35, 38, 48), 16f)
        }
        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val p = layoutParams(width, height, x, y)
        wm?.addView(root, p)
        windows.add(root)
        makeDraggable(root, p)
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var sx = 0f; var sy = 0f; var wx = 0; var wy = 0
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY
                    wx = params.x; wy = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = wx + (e.rawX - sx).toInt()
                    params.y = wy + (e.rawY - sy).toInt()
                    wm?.updateViewLayout(v, params)
                    true
                }
                else -> true
            }
        }
    }

    private fun removeWindow(view: View) {
        try { wm?.removeView(view) } catch (_: Exception) {}
        windows.remove(view)
    }

    override fun onDestroy() {
        windows.forEach { try { wm?.removeView(it) } catch (_: Exception) {} }
        windows.clear()
        dock?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        dock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
