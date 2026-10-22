package com.heysig.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import com.heysig.app.voice.HandsFreeService
import com.heysig.control.ManualRequest

/** Exists only for one claimed tone operation. No microphone, sticky restart or command retry. */
class AutomaticSleepService: Service() {
    private val handler=Handler(Looper.getMainLooper())
    private val runtime by lazy { ManualRuntime.get(this) }
    private val scheduler by lazy { AutomaticSleepScheduler(this) }
    private var claim: AutomaticSleepScheduler.Claim?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private var owned=false
    private var finished=false
    private val watchdog=object: Runnable {
        override fun run() {
            val active=claim ?: return
            if(!scheduler.authorized(active)) cancelPlayback()
            if(!finished) handler.postDelayed(this,100)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(claim!=null) return START_NOT_STICKY
        val active=runCatching { scheduler.takeClaim(intent?.getStringExtra("token").orEmpty()) }.getOrNull()
        if(active==null) { releaseHandoff();stopSelf();return START_NOT_STICKY }
        claim=active;running=this
        try {
            val notice=scheduler.notification("Switching to ${runtime.fitting?.programs?.find { it.slot==active.slot }?.name ?: "saved program"}. Stop cancels future switches and requests cancellation.")
            startForeground(AutomaticSleepScheduler.NOTIFICATION,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:AutomaticSleep").apply { acquire(60_000) }
            releaseHandoff()
            check(!runtime.busy.get() && !HandsFreeService.manualSpeechBusy && HandsFreeService.externalSpeechBusy?.invoke()!=true &&
                HandsFreeService.running?.commandInProgress!=true && !runtime.personalPhrases.awaitingConfirmation &&
                !runtime.fittingWorkflow.pending && !runtime.sleepRoutine.hasDraft) { "Another command, speech or review is active" }
            HandsFreeService.stopNoise?.invoke()
            HandsFreeService.running?.stopSpokenReply()
            HandsFreeService.running?.suspendMicrophone(invalidateConfirmation=true)
            owned=true
            handler.post(watchdog)
            handler.postDelayed({
                if(!finished) {
                    cancelPlayback()
                    runCatching { scheduler.disable("Automatic action timed out. Aid state may be uncertain; no retry.",report=true) }
                    finish("Automatic action timed out. Review the aids before starting automatic again.",false)
                }
            },55_000)
            runtime.execute(listOf(ManualRequest.SelectProgram(active.slot)),false,false,{false},
                { !finished && running===this && scheduler.authorized(active) },{},sleepTarget=active.sleep) { text,success -> finish(text,success) }
        } catch(e: Exception) { finish("Automatic action blocked: ${e.message}. No retry.",false) }
        return START_NOT_STICKY
    }
    fun cancelPlayback() { if(owned && !finished) runtime.cancelled.set(true) }
    private fun finish(text: String,success: Boolean) {
        if(finished) return
        finished=true;owned=false
        handler.removeCallbacksAndMessages(null)
        claim?.let { runCatching { scheduler.finish(it,text,success) } }
        releaseHandoff()
        if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if(running===this) running=null
        runCatching { scheduler.publishStatus() }
        HandsFreeService.running?.resumeWhenQuiet()
        stopSelf()
    }
    override fun onDestroy() {
        cancelPlayback()
        if(!finished && claim!=null) runCatching { scheduler.disable("Automatic playback was interrupted. Review the aids; nothing will replay.",report=true) }
        finished=true;handler.removeCallbacksAndMessages(null)
        if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
        releaseHandoff();if(running===this) running=null
        super.onDestroy()
    }
    companion object {
        var running: AutomaticSleepService?=null; private set
        private var handoff: PowerManager.WakeLock?=null
        private fun releaseHandoff() { if(handoff?.isHeld==true) handoff?.release();handoff=null }
        fun launch(context: Context,token: String) {
            releaseHandoff()
            handoff=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:SleepHandoff").apply { acquire(15_000) }
            try { context.startForegroundService(Intent(context,AutomaticSleepService::class.java).putExtra("token",token)) }
            catch(e: Exception) { releaseHandoff();throw e }
        }
    }
}
