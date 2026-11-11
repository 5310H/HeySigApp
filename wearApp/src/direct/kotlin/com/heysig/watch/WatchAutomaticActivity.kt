package com.heysig.watch

import android.Manifest
import android.app.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.widget.*

/** Explicit watch microphone consent and visible controls; leaving does not end an opted-in session. */
class WatchAutomaticActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status:TextView
    private lateinit var toggle:Button
    private var message:String?=null
    private val refresh=object:Runnable { override fun run() { update();handler.postDelayed(this,1000) } }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val screen=WatchAutomaticLayout.create(this,"Build ${BuildConfig.GIT_BUILD_ID} · ${BuildConfig.GIT_BUILD_DATE}",{
            message=null
            if(WatchAutomaticService.running!=null) WatchAutomaticService.stop(this)
            else runCatching { start() }.onFailure { message=it.message;update() }
        },{ finish() })
        status=screen.status;toggle=screen.toggle;setContentView(screen.view)
    }
    override fun onResume() { super.onResume();handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh);super.onPause() }
    private fun update() {
        val service=WatchAutomaticService.running
        toggle.text=if(service==null) "On" else "Off"
        toggle.contentDescription=if(service==null) "Turn watch Automatic On" else "Turn watch Automatic Off"
        status.text=service?.summary() ?: message ?: WatchAutomaticService.lastStatus.ifBlank { "Watch Automatic Off. Microphone stopped." }
    }
    private fun start() {
        check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock the watch to start Automatic" }
        val prefs=getSharedPreferences("heysig-watch-v1",MODE_PRIVATE)
        check(prefs.getString("phone",null)!=null) { "Select your phone in More → Connect phone first" }
        if(!prefs.getBoolean("watchAutomaticDisclosureV1",false)) {
            AlertDialog.Builder(this).setTitle("Watch microphone Automatic")
                .setMessage("When you tap On, this watch captures three seconds, then waits at least ten seconds between checks. Speech detection and neural noise reduction run on this watch. Audio, cleaned sound and removed background stay in memory and are discarded; no recordings, transcripts or noise profiles are saved.\n\nOnly compact estimates, session controls and status cross Google's encrypted Wear OS Data Layer, which may route through Google servers. Your selected phone can send real hearing-aid program tones using your saved Universal, Noisy and Very Noisy choices. The phone microphone stays idle in this mode. The model cannot identify your intended speaker or measure understanding.\n\nA visible watch notification offers Off. Both screens may sleep; capture can continue while the watch is locked after this explicit start. Off on either device, disconnect, permission removal, manual control, Therapy, app stop or eight-hour expiry ends Automatic. No reboot or automatic restart. Starting stops watch voice/shake microphone sessions. Battery and wrist-position accuracy are not yet qualified.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("watchAutomaticDisclosureV1",true).commit()) runCatching { start() }.onFailure { message=it.message;update() }
                }.show();return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),741);return
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),742);return
        }
        WatchAutomaticService.launch(this);update()
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,grants:IntArray) {
        super.onRequestPermissionsResult(code,permissions,grants)
        if(code==741 || code==742) { message=if(grants.isNotEmpty() && grants.all { it==PackageManager.PERMISSION_GRANTED }) "Permission allowed. Tap On when ready." else "Permission denied. Watch Automatic stays Off.";update() }
    }
}
