package com.kaung139abc.hyperdock

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var dock: View? = null
    private var picker: View? = null
    private val launchedPackages = mutableListOf<String>()

    override fun onCreate() {
        super.onCreate()
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
        showDock()
    }

    private fun notification(): Notification {
        val channel = "hyper_dock"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel, "Hyper Dock", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, channel)
            .setContentTitle("Hyper Dock")
            .setContentText("Floating dock is running")
            .setSmallIcon(R.drawable.ic_hyperdock)
            .build()
    }

    private fun bg(color: Int, radius: Float = 22f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(2, Color.argb(150, 255, 255, 255))
    }

    private fun type() = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun params(w: Int, h: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(w, h, type(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun showDock() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = TextView(this).apply {
            text = "☰  HYPER"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(24, 14, 24, 14)
            background = bg(Color.rgb(25, 25, 32), 32f)
            elevation = 14f
        }
        val p = params(-2, -2, 20, 90)
        wm?.addView(v, p)
        dock = v
        var sx=0f; var sy=0f; var wx=0; var wy=0; var moved=false
        v.setOnTouchListener { view,e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; wx=p.x; wy=p.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX-sx)>8 || kotlin.math.abs(e.rawY-sy)>8) moved=true
                    p.x=wx+(e.rawX-sx).toInt(); p.y=wy+(e.rawY-sy).toInt()
                    wm?.updateViewLayout(view,p); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) showPicker(); true }
                else -> true
            }
        }
    }

    private fun showPicker() {
        if (picker != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = bg(Color.rgb(22, 24, 31), 24f)
            elevation = 20f
        }

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "HYPER APPS"
            textSize = 17f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        head.addView(TextView(this).apply {
            text = "×"
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(18, 4, 8, 4)
            setOnClickListener { closePicker() }
        })
        panel.addView(head)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val pm = packageManager
        val apps = pm.getInstalledApplications(0).filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
            pm.getLaunchIntentForPackage(it.packageName) != null
        }.sortedBy {
            pm.getApplicationLabel(it).toString().lowercase()
        }

        apps.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 10, 8, 10)
                background = bg(Color.rgb(30, 33, 42), 16f)
                setOnClickListener {
                    launchApp(app)
                }
            }

            row.addView(ImageView(this).apply {
                setImageDrawable(pm.getApplicationIcon(app))
                layoutParams = LinearLayout.LayoutParams(48, 48)
            })

            row.addView(TextView(this).apply {
                text = pm.getApplicationLabel(app).toString()
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(14, 0, 8, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })

            list.addView(row)
        }

        scroll.addView(list)
        panel.addView(scroll, LinearLayout.LayoutParams(300, 520))

        val p = params(324, -2, 18, 150)
        wm?.addView(panel, p)
        picker = panel
    }

    private fun launchApp(app: ApplicationInfo) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        try {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels
            val margin = (screenW * 0.04f).toInt()
            val gap = (screenW * 0.03f).toInt()
            val windowW = ((screenW - margin * 2 - gap) / 2).coerceAtLeast(320)
            val top = (screenH * 0.10f).toInt()
            val bottom = (screenH * 0.78f).toInt()
            val index = launchedPackages.indexOf(app.packageName)
            val left = if (index == 1) margin + windowW + gap else margin
            val bounds = Rect(left, top, left + windowW, bottom)

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )

            val options = if (Build.VERSION.SDK_INT >= 24) {
                ActivityOptions.makeBasic().apply {
                    setLaunchBounds(bounds)
                }
            } else null

            closePicker()
            if (options != null) startActivity(intent, options.toBundle())
            else startActivity(intent)

            if (!launchedPackages.contains(app.packageName)) {
                if (launchedPackages.size >= 2) launchedPackages.removeAt(0)
                launchedPackages.add(app.packageName)
            }
        } catch (_: Exception) {
            closePicker()
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                showPicker()
            }
        }
    }

    private fun closePicker() {
        picker?.let { try { wm?.removeView(it) } catch (_:Exception){} }
        picker=null
    }

    override fun onDestroy() {
        closePicker()
        dock?.let { try { wm?.removeView(it) } catch (_:Exception){} }
        dock=null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
