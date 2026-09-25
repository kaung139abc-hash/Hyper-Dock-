package com.kaung139abc.hyperdock

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
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var dock: View? = null
    private var picker: View? = null
    private val selected = mutableListOf<ApplicationInfo>()
    private val windows = mutableListOf<View>()

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
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else WindowManager.LayoutParams.TYPE_PHONE

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
        var sx = 0f
        var sy = 0f
        var wx = 0
        var wy = 0
        var moved = false
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; wx=p.x; wy=p.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX-sx)>8 || kotlin.math.abs(e.rawY-sy)>8) moved=true
                    p.x=wx+(e.rawX-sx).toInt()
                    p.y=wy+(e.rawY-sy).toInt()
                    wm?.updateViewLayout(view,p)
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) showPicker(); true }
                else -> true
            }
        }
    }

    private fun showPicker() {
        if (picker != null) return
        val panel = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(12,12,12,12)
            background=bg(Color.rgb(22,24,31),24f)
            elevation=20f
        }
        val head=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text="Choose apps (" + selected.size + "/2)"
            textSize=17f
            setTextColor(Color.WHITE)
            layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        })
        head.addView(TextView(this).apply {
            text="×"
            textSize=25f
            setTextColor(Color.WHITE)
            setPadding(18,4,8,4)
            setOnClickListener { closePicker() }
        })
        panel.addView(head)
        val scroll=ScrollView(this)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val pm=packageManager
        val apps=pm.getInstalledApplications(0).filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM)==0 &&
            pm.getLaunchIntentForPackage(it.packageName)!=null
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        apps.forEach { app ->
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                setPadding(8,10,8,10)
                setOnClickListener { chooseApp(app) }
            }
            row.addView(ImageView(this).apply {
                setImageDrawable(pm.getApplicationIcon(app))
                layoutParams=LinearLayout.LayoutParams(48,48)
            })
            row.addView(TextView(this).apply {
                text=pm.getApplicationLabel(app).toString()
                textSize=15f
                setTextColor(Color.WHITE)
                setPadding(14,0,8,0)
                layoutParams=LinearLayout.LayoutParams(0,-2,1f)
            })
            list.addView(row)
        }
        scroll.addView(list)
        panel.addView(scroll, LinearLayout.LayoutParams(300,520))
        val p=params(324,-2,18,150)
        wm?.addView(panel,p)
        picker=panel
    }

    private fun chooseApp(app: ApplicationInfo) {
        if (!selected.any { it.packageName == app.packageName }) {
            if (selected.size >= 2) selected.removeAt(0)
            selected.add(app)
        }
        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(it) } catch (_:Exception) {}
        }
        closePicker()
        showSelectedWindows()
    }

    private fun showSelectedWindows() {
        windows.forEach { try { wm?.removeView(it) } catch (_:Exception){} }
        windows.clear()
        val sw=resources.displayMetrics.widthPixels
        val w=(sw*0.43f).toInt()
        selected.take(2).forEachIndexed { i, app ->
            val root=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(10,8,10,10)
                background=bg(Color.argb(240,18,20,27),20f)
            }
            root.addView(TextView(this).apply {
                text=packageManager.getApplicationLabel(app).toString()
                textSize=14f
                setTextColor(Color.WHITE)
                setPadding(4,4,4,8)
            })
            root.addView(TextView(this).apply {
                text="Selected app. Use the floating Hyper Dock control to switch."
                gravity=Gravity.CENTER
                setTextColor(Color.LTGRAY)
                textSize=12f
            }, LinearLayout.LayoutParams(-1,0,1f))
            val x=if(i==0) 12 else sw-w-12
            val p=params(w,180,x,700)
            wm?.addView(root,p)
            windows.add(root)
            makeDraggable(root,p)
        }
    }

    private fun closePicker() {
        picker?.let { try { wm?.removeView(it) } catch (_:Exception){} }
        picker=null
    }

    private fun makeDraggable(v:View,p:WindowManager.LayoutParams) {
        var sx=0f; var sy=0f; var wx=0; var wy=0
        v.setOnTouchListener { view,e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;wx=p.x;wy=p.y;true}
                MotionEvent.ACTION_MOVE->{p.x=wx+(e.rawX-sx).toInt();p.y=wy+(e.rawY-sy).toInt();wm?.updateViewLayout(view,p);true}
                else->true
            }
        }
    }

    override fun onDestroy() {
        closePicker()
        windows.forEach { try { wm?.removeView(it) } catch (_:Exception){} }
        windows.clear()
        dock?.let { try { wm?.removeView(it) } catch (_:Exception){} }
        dock=null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
