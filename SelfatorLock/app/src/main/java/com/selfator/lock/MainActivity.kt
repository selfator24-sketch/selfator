package com.selfator.lock

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

// غيّر الرابط هنا لو الموقع اتغير
const val SITE_URL = "https://selfator.ai.studio"
// أمان: القفل مهما حصل ما يزيدش عن الوقت ده (بالدقائق) لو الموقع ما قفلهوش
const val MAX_LOCK_MIN = 240L

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pending = false
    private lateinit var bar: TextView
    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val left = prefs.getLong("endAt", 0) - System.currentTimeMillis()
            if (left > 0) {
                val s = left / 1000
                bar.text = "وضع المذاكرة • المتبقي %02d:%02d".format(s / 60, s % 60)
                bar.visibility = View.VISIBLE
            } else bar.visibility = View.GONE
            h.postDelayed(this, 1000)
        }
    }
    private var prevFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    private val prefs get() = getSharedPreferences("s", MODE_PRIVATE)
    private fun active() = System.currentTimeMillis() < prefs.getLong("endAt", 0)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        bar = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(0xCC000000.toInt())
            textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 12); visibility = View.GONE
        }
        val frame = FrameLayout(this)
        frame.addView(web)
        frame.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(frame)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (u.startsWith("http://") || u.startsWith("https://")) return false
                // روابط تفتح تطبيق خارجي (زي مشغل الفيديو)
                try {
                    val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                            else Intent(Intent.ACTION_VIEW, r.url)
                    i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                    startActivity(i)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "ثبّت مشغل الفيديو (Zenplayer) من Google Play أولاً", Toast.LENGTH_LONG).show()
                }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "SelfatorNative")
        web.loadUrl(SITE_URL)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (active()) return
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    override fun onPause() { h.removeCallbacks(ticker); super.onPause() }

    override fun onResume() {
        super.onResume()
        h.post(ticker)
        if (pending) lock(MAX_LOCK_MIN)
    }

    private fun hasUsage(): Boolean {
        val a = getSystemService(AppOpsManager::class.java)
        return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    // الصلاحيات المطلوبة مرة واحدة: العرض فوق التطبيقات + الوصول للاستخدام + عدم الإزعاج
    private fun lock(minutes: Long) {
        pending = true
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات» لـ Selfator ثم ارجع", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!hasUsage()) {
            Toast.makeText(this, "فعّل «الوصول إلى بيانات الاستخدام» لـ Selfator ثم ارجع", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        pending = false
        prefs.edit().putLong("endAt", System.currentTimeMillis() + minutes * 60000).apply()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE)
                prevFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
        startForegroundService(Intent(this, LockService::class.java))
    }

    private fun unlock() {
        pending = false
        prefs.edit().putLong("endAt", 0).apply()
        stopService(Intent(this, LockService::class.java))
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(prevFilter)
    }

    inner class Bridge {
        @JavascriptInterface fun startLock() { runOnUiThread { lock(MAX_LOCK_MIN) } }
        @JavascriptInterface fun startLockFor(minutes: Int) { runOnUiThread { lock(minutes.toLong()) } }
        @JavascriptInterface fun stopLock() { runOnUiThread { unlock() } }
        @JavascriptInterface fun isLocked(): Boolean = active()
    }
}
