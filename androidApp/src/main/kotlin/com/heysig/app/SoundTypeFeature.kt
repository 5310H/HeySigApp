package com.heysig.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import com.heysig.audio.EnvironmentAudio
import com.heysig.noise.*

/** Foreground-only display experiment. No fitting, command executor or program-send dependency. */
class SoundTypeFeature(
    private val activity: Activity,
    private val safety: () -> NoiseSafety,
    private val prepare: () -> Unit,
    private val prefs: android.content.SharedPreferences=activity.getSharedPreferences("heysig-sound-type-v1",0),
    private val openProgramPreview:(()->Unit)?=null
) {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var active=false
    private var generation=0
    private var endsAt=0L
    private var nextAt=0L
    private var observation=SoundTypeObservation()
    @Volatile private var capturedType=SoundType.UNKNOWN
    private var status="Off. Your hearing-aid programs will not change."
    private var statusView: TextView?=null
    private var toggle: Button?=null
    private fun allowed()=active && safety().let { it.foreground && it.unlocked && it.audioFree }
    private val audio=EnvironmentAudio(activity,{allowed()},
        { cancelled -> MicrophoneLease.acquire(cancelled) },{ MicrophoneLease.release() },
        observeFrames={ frames -> capturedType=SoundTypeClassifier.classify(frames) })
    val running get()=active
    val busy get()=audio.busy()
    fun stop(reason: String="Off. Sound checks stopped.") {
        active=false;generation++;audio.stop();handler.removeCallbacksAndMessages(null)
        observation=SoundTypeObservation();capturedType=SoundType.UNKNOWN;status=reason;update()
    }
    fun detach() { statusView=null;toggle=null }
    private fun update() {
        statusView?.text=status
        toggle?.apply {
            text=if(active) "On" else "Off"
            contentDescription=if(active) "Sound check On. Tap to turn Off" else "Sound check Off. Tap to turn On"
            PhoneStyle.action(this,if(active) "noiseOn" else "noiseOff")
        }
    }
    private fun start() {
        if(active) return
        if(!safety().let { it.foreground && it.unlocked }) { status="Keep HeySig open and the phone unlocked for this check.";update();return }
        if(!prefs.getBoolean("disclosureV1",false)) {
            AlertDialog.Builder(activity).setTitle("Check speech and noise?")
                .setMessage("This try-out uses the phone microphone for three seconds at a time, with a pause between checks. Keep HeySig open and unlocked. Checks stop when you leave this screen, lock the phone, tap On to turn it Off, or after five minutes.\n\nIt guesses whether sound is mostly speech, speech with noise, or other noise. It cannot tell which person you want to hear, or how clearly you hear words. Sometimes it will say Not sure yet.\n\nThe model runs on this phone. Sound is processed in memory and discarded. This check saves no recording, transcript, sound history or answers, and uploads no audio.\n\nStarting this check stops Automatic and other listening features. It will not change your hearing-aid programs or fitting.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("disclosureV1",true).commit()) start()
                    else { status="Could not save your microphone choice. Check remains Off.";update() }
                }.show()
            return
        }
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
            status="Allow microphone access, then tap Off to start the check.";update();return
        }
        if(!safety().audioFree) { status="Wait until the phone finishes speaking or sending a command, then try again.";update();return }
        prepare()
        active=true;generation++;endsAt=SystemClock.elapsedRealtime()+5*60_000;nextAt=0
        observation=SoundTypeObservation();status="Listening… A description appears after two matching checks."
        handler.postDelayed(tick,500);update()
    }
    private val tick=object: Runnable {
        override fun run() {
            if(!active) return
            val now=SystemClock.elapsedRealtime()
            if(now>=endsAt) { stop("Five-minute check finished. Tap Off to try again.");return }
            if(!safety().let { it.foreground && it.unlocked }) { stop("Check stopped. Keep HeySig open and unlocked to try again.");return }
            if(!allowed()) {
                observation=SoundTypeObservation();status="Paused while the phone is speaking or busy.";update()
            } else if(!audio.busy() && now>=nextAt) {
                val token=generation
                capturedType=SoundType.UNKNOWN;nextAt=now+10_000
                audio.sample(3) { result,error,capturedAt ->
                    val type=capturedType
                    handler.post {
                        if(!active || token!=generation) return@post
                        if(!allowed()) { observation=SoundTypeObservation();status="Check interrupted. Waiting to try again." }
                        else if(result==null || error!=null) {
                            stop("Could not check the sound. Make sure the microphone is available, then try again.");return@post
                        } else {
                            observation=observation.next(type,capturedAt,SystemClock.elapsedRealtime())
                            status=if(observation.shown!=SoundType.UNKNOWN) observation.shown.label
                                else if(observation.count==1) "Checking again before showing a description…"
                                else "Not sure yet. Quiet, music or changing sounds can be hard to identify."
                        }
                        update()
                    }
                }
            }
            handler.postDelayed(this,500)
        }
    }
    fun render(content: LinearLayout) {
        val card=PhoneStyle.card(activity,content)
        PhoneStyle.heading(activity,card,"What does HeySig hear?")
        fun line(value:String)=TextView(activity).also { it.text=value;PhoneStyle.text(it);it.setPadding(0,8,0,16);card.addView(it) }
        line("Try a sound check. It only shows a description. Your hearing aids will not change.")
        openProgramPreview?.let { open -> card.addView(Button(activity).apply { text="Choose Automatic programs for speech";setOnClickListener { stop();open() } }) }
        toggle=Button(activity).also { button ->
            card.addView(button);button.setOnClickListener { if(active) stop() else start() }
        }
        statusView=line(status).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        line("Mostly speech • Speech and noise • Mostly noise\nIf HeySig cannot tell, it says Not sure yet.")
        line("Point the phone toward what you are listening to. Keep this screen open and the phone unlocked. Use comfortable, everyday sounds. You do not need to make loud sounds for this check.")
        line("This is a try-out. Detecting speech does not mean knowing whether you can understand it. This check never changes programs. To use Automatic afterward, go back to Noise and turn it on.")
        update()
    }
}
