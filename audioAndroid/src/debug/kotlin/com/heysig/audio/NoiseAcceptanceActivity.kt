package com.heysig.audio

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Development instrumentation only. No preferences, companion connection or aid executor. */
class NoiseAcceptanceActivity: Activity() {
    @Volatile var foreground=false;private set
    @Volatile var access=true
    @Volatile var cancelled=false;private set
    @Volatile var eventScores: Map<String,Double> = emptyMap();private set
    val audio by lazy { EnvironmentAudio(this,{ foreground && access && !cancelled &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked },observeScores={ eventScores=it }) }
    private lateinit var status: TextView
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val density=resources.displayMetrics.density
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding((28*density).toInt(),(24*density).toInt(),(28*density).toInt(),(36*density).toInt())
        }
        status=TextView(this).apply {
            text="Noise acceptance tests\nNo hearing-aid commands. Microphone samples are bounded to 3–10 seconds, processed locally in memory and discarded. No audio or transcript is saved or sent. Only timing, memory, coarse sound-event scores and test results are returned."
            textSize=16f;setTextColor(Color.WHITE)
            accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text="Allow microphone tests";minHeight=(48*density).toInt()
            setOnClickListener {
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),881)
            }
        })
        layout.addView(Button(this).apply {
            text="Stop tests";minHeight=(48*density).toInt()
            setOnClickListener { cancelled=true;audio.stop();finish() }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK);addView(layout) })
    }
    fun show(message: String) { status.append("\n$message") }
    override fun onResume() { super.onResume();foreground=true }
    override fun onPause() { foreground=false;audio.stop();super.onPause() }
    override fun onDestroy() { audio.stop();super.onDestroy() }
}
