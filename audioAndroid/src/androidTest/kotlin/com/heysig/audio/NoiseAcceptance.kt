package com.heysig.audio

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import com.heysig.noise.EnvironmentClassification
import com.heysig.noise.EnvironmentReleaseGate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

/** Explicit runner for either debug app. No fitting/settings/transport access. */
class NoiseAcceptance: Instrumentation() {
    private var microphone=false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments);microphone=arguments?.getString("microphone")=="true";start()
    }
    override fun onStart() {
        val result=Bundle()
        var screen: NoiseAcceptanceActivity?=null
        var passed=false
        try {
            check(!EnvironmentReleaseGate.qualified())
            val intent=Intent().setClassName(targetContext.packageName,NoiseAcceptanceActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            screen=startActivitySync(intent) as NoiseAcceptanceActivity
            waitForIdleSync()
            val activity=checkNotNull(screen)
            fun show(text: String) = runOnMainSync { activity.show(text) }
            runOnMainSync {
                val labels=listOf("Connect / refresh status","Start monitoring","Stop monitoring","Automatic on","Automatic off","Choose program","Voice command")
                for(scale in listOf(1f,2f)) {
                    val configuration=android.content.res.Configuration(activity.resources.configuration).apply {
                        fontScale=scale;screenWidthDp=192;screenHeightDp=192
                        screenLayout=(screenLayout and android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or android.content.res.Configuration.SCREENLAYOUT_ROUND_YES
                    }
                    val context=android.view.ContextThemeWrapper(activity.createConfigurationContext(configuration),android.R.style.Theme_Material_NoActionBar)
                    val screen=com.heysig.watch.EnvironmentMonitoringLayout.create(context,labels.map { it to {} })
                    val size=(192*context.resources.displayMetrics.density).toInt()
                    val spec=android.view.View.MeasureSpec.makeMeasureSpec(size,android.view.View.MeasureSpec.EXACTLY)
                    screen.view.measure(spec,spec);screen.view.layout(0,0,size,size)
                    val column=screen.view.getChildAt(0) as android.widget.LinearLayout
                    val buttons=(0 until column.childCount).map { column.getChildAt(it) }.filterIsInstance<android.widget.Button>()
                    check(buttons.map { it.text.toString() }==labels)
                    val minimum=48*context.resources.displayMetrics.density
                    check(buttons.all { it.height>=minimum && it.width>=minimum }) { "Monitoring touch target below 48dp" }
                    check(buttons.all { button ->
                        button.layout.height <= button.height-button.compoundPaddingTop-button.compoundPaddingBottom &&
                            (0 until button.layout.lineCount).all { button.layout.getEllipsisCount(it)==0 }
                    }) { "Monitoring action text clipped at font scale $scale" }
                    for(button in buttons) {
                        screen.view.scrollTo(0,(button.top-size/2+button.height/2).coerceAtLeast(0))
                        check(button.top+button.height>screen.view.scrollY && button.top<screen.view.scrollY+size) { "Monitoring action unreachable" }
                    }
                }
            }
            result.putString("roundLayout","PASS: actual monitoring layout at 192dp round, font scales 1/2, >=48dp targets and scroll reachability")
            fun awaitIdle() {
                val deadline=SystemClock.elapsedRealtime()+5000
                while(activity.audio.busy() && SystemClock.elapsedRealtime()<deadline) Thread.sleep(10)
                check(!activity.audio.busy()) { "Capture failed to stop within 5 seconds" }
            }
            result.putLong("pssBeforeKb",Debug.getPss())
            // Synthetic in-memory PCM only: performance evidence, never accuracy evidence.
            for(seconds in listOf(3,5,10)) {
                show("Model benchmark · $seconds seconds of synthetic PCM · microphone off")
                val pcm=ShortArray(seconds*16000) { (sin(it*2.0*Math.PI*233/16000)*3000).toInt().toShort() }
                val wall=SystemClock.elapsedRealtime()
                val cpu=Debug.threadCpuTimeNanos()
                try {
                    Yamnet(activity).use { model ->
                        check(model.classify(pcm) { activity.cancelled }.confidence.isFinite())
                        check(runCatching { model.classify(pcm) { true } }.isFailure)
                    }
                } finally { pcm.fill(0) }
                result.putLong("model${seconds}WallMs",SystemClock.elapsedRealtime()-wall)
                result.putLong("model${seconds}CpuMs",(Debug.threadCpuTimeNanos()-cpu)/1_000_000)
            }
            result.putLong("pssAfterModelKb",Debug.getPss())
            if(microphone) {
                show("Microphone tests requested. If needed, tap Allow microphone tests and approve the system prompt.")
                val deadline=SystemClock.elapsedRealtime()+60_000
                while(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED &&
                    !activity.cancelled && SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
                check(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "Microphone permission not granted" }
                check(activity.foreground && !activity.cancelled) { "Keep acceptance screen open and unlocked" }
                for(seconds in listOf(3,5,10)) {
                    val done=CountDownLatch(1)
                    var error: String?=null
                    var classification: EnvironmentClassification?=null
                    var inferenceAge=Long.MAX_VALUE
                    val started=SystemClock.elapsedRealtime()
                    show("Microphone active · $seconds seconds · no commands")
                    check(activity.audio.sample(seconds) { value,failure,capturedAt ->
                        classification=value;error=failure
                        inferenceAge=SystemClock.elapsedRealtime()-capturedAt;done.countDown()
                    })
                    check(done.await(30,TimeUnit.SECONDS)) { "Sample completion timed out" }
                    check(error==null && classification!=null) { error ?: "Missing classification" }
                    check(inferenceAge in 0..5000) { "Classification exceeds production 5-second freshness limit" }
                    result.putLong("sample${seconds}WallMs",SystemClock.elapsedRealtime()-started)
                    result.putLong("sample${seconds}InferenceAgeMs",inferenceAge)
                    result.putString("sample${seconds}Environment",classification?.environment?.name ?: "UNCERTAIN")
                    result.putDouble("sample${seconds}Score",classification?.confidence ?: 0.0)
                    awaitIdle()
                }
                show("Microphone cancellation test")
                val callbacks=AtomicInteger()
                check(activity.audio.sample(10) { _,_,_ -> callbacks.incrementAndGet() })
                Thread.sleep(250);activity.audio.stop();awaitIdle()
                check(callbacks.get()==0) { "Cancelled sample delivered a callback" }
                result.putString("cancel","PASS: stopped capture, discarded callback, worker idle")
                show("Capture-access interruption test")
                val lost=CountDownLatch(1)
                var failed=false
                check(activity.audio.sample(10) { value,error,_ -> failed=value==null && error!=null;lost.countDown() })
                Thread.sleep(250);activity.access=false
                check(lost.await(10,TimeUnit.SECONDS) && failed) { "Access loss did not fail the sample" }
                awaitIdle();activity.access=true
                result.putString("accessLoss","PASS: interruption reports failure and frees worker")
                show("Leaving-screen cancellation test")
                check(activity.audio.sample(10) { _,_,_ -> callbacks.incrementAndGet() })
                Thread.sleep(250)
                runOnMainSync { activity.finish() }
                waitForIdleSync();awaitIdle()
                check(!activity.foreground && callbacks.get()==0) { "Capture survived leaving screen" }
                result.putString("pause","PASS: leaving screen stops capture and discards result")
            }
            result.putLong("pssFinalKb",Debug.getPss())
            result.putString("result","PASS: pinned native model, cancellable inference"+
                if(microphone) ", bounded microphone windows, Stop/access loss/pause; no aid commands" else "; microphone not requested")
            passed=true
        } catch(e: Exception) {
            result.putString("result","FAIL: ${e.message}")
        } catch(e: LinkageError) {
            result.putString("result","FAIL: native model ${e.message}")
        } finally {
            screen?.let { runOnMainSync { it.audio.stop();it.finish() } }
        }
        finish(if(passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,result)
    }
}
