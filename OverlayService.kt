package com.example.quotebeast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var button: Button
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val channelId = "quote_beast_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Quote Beast", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            1,
            Notification.Builder(this, channelId)
                .setContentTitle("Quote Beast")
                .setContentText("Floating button on")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build()
        )

        button = Button(this).apply {
            text = "QB"
            textSize = 16f
        }

        params = WindowManager.LayoutParams(
            160,
            160,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 400
        }

        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        var dragged = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragged = false
                    downX = event.rawX.toInt()
                    downY = event.rawY.toInt()
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - downX
                    val dy = event.rawY.toInt() - downY
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) dragged = true
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) generate()
                    true
                }
                else -> false
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(button, params)
    }

    private fun setFocusable(on: Boolean) {
        params.flags = if (on) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(button, params)
    }

    private fun generate() {
        setFocusable(true)
        handler.postDelayed({
            val clip = getSystemService(ClipboardManager::class.java)
            val post = clip.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
                .orEmpty()
            val prefs = getSharedPreferences("quote_beast", 0)
            val key = prefs.getString("api_key", "") ?: ""
            val prompt = prefs.getString(
                "prompt",
                "Write ONE grounded motivational sentence. Maximum 11 words. Honest, no fluff. Output ONLY the sentence. Do not show thinking."
            ) ?: ""
            val model = prefs.getString("model", "qwen/qwen3.6-27b") ?: "qwen/qwen3.6-27b"
            val temperature = prefs.getString("temperature", "0.7") ?: "0.7"
            val lastOut = prefs.getString("last_result", "") ?: ""
            val source = if (post.isNotBlank() && post != lastOut) post else ""

            if (key.isBlank()) {
                Toast.makeText(this, "Save a Groq key in the app first", Toast.LENGTH_LONG).show()
                setFocusable(false)
                return@postDelayed
            }

            button.text = "..."
            CoroutineScope(Dispatchers.Main).launch {
                val out = generateBoost(key, source, prompt, model, temperature)
                button.text = "QB"
                setFocusable(false)
                if (out != null) {
                    prefs.edit().putString("last_result", out).apply()
                    clip.setPrimaryClip(ClipData.newPlainText("quote", out))
                    Toast.makeText(applicationContext, out, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(applicationContext, "Groq failed", Toast.LENGTH_SHORT).show()
                }
            }
        }, 250)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::button.isInitialized) windowManager.removeView(button)
    }
}