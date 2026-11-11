package com.heysig.app

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import com.heysig.audio.Yamnet
import com.heysig.noise.*
import org.json.JSONObject
import java.security.MessageDigest

/** Native consistency/UI checks. Live/screen-off capture is a separate explicit instrument argument. */
class SpeechAutomaticAcceptance:Instrumentation() {
    private var live=false;private var isolated=false;private var seconds=90
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);live=arguments?.getString("live")=="true";isolated=arguments?.getString("isolated")=="true";seconds=(arguments?.getString("seconds")?.toIntOrNull() ?: 90).coerceIn(30,600);start() }
    override fun onStart() {
        val result=Bundle();var activity:Activity?=null
        fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        fun waitUntil(label:String,condition:()->Boolean) { val end=SystemClock.elapsedRealtime()+60_000
            while(SystemClock.elapsedRealtime()<end) { var ready=false;runOnMainSync { ready=condition() };if(ready)return;SystemClock.sleep(100) };error("Timeout: $label") }
        val core=targetContext.getSharedPreferences("heysig-core-v1",0)
        val fitting=core.getString("fitting",null);val saved=core.getString("saved",null)
        val noiseBytes=targetContext.getSharedPreferences("heysig-noise-v1",0).getString("settings",null)
        try {
            val baselineBytes=targetContext.assets.open("masking/masking-baseline.txt").use { it.readBytes() }
            check(digest(baselineBytes)==MaskingModelIdentity.BASELINE_SHA256)
            val bandBytes=targetContext.assets.open("separation/bands.txt").use { it.readBytes() }
            check(digest(bandBytes)==SeparationModelIdentity.BANDS_SHA256)
            val head=SpeechSeparationDetector(SpeechMaskingHead.decode(baselineBytes.toString(Charsets.UTF_8)),
                SeparationMaskingBands.decode(bandBytes.toString(Charsets.UTF_8)))
            val fixtures=JSONObject(context.assets.open("masking-fixtures/manifest.json").bufferedReader().use { it.readText() })
            check(fixtures.getString("headSha256")==MaskingModelIdentity.BASELINE_SHA256)
            check(fixtures.getString("baselineSha256")==MaskingModelIdentity.BASELINE_SHA256)
            check(fixtures.getString("separationBandsSha256")==SeparationModelIdentity.BANDS_SHA256)
            val entries=fixtures.getJSONArray("fixtures");val native=mutableListOf<String>();val driftFailures=mutableListOf<String>()
            var separationCpu=0L
            for(i in 0 until entries.length()) {
                val fixture=entries.getJSONObject(i);val raw=context.assets.open("masking-fixtures/${fixture.getString("file")}").use { it.readBytes() }
                check(digest(raw)==fixture.getString("sha256"))
                val pcm=ShortArray(raw.size/2) { n -> ((raw[n*2].toInt() and 255) or (raw[n*2+1].toInt() shl 8)).toShort() }
                try {
                    var evidence:MaskingEvidence?=null
                    Yamnet(targetContext,observeEvidence={ capture,pool,frames ->
                        val expected=fixture.getJSONArray("features");val speech=MaskingSignal.features(capture)
                        check((0..3).all { kotlin.math.abs(speech[it]-expected.getDouble(2048+it))<1e-7 }) { "Speech features differ from developer extraction" }
                        val drift=pool?.indices?.maxOf { kotlin.math.abs(pool[it]*20-expected.getDouble(it)) } ?: Double.POSITIVE_INFINITY
                        // LiteRT host 1.4.0 vs Android 1.4.2 quantized embeddings differ;
                        // retain a bounded feature check plus the stricter class/score check below.
                        val cosine=pool?.indices?.sumOf { pool[it]*expected.getDouble(it)/20 } ?: 0.0
                        if(drift>=.25 || cosine<.99) driftFailures.add("${fixture.getString("file")}: feature max $drift, cosine $cosine")
                        val cpuBefore=Debug.threadCpuTimeNanos()
                        evidence=head.classify(capture,pool,frames)
                        // UNKNOWN/QUIET legitimately bypass the denoiser in production.
                        // Still exercise its native measurements independently for that fixture;
                        // retain the full pipeline severity/score assertions below.
                        val measured=head.latestMeasurement ?: run {
                            check(evidence?.severity in listOf(MaskingSeverity.UNKNOWN,MaskingSeverity.QUIET)) {
                                "${fixture.getString("file")}: missing neural measurement for $evidence"
                            }
                            NeuralSeparation().measure(capture)
                        }
                        separationCpu+=Debug.threadCpuTimeNanos()-cpuBefore
                        val reference=fixture.getJSONObject("separation")
                        fun near(name:String,value:Double?,tolerance:Double) {
                            if(reference.isNull(name)) check(value==null)
                            else check(value!=null && kotlin.math.abs(value-reference.getDouble(name))<tolerance) { "Separation $name differs: $value / ${reference.getDouble(name)}" }
                        }
                        near("ratioDb",measured.retainedToResidualDb,.3)
                        near("retainedDbfs",measured.retainedDbfs,.3);near("residualDbfs",measured.residualDbfs,.3)
                        near("speechScore",measured.speechScore,.035);near("speechActivity",measured.speechActivity,.04)
                        near("residualSpeechScore",measured.residualSpeechScore,.05)
                    }).use { it.classify(pcm) }
                    val actual=checkNotNull(evidence)
                    if(actual.severity.name!=fixture.getString("severity") || kotlin.math.abs(actual.score-fixture.getDouble("score"))>=.01) driftFailures.add("${fixture.getString("file")}: expected ${fixture.getString("severity")}/${fixture.getDouble("score")}, actual $actual")
                    native.add("${fixture.getString("file")}: ${actual.severity}")
                } finally { pcm.fill(0);raw.fill(0) }
            }
            result.putString("native",native.joinToString("; ")+"; consistency, not accuracy acceptance")
            result.putString("separationCpu","${separationCpu/1e9}s for six recorded decisions (includes masking DSP, excludes YAMNet); not a battery-life qualification")
            check(driftFailures.isEmpty()) { driftFailures.joinToString("; ") }
            sendStatus(0,Bundle().apply { putString("phase","native fixtures passed") })
            activity=startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val screen=checkNotNull(activity)
            sendStatus(0,Bundle().apply { putString("phase","Activity opened") })
            fun click(label:String) { runOnMainSync { descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString()==label }.performClick() };waitForIdleSync() }
            click("Noise");click("Choose programs")
            runOnMainSync {
                val views=descendants(screen.findViewById(android.R.id.content));val text=views.filterIsInstance<TextView>().map { it.text.toString() }
                check(text.any { it=="Speech-aware Automatic" } && text.any { it.contains("Universal ·") } && text.any { it.contains("Noisy ·") } && text.any { it.contains("Very Noisy ·") })
                check(views.filterIsInstance<Spinner>().count { it.isShown }>=3)
                check(!SpeechMaskingValidation.qualified() && !EnvironmentReleaseGate.qualified())
            }
            result.putString("firstUse","PASS: pretrained packaged head, choices available without calibration/examples/learning; no acceptance lock")
            val isolatedChoices=targetContext.getSharedPreferences("acceptance-speech-choices",0)
            check(isolatedChoices.edit().clear().commit())
            var isolatedFeature:SpeechAutomaticFeature?=null
            var namedPrograms=listOf(com.heysig.control.Program(1,"Everyday",com.heysig.control.TinnitusCapability.SUPPORTED),com.heysig.control.Program(2,"Noisy",com.heysig.control.TinnitusCapability.SUPPORTED),com.heysig.control.Program(3,"Very Noisy",com.heysig.control.TinnitusCapability.SUPPORTED))
            fun renderIsolated() {
                isolatedFeature?.detach()
                val column=LinearLayout(screen).apply { orientation=LinearLayout.VERTICAL }
                screen.setContentView(ScrollView(screen).apply { addView(column) })
                isolatedFeature=SpeechAutomaticFeature(screen,{namedPrograms},{NoiseSettings(programs=listOf(1,1,2,2,3,3))},{NoiseSafety(foreground=true,unlocked=true,audioFree=true)},{ {} },isolatedChoices)
                isolatedFeature!!.render(column)
            }
            try {
                runOnMainSync {
                    renderIsolated()
                    val views=descendants(screen.findViewById(android.R.id.content));val spinners=views.filterIsInstance<Spinner>()
                    spinners[0].setSelection(2);spinners[1].setSelection(3);spinners[2].setSelection(0)
                    views.filterIsInstance<Button>().first { it.text=="Save speech program choices" }.performClick()
                    check(!SpeechAutomaticService.active)
                    renderIsolated()
                    val restored=descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Spinner>()
                    check(restored.map { it.selectedItemPosition }==listOf(2,3,0)) { "Saved speech choices lost on navigation/recreation" }
                    val previous=isolatedChoices.getString("choices",null)
                    namedPrograms=namedPrograms.map { if(it.slot==1) it.copy(name="Renamed program") else it }
                    descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text=="Save speech program choices" }.performClick()
                    check(isolatedChoices.getString("choices",null)==previous) { "Changed fitting silently rebound choices" }
                }
                result.putString("choices","PASS: isolated explicit Save survives recreation; unused choice retained; stale fitting refuses rebinding; no microphone or program command")
            } finally { runOnMainSync { isolatedFeature?.detach() };isolatedChoices.edit().clear().commit() }
            sendStatus(0,Bundle().apply { putString("phase","first-use and saved choices passed") })
            if(isolated) {
                val config=NoiseSettings(programs=listOf(1,1,2,2,3,3),confirmations=2,cooldownSeconds=30)
                val choices=MaskingChoices(1,2,3);val signature="1:Everyday|2:Noisy|3:Very Noisy"
                waitUntil("visible Activity before isolated session") { screen.hasWindowFocus() }
                runOnMainSync {
                    SpeechServiceAcceptanceService.severity=MaskingSeverity.MODERATE;SpeechServiceAcceptanceService.plays=0
                    SpeechAutomaticService.launch(screen,config,choices,signature,SpeechServiceAcceptanceService::class.java)
                }
                waitUntil("isolated microphone") { SpeechAutomaticService.running?.microphoneBusy==true }
                runOnMainSync {
                    check(targetContext.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==7305 }) {
                        "Microphone started before the Automatic notification was visible"
                    }
                }
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                uiAutomation.executeShellCommand("input keyevent 223").close()
                waitUntil("isolated Noisy dispatch") { (SpeechAutomaticService.running as? SpeechServiceAcceptanceService)?.let { it.program==2 && it.idle }==true }
                sendStatus(0,Bundle().apply { putString("phase","screen-off Noisy dispatch passed") })
                val held=SpeechAutomaticService.running as SpeechServiceAcceptanceService
                val count=SpeechServiceAcceptanceService.plays
                runOnMainSync { SpeechServiceAcceptanceService.severity=MaskingSeverity.QUIET }
                SystemClock.sleep(29_000)
                runOnMainSync { check(held.program==2 && SpeechServiceAcceptanceService.plays==count) { "Cooldown did not prevent a return/duplicate send" } }
                waitUntil("fresh sustained return to Everyday") { held.program==1 && held.idle }
                result.putString("isolatedScreenOff",(!targetContext.getSystemService(PowerManager::class.java).isInteractive).toString())
                runOnMainSync { held.manual() }
                waitUntil("manual cancels Automatic") { !SpeechAutomaticService.active }
                result.putString("isolatedExecutor","PASS: actual microphone/hidden Activity, forced debug evidence -> shared validated executor -> silent transport, Noisy then cooldown then sustained Everyday; no duplicate play; manual request cancels; no real hearing-aid output")
                // A new explicit session exercises notification Off without restoring/replaying a command.
                uiAutomation.executeShellCommand("input keyevent 224").close()
                uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
                runOnMainSync { screen.startActivity(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                waitUntil("visible Activity for second explicit start") { screen.hasWindowFocus() }
                runOnMainSync { SpeechAutomaticService.launch(screen,config,choices,signature,SpeechServiceAcceptanceService::class.java) }
                waitUntil("second isolated session") { SpeechAutomaticService.running!=null }
                runOnMainSync {
                    targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7305 }.notification.actions.single().actionIntent.send()
                }
                waitUntil("notification Off") { !SpeechAutomaticService.active }
            }
            if(live) {
                val runtime=ManualRuntime.get(targetContext);val raw=JSONObject(checkNotNull(noiseBytes) { "Save real noise program choices before live testing" })
                val slots=raw.getJSONArray("programs");val bounds=raw.getJSONArray("boundaries")
                val config=NoiseSettings((0..5).map { if(slots.isNull(it)) null else slots.getInt(it) },(0..4).map { bounds.getDouble(it) },raw.getDouble("margin"),raw.getInt("confirmations"),raw.getInt("cooldown"),raw.getInt("capture"),raw.getInt("wait"),raw.getInt("freshness"),if(raw.isNull("sleepProgram")) null else raw.getInt("sleepProgram"))
                val choiceRaw=targetContext.getSharedPreferences("heysig-masking-v1",0).getString("choices",null)
                val choices=if(choiceRaw==null) MaskingChoices(config.programs[0],config.programs[3],config.programs[5]) else JSONObject(choiceRaw).let { j ->
                    fun slot(k:String)=if(j.isNull(k)) null else j.getInt(k)
                    MaskingChoices(slot("everyday"),slot("noisy"),slot("veryNoisy")) }
                val signature=runtime.fitting!!.programs.sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
                val battery=targetContext.getSystemService(BatteryManager::class.java)
                val chargeBefore=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                val programBefore=runtime.requested.program
                runOnMainSync {
                    AutomaticNoiseService.cancel()
                    check(runtime.sleepRoutine.state.mode==com.heysig.sleep.SleepMode.AWAKE && !runtime.sleepRoutine.state.pending)
                    runtime.sleepRoutine.transition(runtime.sleepRoutine.state.resume()) // Explicit live test authorization, same as Automatic On.
                    SpeechAutomaticService.launch(screen,config,choices,signature)
                }
                waitUntil("live service") { SpeechAutomaticService.running!=null }
                waitUntil("microphone") { SpeechAutomaticService.running?.microphoneBusy==true }
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                uiAutomation.executeShellCommand("input keyevent 223").close()
                SystemClock.sleep(seconds*1000L)
                runOnMainSync {
                    val service=checkNotNull(SpeechAutomaticService.running) { SpeechAutomaticService.lastStatus }
                    check(service.samples>=2) { "Too few completed captures" }
                    result.putString("screenOff",(!targetContext.getSystemService(PowerManager::class.java).isInteractive).toString())
                    result.putString("live",service.summary())
                    result.putString("runtime","${service.samples} checks in ${seconds}s; inference CPU ${service.cpuNanos/1e9}s; charge counter ${chargeBefore} to ${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)} uAh; program $programBefore to ${runtime.requested.program}; short connected-device observation, not battery-life qualification")
                    val notification=targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7305 }
                    notification.notification.actions.single().actionIntent.send()
                }
                waitUntil("notification Off") { !SpeechAutomaticService.active }
                result.putString("stop","PASS: notification Off stops future microphone checks and program requests")
            }
            check(core.getString("fitting",null)==fitting && core.getString("saved",null)==saved)
            check(targetContext.getSharedPreferences("heysig-noise-v1",0).getString("settings",null)==noiseBytes)
            result.putString("preservation","PASS: saved fitting, saved user snapshot and all six noise assignments/thresholds/timing unchanged; live last-requested state may change")
            result.putString("result","PASS: speech-aware Automatic native regression and UI; physical aid reception requires wearer confirmation")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) {
            result.putString("serviceStatus",SpeechAutomaticService.running?.summary() ?: SpeechAutomaticService.lastStatus)
            result.putString("result","FAIL: ${e.message}");finish(Activity.RESULT_CANCELED,result)
        }
        finally { runOnMainSync { SpeechAutomaticService.cancel();activity?.finish() } }
    }
}
