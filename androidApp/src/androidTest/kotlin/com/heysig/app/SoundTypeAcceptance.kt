package com.heysig.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.heysig.audio.Yamnet
import com.heysig.noise.*
import java.util.concurrent.atomic.AtomicBoolean

/** UI, native scalar evidence and optional foreground mic; no aid executor or fitting writes. */
class SoundTypeAcceptance: Instrumentation() {
    private var microphone=false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);microphone=arguments?.getString("microphone")=="true";start() }
    override fun onStart() {
        val result=Bundle()
        var screen: Activity?=null
        var feature: SoundTypeFeature?=null
        val prefs=targetContext.getSharedPreferences("acceptance-isolated-sound-type",0)
        val foreground=AtomicBoolean(true)
        fun descendants(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        fun waitUntil(label: String,condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+20_000
            while(SystemClock.elapsedRealtime()<end) {
                var ready=false;runOnMainSync { ready=condition() }
                if(ready) return
                SystemClock.sleep(100)
            }
            error("Timeout: $label")
        }
        try {
            screen=startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val activity=checkNotNull(screen)
            waitForIdleSync()
            fun open(label:String) {
                runOnMainSync { descendants(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString()==label }.performClick() }
                waitForIdleSync()
            }
            open("Noise");open("Check speech and noise")
            runOnMainSync {
                val texts=descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().map { it.text.toString() }
                check(texts.any { it.contains("Mostly speech") && it.contains("Speech and noise") && it.contains("Mostly noise") })
                check(texts.any { it.contains("Your hearing aids will not change") })
                check(!AutomaticNoiseService.active)
            }
            open("‹ Back")
            runOnMainSync {
                check(descendants(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text.toString()=="Check speech and noise" })
            }
            // Same pinned JNI encoder, explicitly licensed test-only fixtures. No audio playback.
            val predictions=mutableListOf<String>()
            for(name in listOf("quiet.pcm","conversation.pcm","non_speech_noise.pcm")) {
                val bytes=context.assets.open("scene-fixtures/$name").use { it.readBytes() }
                val pcm=ShortArray(bytes.size/2) { i -> ((bytes[2*i].toInt() and 255) or (bytes[2*i+1].toInt() shl 8)).toShort() }
                var frames: List<Map<String,Double>>?=null
                try {
                    Yamnet(targetContext,observeFrames={ frames=it }).use { it.classify(pcm) }
                    val captured=checkNotNull(frames)
                    check(captured.size in 3..32 && captured.all { it.keys.containsAll(EnvironmentClassifier.requiredLabels) })
                    predictions.add("$name: ${SoundTypeClassifier.classify(captured).label}")
                } finally { pcm.fill(0);bytes.fill(0) }
            }
            result.putString("native",predictions.joinToString("; ")+"; integration evidence, not accuracy qualification")
            check(prefs.edit().clear().putBoolean("disclosureV1",true).commit())
            runOnMainSync {
                val column=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
                val scroll=ScrollView(activity).apply { addView(column) }
                activity.setContentView(scroll)
                feature=SoundTypeFeature(activity,{
                    NoiseSafety(foreground=foreground.get(),unlocked=!activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked,audioFree=true)
                },{},prefs)
                feature!!.render(column)
            }
            waitForIdleSync()
            if(microphone) {
                check(targetContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED) { "Microphone permission must be granted for the explicitly requested test" }
                open("Off")
                waitUntil("microphone started") { feature!!.running && feature!!.busy }
                SystemClock.sleep(15_000)
                runOnMainSync {
                    check(feature!!.running)
                    val texts=descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().map { it.text.toString() }
                    result.putString("liveDescription",texts.first { it in SoundType.entries.map { t -> t.label } || it.startsWith("Not sure yet.") || it.startsWith("Checking again") || it.startsWith("Listening…") })
                    foreground.set(false)
                }
                waitUntil("foreground loss stopped check") { !feature!!.running && !feature!!.busy }
                result.putString("capture","PASS: bounded foreground microphone and cancellation; no recording, transcript or program command")
            }
            runOnMainSync {
                val root=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                val directory=java.io.File(targetContext.getExternalFilesDir(null),"ui-previews").apply { mkdirs() }
                java.io.File(directory,"sound-type.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            check(!EnvironmentReleaseGate.qualified())
            result.putString("result","PASS: new route, plain labels, no Automatic arming, native event frames and foreground-only display controller")
            finish(Activity.RESULT_OK,result)
        } catch(e: Throwable) { result.putString("result","FAIL: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
        finally {
            runOnMainSync { feature?.stop();screen?.finish() }
            prefs.edit().clear().commit()
        }
    }
}
