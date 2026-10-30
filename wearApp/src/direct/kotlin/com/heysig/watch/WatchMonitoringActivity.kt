package com.heysig.watch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.widget.*
import com.heysig.audio.EnvironmentAudio
import com.heysig.noise.*
import org.json.JSONObject
import java.util.UUID

/** Explicit, foreground-only intermittent monitoring. All aid control remains on phone. */
class WatchMonitoringActivity: Activity() {
    private val client by lazy { WatchVoiceClient(this) }
    private val handler=Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("heysig-watch-v1",MODE_PRIVATE) }
    @Volatile private var foreground=false
    @Volatile private var monitoring=false
    private var connected=false
    private var generation=0
    private var session=UUID.randomUUID().toString()
    private var sequence=0L
    private var startedAt=0L
    private var schedule=SamplingSchedule()
    private var programs=emptyList<Pair<Int,String>>()
    private lateinit var status: TextView
    private val audio by lazy { EnvironmentAudio(this,{ foreground && monitoring &&
        !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked }) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val dp=resources.displayMetrics.density
            setPadding((28*dp).toInt(),(24*dp).toInt(),(28*dp).toInt(),(36*dp).toInt())
        }
        fun line(text: String)=TextView(this).apply { this.text=text;textSize=16f;setTextColor(Color.WHITE);layout.addView(this) }
        fun button(text: String, action: ()->Unit) { layout.addView(Button(this).apply {
            this.text=text;minHeight=(48*resources.displayMetrics.density).toInt();setOnClickListener { action() }
        }) }
        line("Listening environments")
        status=line("Connect with HeySig open on your phone").apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        button("Connect / refresh status") { connect() }
        button("Start monitoring") { start() }
        button("Stop monitoring") { stop("Monitoring off",notify=true) }
        button("Automatic on") { command("automatic on") }
        button("Automatic off") { command("automatic off") }
        button("Choose program") {
            if(!connected || programs.isEmpty()) { status.text="Connect / refresh status first";return@button }
            AlertDialog.Builder(this).setTitle("Phone programs").setItems(programs.map { it.second }.toTypedArray()) { _,i ->
                stop("Manual program selection",notify=true)
                command("program ${programs[i].first}")
            }.show()
        }
        button("Voice command") {
            stop("Voice has microphone priority",notify=true)
            // Existing watch Speak control handles disclosure, permissions and bounded recognition.
            startActivity(android.content.Intent(this,WatchActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("environmentSpeak",true))
            finish()
        }
        line("Voice: return to Speak, then tap it. Monitoring stops before voice capture. Sampling settings come from Automatic program switching on your phone. Keep both apps visible and unlocked. Leaving this screen stops watch capture. Estimates are experimental.")
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK);addView(layout) })
    }
    override fun onResume() { super.onResume();foreground=true;connect() }
    override fun onPause() {
        foreground=false;stop("Foreground monitoring ended",notify=true)
        super.onPause()
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);client.close();super.onDestroy() }
    private fun connect() {
        if(monitoring) { status.text="Stop monitoring before reconnecting";return }
        val token=++generation
        client.connect { reply ->
            if(!foreground || token!=generation) return@connect
            connected=reply.optString("phase")=="READY"
            if(connected) client.environment(JSONObject().put("action","status")) { update(it) }
            else status.text=reply.optString("text","Disconnected")
        }
    }
    private fun update(reply: JSONObject) {
        if(!foreground) return
        status.text=reply.optString("text","No phone result")
        if(reply.optString("phase")!="FINAL") { stop("Phone disconnected or request rejected. Reconnect explicitly.",false);connected=false;return }
        try {
            schedule=SamplingSchedule(reply.getInt("interval"),reply.getInt("duration"),reply.getInt("timeout"))
            val list=reply.getJSONArray("programs")
            programs=(0 until list.length()).map { list.getJSONObject(it).let { p -> p.getInt("slot") to p.getString("name") } }
            if(monitoring && !reply.getBoolean("monitoring")) stop("Phone monitoring stopped",false)
        } catch(e: Exception) { stop("Invalid phone monitoring settings",false);connected=false }
    }
    private fun start() {
        if(monitoring) return
        if(!connected) { status.text="Connect to the selected phone first";return }
        if(!prefs.getBoolean("environmentDisclosureV1",false)) {
            AlertDialog.Builder(this).setTitle("Watch microphone sampling")
                .setMessage("Explicit monitoring samples this watch microphone intermittently while this screen is visible and unlocked. An offline model estimates speech/environment; audio stays in memory and is discarded. Only compact classifications, commands and status use Google's Data Layer, which may route through encrypted Google servers. No raw audio or transcript is transferred by monitoring. Starting ends watch shake voice to avoid microphone conflicts. The phone remains responsible for aid commands; automatic dispatch still requires physical acceptance. Stops on leaving, timeout, disconnection or Stop. The initial 3 seconds every 30 seconds needs accuracy and battery validation.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("environmentDisclosureV1",true).commit()) start()
                }.show();return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),733);return
        }
        if(getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { status.text="Unlock before monitoring";return }
        WatchVoiceSessionService.stop(this);SleepingShakeService.stop(this)
        session=UUID.randomUUID().toString();sequence=0;val token=++generation
        client.environment(JSONObject().put("action","start").put("session",session)) { reply ->
            if(!foreground || token!=generation) return@environment
            update(reply)
            if(connected && reply.optBoolean("monitoring",false)) {
                monitoring=true;startedAt=SystemClock.elapsedRealtime();handler.post(sample)
            }
        }
    }
    override fun onRequestPermissionsResult(code: Int,permissions: Array<out String>,grants: IntArray) {
        super.onRequestPermissionsResult(code,permissions,grants)
        if(code==733) status.text=if(grants.isNotEmpty() && grants.all { it==PackageManager.PERMISSION_GRANTED })
            "Microphone allowed. Tap Start monitoring when ready." else "Microphone denied. Monitoring off; manual phone controls remain available."
    }
    private val sample=object: Runnable {
        override fun run() {
            if(!foreground || !monitoring) return
            val now=SystemClock.elapsedRealtime()
            if(schedule.expired(startedAt,now)) { stop("Monitoring session expired",true);return }
            val token=generation
            status.text="Microphone active · ${schedule.durationSeconds} seconds"
            val started=audio.sample(schedule.durationSeconds) { result,error,capturedAt -> runOnUiThread {
                if(!foreground || !monitoring || token!=generation) return@runOnUiThread
                if(result==null) { stop(error ?: "Audio unavailable",true);return@runOnUiThread }
                val body=JSONObject().put("action","sample").put("session",session).put("sequence",sequence++)
                    .put("ageMillis",SystemClock.elapsedRealtime()-capturedAt)
                    .put("environment",result.environment?.id ?: 0).put("confidence",result.confidence)
                client.environment(body) { reply ->
                    if(!foreground || token!=generation) return@environment
                    update(reply)
                    if(monitoring) {
                        status.append("\nMicrophone idle between samples")
                        val delay=(now+schedule.intervalSeconds*1000L-SystemClock.elapsedRealtime()).coerceAtLeast(0)
                        handler.postDelayed(this,delay)
                    }
                }
            } }
            if(!started) stop("Previous capture is stopping; restart explicitly",true)
        }
    }
    private fun stop(reason: String,notify: Boolean) {
        generation++;monitoring=false;audio.stop();handler.removeCallbacks(sample)
        if(::status.isInitialized) status.text=reason
        if(notify && connected) client.environment(JSONObject().put("action","stop")) { /* Never retry. */ }
    }
    private fun command(text: String) {
        if(!connected) { status.text="Connect phone first";return }
        if(monitoring) stop("Manual control stopped monitoring",notify=true)
        val at=SystemClock.elapsedRealtime()
        client.command(text,listOf(text),UUID.randomUUID().toString(),{ foreground && SystemClock.elapsedRealtime()-at<15_000 }) {
            if(foreground) status.text=it.optString("text","No confirmed result")
        }
    }
}
