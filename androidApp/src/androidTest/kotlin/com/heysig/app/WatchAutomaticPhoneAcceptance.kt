package com.heysig.app

import android.app.*
import android.content.Intent
import android.os.*
import com.heysig.noise.*

/** Paired watch receiver test: isolated fitting and silent transport; real settings are read only. */
class WatchAutomaticPhoneAcceptance:Instrumentation() {
    private var seconds=240;private var mode="remote";private var screenOff=false
    override fun onCreate(args:Bundle?) { super.onCreate(args);seconds=(args?.getString("seconds")?.toIntOrNull() ?: 240).coerceIn(30,600);mode=args?.getString("mode") ?: "remote";screenOff=args?.getString("screenOff")=="true";start() }
    override fun onStart() {
        val result=Bundle();var activity:Activity?=null
        val core=targetContext.getSharedPreferences("heysig-core-v1",0)
        val fitting=core.getString("fitting",null);val saved=core.getString("saved",null)
        try {
            val prefs=targetContext.getSharedPreferences("heysig-companion",0)
            val node=checkNotNull(prefs.getString("node",null)) { "Select a paired watch first" }
            check(prefs.getBoolean("foregroundEnabledV1",false)) { "Enable the selected watch first" }
            activity=startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen=checkNotNull(activity)
            val focusBy=SystemClock.elapsedRealtime()+60_000
            while(!screen.hasWindowFocus() && SystemClock.elapsedRealtime()<focusBy) SystemClock.sleep(100)
            check(screen.hasWindowFocus()) { "Unlock the phone before starting the receiver test" }
            if(!screenOff) runOnMainSync { screen.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            runOnMainSync {
                check(!SpeechAutomaticService.active) { "Turn current Automatic Off before isolated testing" }
                SpeechServiceAcceptanceService.plays=0
                SpeechAutomaticService.launch(screen,NoiseSettings(programs=listOf(1,1,2,2,3,3),confirmations=2,cooldownSeconds=10),
                    MaskingChoices(1,2,3),"1:Everyday|2:Noisy|3:Very Noisy",SpeechServiceAcceptanceService::class.java,watchNode=node)
            }
            val startBy=SystemClock.elapsedRealtime()+10_000
            while(SpeechAutomaticService.running==null && SystemClock.elapsedRealtime()<startBy) SystemClock.sleep(100)
            val service=checkNotNull(SpeechAutomaticService.running) { SpeechAutomaticService.lastStatus }
            check(!service.microphoneBusy)
            check(service.summary().let { it.contains("Microphone: watch") && it.contains("Program considered:") && it.contains("Last program requested:") }) { "Automatic status must distinguish microphone source, considered program and last request" }
            sendStatus(0,Bundle().apply { putString("phase","Selected-watch silent receiver armed; start the watch test") })
            if(screenOff) {
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                uiAutomation.executeShellCommand("input keyevent 223").close()
            }
            val until=SystemClock.elapsedRealtime()+seconds*1000L
            var triggered=false
            while(SystemClock.elapsedRealtime()<until && SpeechAutomaticService.active) {
                runOnMainSync {
                    check(!service.microphoneBusy) { "Phone microphone opened in watch mode" }
                    if(Build.VERSION.SDK_INT>=30) check(!targetContext.getSystemService(AppOpsManager::class.java)
                        .isOpActive(AppOpsManager.OPSTR_RECORD_AUDIO,android.os.Process.myUid(),targetContext.packageName)) {
                        "Android reported active phone microphone access during watch input"
                    }
                    if(!triggered && service.samples>=2 && mode in listOf("manual","notification")) {
                        triggered=true
                        if(mode=="manual") (service as SpeechServiceAcceptanceService).manual()
                        else targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7305 }.notification.actions.single().actionIntent.send()
                    }
                }
                SystemClock.sleep(100)
            }
            check(!SpeechAutomaticService.active) { "Watch test did not stop: ${service.summary()}" }
            check(service.samples>=2) { "Too few accepted watch samples: ${service.samples}; ${SpeechAutomaticService.lastStatus}" }
            check(core.getString("fitting",null)==fitting && core.getString("saved",null)==saved)
            result.putString("source","PASS: ${service.samples} accepted watch samples; phone microphone worker idle and Android RECORD_AUDIO inactive throughout checks")
            result.putString("mode",mode);result.putString("phoneScreenOff",(!targetContext.getSystemService(PowerManager::class.java).isInteractive).toString())
            result.putString("silentPlays",SpeechServiceAcceptanceService.plays.toString())
            result.putString("stop",SpeechAutomaticService.lastStatus)
            result.putString("result","PASS: paired selected-watch evidence and isolated shared executor; no aid output or real fitting changes")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("result","FAIL: ${e.message}");result.putString("stop",SpeechAutomaticService.lastStatus);finish(Activity.RESULT_CANCELED,result) }
        finally { runOnMainSync { SpeechAutomaticService.cancel();activity?.finish() } }
    }
}
