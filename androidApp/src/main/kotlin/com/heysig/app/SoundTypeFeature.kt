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
    private val openProgramPreview:(()->Unit)?=null,
    private val soundSettings:()->NoiseSettings={NoiseSettings()},
    private val fittingSignature:()->String={""}
) {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var active=false
    private var generation=0
    private var endsAt=0L
    private var nextAt=0L
    private var observation=SoundTypeObservation()
    @Volatile private var capturedType=SoundType.UNKNOWN
    @Volatile private var detailedCheck=false
    @Volatile private var capturedCheck:AcousticCheckResult?=null
    @Volatile private var capturedScene=AutomaticScene.UNKNOWN
    @Volatile private var capturedCombined:MaskingEvidence?=null
    private var status="Off. Your hearing-aid programs will not change."
    private var statusView: TextView?=null
    private var toggle: Button?=null
    private fun allowed()=active && safety().let { it.foreground && it.unlocked && it.audioFree }
    private val audio=EnvironmentAudio(activity,{allowed()},
        { cancelled -> MicrophoneLease.acquire(cancelled) },{ MicrophoneLease.release() },
        observeFrames={ frames -> capturedType=SoundTypeClassifier.classify(frames) },
        observeEvidence={ pcm,embedding,frames ->
            if(detailedCheck) {
                val analyzer=CombinedSoundAnalyzer(activity)
                val raw=analyzer.classify(pcm,embedding,frames,soundSettings()) { !allowed() }
                val memory=AutomaticMemoryStore(activity)
                val result=CombinedAutomatic.corrected(raw,memory.recall(AutomaticMemoryStore.scope("phone",fittingSignature()),raw.soundContext))
                capturedCombined=result
                capturedScene=result.scene ?: AutomaticScene.UNKNOWN
                val bands=activity.assets.open("separation/bands.txt").bufferedReader().use { SeparationMaskingBands.decode(it.readText()) }
                capturedCheck=AcousticCheckResult(null,analyzer.lastSections.map {
                    AcousticCheckWindow(it.masking.severity,it.separation,bands.severity(it.separation))
                })
            }
        })
    val running get()=active
    val busy get()=audio.busy()
    fun stop(reason: String="Off. Sound checks stopped.") {
        active=false;generation++;audio.stop();handler.removeCallbacksAndMessages(null)
        observation=SoundTypeObservation();capturedType=SoundType.UNKNOWN;detailedCheck=false;capturedCheck=null;capturedCombined=null;capturedScene=AutomaticScene.UNKNOWN;status=reason;update()
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
    private fun detailed() {
        if(active || audio.busy()) { stop("Sound check cancelled. Wait for the microphone to stop, then try again.");return }
        AlertDialog.Builder(activity).setTitle("Estimate noise for ten seconds?")
            .setMessage("Keep this screen open and the phone unlocked, near your usual listening position. The phone microphone will listen for ten seconds, then estimate speech masking and noise in three sections. Processing can take a little longer.\n\nSound stays in memory and is discarded. This check saves no recording or transcript and uploads no audio. Its latest compact summary stays in memory for two minutes; Teach Automatic saves a correction only after you review and tap Remember. Leaving this screen or tapping On to turn it Off cancels the check.\n\nStarting stops Automatic and other listening features. Your hearing-aid programs and fitting will not change.")
            .setNegativeButton("Cancel",null).setPositiveButton("Start check") { _,_ ->
                if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
                    status="Allow microphone access, then tap Estimate noise again.";update()
                } else if(!safety().let { it.foreground && it.unlocked && it.audioFree } || audio.busy()) {
                    status="Keep HeySig open and unlocked. Wait until other audio or commands finish.";update()
                } else {
                    prepare();active=true;detailedCheck=true;capturedCheck=null;capturedCombined=null;capturedScene=AutomaticScene.UNKNOWN;generation++
                    endsAt=SystemClock.elapsedRealtime()+120_000;nextAt=0
                    status="Listening for ten seconds, then estimating noise levels… Tap On to cancel."
                    handler.post(tick);update()
                }
            }.show()
    }
    private fun describe(check:AcousticCheckResult):String {
        fun number(value:Double?)=value?.let { "%.3f".format(java.util.Locale.US,it) } ?: "unavailable"
        fun level(severity:MaskingSeverity)=when(severity) {
            MaskingSeverity.QUIET -> "Quiet"
            MaskingSeverity.CLEAR -> "Normal"
            MaskingSeverity.MODERATE -> "Noisy"
            MaskingSeverity.HEAVY -> "Very Noisy"
            MaskingSeverity.UNKNOWN -> "Uncertain"
        }
        return "Ten-second check complete. No program changed.\nCombined Automatic result: "+capturedScene.label+".\n"+
            "Current detector: ${level(check.agreement())}. Noise-reduction estimate: ${level(check.agreement(true))}.\n"+
            check.windows.mapIndexed { index,w ->
                "Section ${index+1}: detector ${level(w.masking)}, reduction ${level(w.reductionSeverity)}; retained/background ${number(w.separation.retainedToResidualDb)} dB; retained ${number(w.separation.retainedDbfs)}, removed ${number(w.separation.residualDbfs)} dBFS; speech ${number(w.separation.speechScore)}; ${w.separation.status}."
            }.joinToString("\n")+
            "\nThe three sections cover seconds 0–9. Removed sound can include speech; these estimates do not measure what you understand."
    }
    private val tick=object: Runnable {
        override fun run() {
            if(!active) return
            val now=SystemClock.elapsedRealtime()
            if(now>=endsAt) { stop(if(detailedCheck) "Sound check timed out. No program changed." else "Five-minute check finished. Tap Off to try again.");return }
            if(!safety().let { it.foreground && it.unlocked }) { stop("Check stopped. Keep HeySig open and unlocked to try again.");return }
            if(!allowed()) {
                observation=SoundTypeObservation();status="Paused while the phone is speaking or busy.";update()
            } else if(!audio.busy() && now>=nextAt) {
                val token=generation
                capturedType=SoundType.UNKNOWN;nextAt=now+10_000
                val detailed=detailedCheck
                audio.sample(if(detailed) 10 else 3) { result,error,capturedAt ->
                    val type=capturedType
                    val check=capturedCheck
                    val combined=capturedCombined
                    handler.post {
                        if(!active || token!=generation) return@post
                        if(!allowed()) { observation=SoundTypeObservation();status="Check interrupted. Waiting to try again." }
                        else if(result==null || error!=null) {
                            stop("Could not check the sound. Make sure the microphone is available, then try again.");return@post
                        } else if(detailed) {
                            AutomaticMemoryStore.observe("phone",fittingSignature(),combined?.soundContext,combined?.scene ?: AutomaticScene.UNKNOWN)
                            stop(check?.let { describe(it) } ?: "Sound check incomplete. No program changed.");return@post
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
        PhoneStyle.heading(activity,card,"Noise estimate")
        fun line(value:String)=TextView(activity).also { it.text=value;PhoneStyle.text(it);it.setPadding(0,8,0,16);card.addView(it) }
        line("This estimates speech masking and background noise. It only shows a description; your hearing aids will not change.")
        openProgramPreview?.let { open -> card.addView(Button(activity).apply { text="Choose Automatic noise programs";setOnClickListener { stop();open() } }) }
        toggle=Button(activity).also { button ->
            card.addView(button);button.setOnClickListener { if(active) stop() else start() }
        }
        statusView=line(status).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        card.addView(Button(activity).apply {
            text="Estimate noise (10 seconds)";PhoneStyle.action(this);setOnClickListener { detailed() }
        })
        line("Mostly speech • Speech and noise • Mostly noise\nIf HeySig cannot tell, it says Not sure yet.")
        line("Point the phone toward what you are listening to. Keep this screen open and the phone unlocked. Use comfortable, everyday sounds. You do not need to make loud sounds for this check.")
        line("This is a try-out. Detecting speech does not mean knowing whether you can understand it. This check never changes programs. To use Automatic afterward, go back to Noise and turn it on.")
        update()
    }
}
