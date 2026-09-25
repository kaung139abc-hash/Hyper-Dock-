package com.kaung139abc.hyperdock

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var dock: View? = null
    override fun onCreate() { super.onCreate(); startForeground(7, notification()); showDock() }
    private fun notification(): Notification {
        val channel = "hyper_dock"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel,"Hyper Dock",NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this,channel).setContentTitle("Hyper Dock").setContentText("Floating dock is running").setSmallIcon(R.drawable.ic_hyperdock).build()
    }
    private fun showDock() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE)
        val box = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(8,6,8,6); setBackgroundColor(Color.argb(235,20,20,26)) }
        val label = TextView(this).apply { text="☰"; textSize=22f; setTextColor(Color.WHITE); setPadding(12,4,12,4) }
        box.addView(label)
        label.setOnClickListener { label.text = if (label.text=="☰") "×" else "☰" }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT)
        p.gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; p.y=80
        wm?.addView(box,p); dock=box
        box.setOnTouchListener(object: View.OnTouchListener {
            var sx=0f; var sy=0f; var wx=0; var wy=0
            override fun onTouch(v:View,e:android.view.MotionEvent):Boolean = when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; wx=p.x; wy=p.y; true }
                MotionEvent.ACTION_MOVE -> { p.x=wx+(e.rawX-sx).toInt(); p.y=wy+(e.rawY-sy).toInt(); wm?.updateViewLayout(v,p); true }
                else -> true
            }
        })
    }
    override fun onDestroy(){ dock?.let{wm?.removeView(it)}; dock=null; super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder? = null
}
