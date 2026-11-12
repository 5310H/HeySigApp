package com.heysig.app

import android.app.*
import android.content.Intent
import android.os.*
import com.heysig.noise.*

/** Full native phone analysis, isolated fitting/storage and silent transport. Explicit host-run only. */
class PhoneAutomaticInterruptionAcceptance:Instrumentation() {
    private var mode="notification";private var seconds=120;private var screenOff=false
    override fun onCreate(args:Bundle?) { super.onCreate(args);mode=args?.getString("mode") ?: "notification";seconds=(args?.getString("seconds")?.toIntOrNull() ?: 120).coerceIn(30,600);screenOff=args?.getString("screenOff")=="true";start() }
    override fun callActivityOnCreate(activity:Activity,state:Bundle?) {
        if(activity is HomeActivity) { activity.setTurnScreenOn(true);activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        super.callActivityOnCreate(activity,state)
    }
    override fun onStart() {
        val result=Bundle();var activity:Activity?=null
        fun waitFor(label:String,limit:Long=60_000,condition:()->Boolean) {
            val until=SystemClock.elapsedRealtime()+limit
            while(SystemClock.elapsedRealtime()<until) { if(condition())return;SystemClock.sleep(100) };error("Timeout: $label; ${SpeechAutomaticService.lastStatus}")
        }
        try {
            check(mode in listOf("notification","manual","external","observe")) { "Unknown mode; no capture started" }
            activity=startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen=checkNotNull(activity)
            waitFor("Unlock phone for explicit foreground start") { screen.hasWindowFocus() && !targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
            runOnMainSync {
                check(!SpeechAutomaticService.active) { "Turn Automatic Off before isolated test" }
                SpeechServiceAcceptanceService.useNativeEvidence=true;SpeechServiceAcceptanceService.plays=0
                SpeechAutomaticService.launch(screen,NoiseSettings(programs=listOf(1,1,2,2,3,3),confirmations=2,cooldownSeconds=10),MaskingChoices(1,2,3),"1:Everyday|2:Noisy|3:Very Noisy",SpeechServiceAcceptanceService::class.java)
            }
            waitFor("Automatic service",15_000) { SpeechAutomaticService.running!=null }
            val service=checkNotNull(SpeechAutomaticService.running)
            check(service.summary().contains("Microphone: phone"))
            val started=SystemClock.elapsedRealtime();var microphoneObserved=false;var reported=0
            fun observe() {
                if(service.samples>reported) {
                    val sample=org.json.JSONObject().put("sample",service.samples).put("elapsedMillis",SystemClock.elapsedRealtime()-started)
                        .put("severity",service.latest.severity.name).put("soundType",service.latest.soundType.name)
                        .put("score",service.latest.score).put("reason",service.latest.reason)
                        .put("programRequested",(service as SpeechServiceAcceptanceService).program ?: org.json.JSONObject.NULL)
                        .put("silentPlays",SpeechServiceAcceptanceService.plays).put("analysisCpuNanos",service.cpuNanos)
                    sendStatus(0,Bundle().apply { putString("sample",sample.toString()) });reported=service.samples
                }
                if(targetContext.getSystemService(AppOpsManager::class.java).isOpActive(AppOpsManager.OPSTR_RECORD_AUDIO,android.os.Process.myUid(),targetContext.packageName)) microphoneObserved=true
            }
            if(screenOff) {
                runOnMainSync { screen.setTurnScreenOn(false);screen.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                uiAutomation.executeShellCommand("input keyevent 223").close()
            }
            waitFor("Two complete full-native samples",90_000) { observe();check(SpeechAutomaticService.active) { SpeechAutomaticService.lastStatus };service.samples>=2 }
            sendStatus(0,Bundle().apply { putString("phase","Two full-native phone samples complete; ready for interruption") })
            when(mode) {
                "notification" -> runOnMainSync { targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7305 }.notification.actions.single().actionIntent.send() }
                "manual" -> runOnMainSync { (service as SpeechServiceAcceptanceService).manual() }
            }
            val until=started+seconds*1000L
            while(SpeechAutomaticService.active && SystemClock.elapsedRealtime()<until) { observe();SystemClock.sleep(100) }
            if(mode=="observe") runOnMainSync { SpeechAutomaticService.cancel() }
            waitFor("Session stop",10_000) { !SpeechAutomaticService.active }
            check(microphoneObserved) { "No Android microphone access observed" }
            result.putLong("analysisCpuNanos",service.cpuNanos)
            result.putString("finalStatus",service.summary())
            result.putInt("samples",service.samples);result.putInt("silentPlays",SpeechServiceAcceptanceService.plays)
            result.putLong("elapsedMillis",SystemClock.elapsedRealtime()-started)
            result.putString("stop",SpeechAutomaticService.lastStatus)
            result.putBoolean("screenOff",!targetContext.getSystemService(PowerManager::class.java).isInteractive)
            result.putString("result","PASS: full native phone captures with isolated silent executor; not field accuracy, listening benefit or endurance qualification")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("result","FAIL: ${e.message}");result.putString("stop",SpeechAutomaticService.lastStatus);finish(Activity.RESULT_CANCELED,result) }
        finally { runOnMainSync { SpeechAutomaticService.cancel();SpeechServiceAcceptanceService.useNativeEvidence=false;activity?.finish() } }
    }
}
