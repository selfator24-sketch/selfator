package com.selfator.lock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

// يراقب التطبيق اللي على الشاشة، ولو مش Selfator يرجّع الطالب فوراً
class LockService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var cur: String? = null
    private val allowed = setOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.vdocipher.zenplayer"
    )
    private val loop = object : Runnable {
        override fun run() { tick(); h.postDelayed(this, 400) }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lock", "Selfator", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "lock")
            .setContentTitle("وضع المذاكرة شغال")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock).build()
        startForeground(1, n)
        h.removeCallbacks(loop); h.post(loop)
        return START_STICKY
    }

    private fun tick() {
        val end = getSharedPreferences("s", MODE_PRIVATE).getLong("endAt", 0)
        if (System.currentTimeMillis() >= end) { stopSelf(); return }
        val um = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(now - 30000, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) cur = e.packageName
        }
        val p = cur ?: return
        if (p != packageName && p !in allowed) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    override fun onDestroy() { h.removeCallbacks(loop); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
