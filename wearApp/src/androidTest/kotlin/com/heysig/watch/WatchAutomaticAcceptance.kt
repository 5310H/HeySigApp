package com.heysig.watch

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.heysig.app.SpeechSeparationDetector
import com.heysig.audio.Yamnet
import com.heysig.noise.*
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Licensed native fixtures, actual round controls and optional paired silent-controller tests. */
class WatchAutomaticAcceptance:Instrumentation() {
    private var mode="native";private var screenOff=false;private var fixtures=true;private var restart=false
    override fun onCreate(args:Bundle?) { super.onCreate(args);mode=args?.getString("mode") ?: "native";screenOff=args?.getString("screenOff")=="true";fixtures=mode=="native" || args?.getString("fixtures")!="false";restart=args?.getString("restart")=="true";start() }
    override fun callActivityOnCreate(activity:Activity,state:Bundle?) {
        if(activity is WatchAutomaticActivity) {
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        super.callActivityOnCreate(activity,state)
    }
    override fun onStart() {
        val result=Bundle();var activity:Activity?=null;var client:WatchAutomaticClient?=null
        fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        try {
            check(mode in listOf("native","controlled","capture","watchOff")) { "Unknown watch test mode; no capture started" }
            val manifest=JSONObject(context.assets.open("masking-fixtures/manifest.json").bufferedReader().use { it.readText() })
            val fixtures=manifest.getJSONArray("fixtures");val detector=SpeechSeparationDetector.load(targetContext);val denoiser=com.heysig.app.NeuralSeparation()
            val classes=mutableListOf<String>();var nativeCpu=0L
            for(i in 0 until if(this.fixtures) fixtures.length() else 0) {
                val fixture=fixtures.getJSONObject(i);val raw=context.assets.open("masking-fixtures/${fixture.getString("file")}").use { it.readBytes() }
                check(MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }==fixture.getString("sha256"))
                val pcm=ShortArray(raw.size/2) { n -> ((raw[n*2].toInt() and 255) or (raw[n*2+1].toInt() shl 8)).toShort() }
                try {
                    var evidence:MaskingEvidence?=null
                    Yamnet(targetContext,observeEvidence={ capture,pool,frames ->
                        val started=Debug.threadCpuTimeNanos();evidence=detector.classify(capture,pool,frames)
                        val measurement=detector.latestMeasurement ?: denoiser.measure(capture)
                        nativeCpu+=Debug.threadCpuTimeNanos()-started
                        val expected=fixture.getJSONObject("separation")
                        for((key,value,tolerance) in listOf(Triple("ratioDb",measurement.retainedToResidualDb,.3),Triple("retainedDbfs",measurement.retainedDbfs,.3),
                            Triple("residualDbfs",measurement.residualDbfs,.3),Triple("speechScore",measurement.speechScore,.035),Triple("speechActivity",measurement.speechActivity,.04))) {
                            if(expected.isNull(key)) check(value==null) else check(value!=null && kotlin.math.abs(value-expected.getDouble(key))<tolerance) { "Native $key differs for ${fixture.getString("file")}" }
                        }
                    }).use { it.classify(pcm) }
                    check(evidence?.severity?.name==fixture.getString("severity") && kotlin.math.abs(checkNotNull(evidence).score-fixture.getDouble("score"))<.01) { "Native watch decision differs: $evidence" }
                    classes.add("${fixture.getString("file")}: ${evidence?.severity}")
                } finally { pcm.fill(0);raw.fill(0) }
                sendStatus(0,Bundle().apply { putString("phase","Native fixture ${i+1} of ${fixtures.length()} passed") })
            }
            result.putString("native",if(this.fixtures) classes.joinToString("; ") else "SKIPPED by explicit fixtures=false; retain separate native-run evidence")
            result.putString("nativeCpu","${nativeCpu/1e9}s excluding YAMNet; consistency only")
            runOnMainSync {
                for(scale in listOf(1f,2f)) {
                    val config=Configuration(targetContext.resources.configuration).apply { fontScale=scale;screenWidthDp=192;screenHeightDp=192;screenLayout=(screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or Configuration.SCREENLAYOUT_ROUND_YES }
                    val ctx=targetContext.createConfigurationContext(config);val width=(192*ctx.resources.displayMetrics.density).toInt()
                    val layout=WatchAutomaticLayout.create(ctx,"Build test",{},{});val view=layout.view
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY));view.layout(0,0,width,width)
                    check(descendants(view).filterIsInstance<Button>().all { it.measuredHeight>=48*ctx.resources.displayMetrics.density && it.right<=width }) { "Round/large-font buttons clipped or too small" }
                    check(layout.toggle.top<layout.status.top && layout.toggle.bottom<=width) { "Automatic On/Off must remain above long status and visible without scrolling" }
                }
            }
            result.putString("layout","PASS: actual 192dp round Automatic controls at font scales 1 and 2, >=48dp buttons")
            if(mode!="native") {
                activity=startActivitySync(Intent(targetContext,WatchAutomaticActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val screen=checkNotNull(activity)
                val focusBy=SystemClock.elapsedRealtime()+60_000
                while(!screen.hasWindowFocus() && SystemClock.elapsedRealtime()<focusBy) SystemClock.sleep(100)
                check(screen.hasWindowFocus()) { "Unlock watch before paired testing" }
                sendStatus(0,Bundle().apply { putString("phase","Watch Automatic Activity has foreground focus") })
                if(mode=="controlled") {
                    var ended:String?=null
                    val paired=WatchAutomaticClient(targetContext) { ended=it };client=paired
                    fun rpc(start:((JSONObject)->Unit)->Unit):JSONObject {
                        val latch=CountDownLatch(1);var reply:JSONObject?=null
                        runOnMainSync { start { value -> reply=value;latch.countDown() } }
                        check(latch.await(15,TimeUnit.SECONDS)) { ended ?: "Phone RPC timed out" }
                        return checkNotNull(reply).also { check(it.optBoolean("active",false)) { it.optString("text") } }
                    }
                    rpc { paired.connect(it) };var sequence=0L
                    sendStatus(0,Bundle().apply { putString("phase","Selected-phone controlled session connected") })
                    fun sample(severity:MaskingSeverity) {
                        val captured=SystemClock.elapsedRealtime();val id=UUID.randomUUID().toString()
                        val lease=rpc { paired.request("prepare",JSONObject().put("id",id),it) }
                        val body=JSONObject().put("profile",WatchAutomaticProfile.ID).put("sequence",sequence++)
                            .put("ageMillis",SystemClock.elapsedRealtime()-captured).put("severity",severity.name).put("type","SPEECH")
                            .put("score",if(severity==MaskingSeverity.QUIET) 0.0 else .9).put("rmsDbfs",-35.0)
                        rpc { paired.request("sample",JSONObject().put("id",id).put("token",lease.getString("token")).put("body",body),it) }
                    }
                    for((severity,program) in listOf(MaskingSeverity.MODERATE to 2,MaskingSeverity.QUIET to 1,MaskingSeverity.HEAVY to 3)) {
                        val until=SystemClock.elapsedRealtime()+30_000;var status=rpc { paired.request("status",done=it) }
                        while(!status.optBoolean("captureAllowed") && SystemClock.elapsedRealtime()<until) { SystemClock.sleep(500);status=rpc { paired.request("status",done=it) } }
                        check(status.optBoolean("captureAllowed"));sample(severity);SystemClock.sleep(1000);sample(severity)
                        var current=rpc { paired.request("status",done=it) }
                        while((current.optInt("program")!=program || current.optBoolean("pending")) && SystemClock.elapsedRealtime()<until) { SystemClock.sleep(100);current=rpc { paired.request("status",done=it) } }
                        check(current.optInt("program")==program) { "Expected silent program $program: $current" }
                    }
                    runOnMainSync { paired.stopPhone() };result.putString("paired","PASS: Noisy, Universal return and Very Noisy through selected-phone grants/session/fresh sequence; phone uses silent executor")
                } else {
                    runOnMainSync {
                        if(restart) { WatchAutomaticService.launch(screen);WatchAutomaticService.stop(targetContext) }
                        WatchAutomaticService.launch(screen)
                    }
                    result.putBoolean("rapidRestart",restart)
                    val until=SystemClock.elapsedRealtime()+180_000;var service:WatchAutomaticService?=null
                    while(service==null && SystemClock.elapsedRealtime()<until) { service=WatchAutomaticService.running;SystemClock.sleep(100) }
                    val running=checkNotNull(service) { WatchAutomaticService.lastStatus }
                    if(screenOff) {
                        runOnMainSync { screen.setTurnScreenOn(false);screen.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                        uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                        uiAutomation.executeShellCommand("input keyevent 223").close()
                    }
                    var microphoneObserved=false
                    while(WatchAutomaticService.running!=null && SystemClock.elapsedRealtime()<until) {
                        if(targetContext.getSystemService(AppOpsManager::class.java).isOpActive(AppOpsManager.OPSTR_RECORD_AUDIO,
                            android.os.Process.myUid(),targetContext.packageName)) microphoneObserved=true
                        if(mode=="watchOff" && running.sentSamples>=2) runOnMainSync {
                            targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7405 }.notification.actions.single().actionIntent.send()
                        }
                        SystemClock.sleep(100)
                    }
                    check(WatchAutomaticService.running==null) { "Watch capture session did not stop" }
                    check(microphoneObserved) { "Android did not report watch microphone access" }
                    result.putBoolean("microphoneObserved",microphoneObserved)
                    check(running.samples>=2) { "Too few watch captures: ${running.samples}; ${WatchAutomaticService.lastStatus}" }
                    result.putString("capture","PASS: ${running.samples} actual watch captures; ${running.cpuNanos/1e9}s inference CPU; ${WatchAutomaticService.lastStatus}")
                    result.putString("screenOff",(!targetContext.getSystemService(PowerManager::class.java).isInteractive).toString())
                }
            }
            result.putString("result","PASS: watch Automatic $mode; no claim of field accuracy, battery endurance or physical aid reception")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("result","FAIL: ${e.message}");result.putString("watchStatus",WatchAutomaticService.lastStatus);finish(Activity.RESULT_CANCELED,result) }
        finally { runOnMainSync { client?.close();WatchAutomaticService.stop(targetContext);activity?.finish() } }
    }
}
