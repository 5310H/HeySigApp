package com.heysig.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import com.heysig.app.voice.HandsFreeService
import com.heysig.audio.EnvironmentAudio
import com.heysig.control.ManualRequest
import com.heysig.noise.*

/** Explicit live microphone session. All aid playback uses ManualRuntime's shared validated executor. */
open class SpeechAutomaticService: Service() {
    private val handler=Handler(Looper.getMainLooper())
    protected open val runtime by lazy { ManualRuntime.get(this) }
    private lateinit var request: Request
    @Volatile private var closed=false
    private var deadline=0L
    private var nextAt=0L
    private var notificationReadyBy=0L
    private var notificationSeen=false
    private var watchMode=false
    private var watchEndpoint:WatchAutomaticEndpoint?=null
    private var watchReadyBy=0L
    private var wakeLock:PowerManager.WakeLock?=null
    private var state=MaskingState()
    private var ownsPlayback=false
    @Volatile private var settleUntil=0L
    private var baseline:com.heysig.sleep.SleepState?=null
    private var settingsBytes:String?=null
    private var roleBytes:String?=null
    @Volatile private var evidence=MaskingEvidence()
    @Volatile private var rms:Double?=null
    @Volatile private var analysisCpuNanos=0L
    private val head by lazy { SpeechSeparationDetector.load(this) }
    private fun safety()=NoiseSafety(microphoneSession=!closed && running===this,
        manualOverride=runtime.sleepRoutine.state.override,
        sleeping=runtime.sleepRoutine.state.mode!=com.heysig.sleep.SleepMode.AWAKE || runtime.sleepRoutine.state.pending,
        audioFree=!runtime.busy.get() && !HandsFreeService.manualSpeechBusy && HandsFreeService.externalSpeechBusy?.invoke()!=true &&
            HandsFreeService.running?.isListening!=true && !runtime.fittingWorkflow.pending && !runtime.personalPhrases.awaitingConfirmation && !runtime.sleepRoutine.hasDraft,
        protectedPrograms=runtime.fitting?.programs.orEmpty().filter { isTherapyProgramName(it.name) }.map { it.slot }.toSet())
    private val audio by lazy { EnvironmentAudio(this,{ !closed && safety().blocked()==null && SystemClock.elapsedRealtime()>=settleUntil },
        { cancel -> MicrophoneLease.acquire(cancel) },{ MicrophoneLease.release() },observeEvidence={ pcm,features,frames ->
            evidence=detect(pcm,features,frames)
            rms=NoiseAccumulator(16000,3).apply { add(pcm,pcm.size) }.measurement().rmsDbfs
        },observeInferenceCpu={ analysisCpuNanos=it },microphoneSession={ !closed && running===this }) }
    var detail="Starting speech-aware Automatic"; private set
    protected open fun detect(pcm:ShortArray,features:DoubleArray?,frames:List<Map<String,Double>>)=head.classify(pcm,features,frames)
    var samples=0;private set
    var cpuNanos=0L;private set
    var latest=MaskingEvidence();private set
    var proposal:Int?=null;private set
    val soundLevels=ArrayDeque<Pair<Long,Double>>()
    val microphoneBusy get()=!watchMode && audio.busy()
    private fun signature()=runtime.fitting?.programs.orEmpty().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==OFF) { cancel();return START_NOT_STICKY }
        if(running===this) return START_NOT_STICKY
        val pendingRequest=pending?.takeIf { it.token==intent?.getStringExtra("token") };pending=null
        if(pendingRequest==null) { stopSelf();return START_NOT_STICKY }
        try {
            request=pendingRequest;watchMode=request.watchNode!=null;check(signature()==request.fitting) { "Program setup changed" }
            request.settings.validate(runtime.fitting?.programs.orEmpty().map { it.slot }.toSet())
            val slots=runtime.fitting?.programs.orEmpty().filterNot { isTherapyProgramName(it.name) }.map { it.slot }.toSet()
            check(listOf(request.choices.everyday,request.choices.noisy,request.choices.veryNoisy).all { it==null || it in slots }) { "Review speech program choices" }
            baseline=runtime.sleepRoutine.state
            check(baseline!!.mode==com.heysig.sleep.SleepMode.AWAKE && !baseline!!.pending) { "Therapy has priority" }
            check(runtime.requested.program!=null && runtime.requested.program in slots) { "Choose a daytime program on Home first" }
            state=MaskingState(lastSuccess=lastSuccessMillis)
            settingsBytes=getSharedPreferences("heysig-noise-v1",0).getString("settings",null)
            roleBytes=getSharedPreferences("heysig-masking-v1",0).getString("choices",null)
            running=this;lastStatus="";deadline=SystemClock.elapsedRealtime()+8*60*60_000L
            val notifications=getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Speech-aware Automatic",NotificationManager.IMPORTANCE_LOW))
            check(notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE) { "Allow Automatic notifications first" }
            notificationReadyBy=SystemClock.elapsedRealtime()+5_000L
            if(Build.VERSION.SDK_INT>=30) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                if(watchMode) 0 else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:SpeechAutomatic").apply { acquire(8*60*60_000L+10_000) }
            if(watchMode) {
                detail="Waiting for watch Automatic On. Phone microphone is idle."
                watchReadyBy=SystemClock.elapsedRealtime()+120_000L
                watchEndpoint=WatchAutomaticEndpoint(this,request.watchNode!!,{ watchStatus() },{ value,at,level ->
                    if(!closed) {
                        if(!watchCaptureAllowed()) reset("Paused for phone audio or manual control; fresh watch checks needed")
                        else { samples++;latest=value;soundLevels.addLast(at to level);while(soundLevels.size>120) soundLevels.removeFirst();acceptEvidence(at) }
                        publish()
                    }
                },{ why -> finish(why) }).also { it.start() }
            }
            handler.post(tick)
        } catch(e:Exception) { finish(e.message ?: "Could not start Automatic") }
        return START_NOT_STICKY
    }
    private fun reset(why:String) { state=state.copy(severity=null,count=0,lastCapture=null);latest=MaskingEvidence();proposal=null;detail=why }
    private val tick=object:Runnable {
        override fun run() {
            if(closed) return
            val now=SystemClock.elapsedRealtime();val manager=getSystemService(NotificationManager::class.java)
            val notificationPresent=manager.activeNotifications.any { it.id==NOTIFICATION }
            if(notificationPresent) notificationSeen=true
            val stopReason=when {
                now>=deadline -> "Eight-hour Automatic session finished"
                !watchMode && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED -> "Microphone permission removed"
                !manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE -> "Automatic notification removed"
                !notificationPresent && (notificationSeen || now>=notificationReadyBy) -> "Automatic notification removed"
                watchMode && (getSharedPreferences("heysig-companion",0).getString("node",null)!=request.watchNode || !getSharedPreferences("heysig-companion",0).getBoolean("foregroundEnabledV1",false)) -> "Selected watch changed; restart Automatic"
                watchMode && (watchEndpoint?.hasPeer==true || now>=watchReadyBy) && watchEndpoint?.let { now-it.lastContact>=WatchAutomaticProfile.CONTACT_TIMEOUT_MILLIS }==true -> "Watch disconnected or silent; restart Automatic"
                signature()!=request.fitting -> "Program setup changed; review choices"
                getSharedPreferences("heysig-noise-v1",0).getString("settings",null)!=settingsBytes || getSharedPreferences("heysig-masking-v1",0).getString("choices",null)!=roleBytes -> "Saved choices changed; restart Automatic"
                runtime.sleepRoutine.state!=baseline -> "Manual control or Therapy has priority"
                else -> null
            }
            if(stopReason!=null) { finish(stopReason);return }
            // Android may register the foreground notification asynchronously. Never
            // capture before it is visible; fail closed if registration times out.
            if(!notificationPresent) { handler.postDelayed(this,100);return }
            if(watchMode) {
                if(safety().blocked()!=null) reset("Paused for phone audio or manual control; fresh watch checks needed")
                handler.postDelayed(this,500);return
            }
            if(safety().blocked()!=null) { audio.stop();reset(if(ownsPlayback) "Sending program change" else "Paused for speech or manual control; checking again afterward") }
            else if(!audio.busy() && now>=nextAt && now>=settleUntil) {
                evidence=MaskingEvidence();rms=null
                audio.sample(3) { result,error,capturedAt -> handler.post {
                    if(closed) return@post
                    nextAt=SystemClock.elapsedRealtime()+request.settings.waitSeconds.coerceAtLeast(10)*1000L
                    if(result==null || error!=null || safety().blocked()!=null) {
                        reset(error ?: "Check interrupted; fresh checks needed")
                        if(safety().blocked()==null && error!=null) { finish("Microphone/model unavailable: $error");return@post }
                    } else {
                        samples++;cpuNanos+=analysisCpuNanos;latest=evidence
                        rms?.let { soundLevels.addLast(capturedAt to it);while(soundLevels.size>120) soundLevels.removeFirst() }
                        acceptEvidence(capturedAt)
                    }
                    publish()
                } }
                nextAt=now+request.settings.freshnessSeconds*1000L
            }
            handler.postDelayed(this,500)
        }
    }
    private fun watchCaptureAllowed():Boolean {
        val manager=getSystemService(NotificationManager::class.java)
        return !closed && notificationSeen && manager.areNotificationsEnabled() &&
            manager.activeNotifications.any { it.id==NOTIFICATION } && safety().blocked()==null &&
            SystemClock.elapsedRealtime()>=settleUntil && !ownsPlayback
    }
    private fun watchStatus()=org.json.JSONObject().put("text",summary()).put("program",runtime.requested.program ?: 0).put("pending",ownsPlayback)
        .put("waitSeconds",request.settings.waitSeconds.coerceAtLeast(10))
        .put("captureAllowed",watchCaptureAllowed())
    private fun acceptEvidence(capturedAt:Long) {
        val decision=observeMasking(state,latest,capturedAt,SystemClock.elapsedRealtime(),request.settings,request.choices,safety(),runtime.requested.program)
        state=decision.state;proposal=decision.proposed
        detail="${latest.severity.label}. ${decision.reason}."
        decision.proposed?.let { target ->
            val token=state.token
            ownsPlayback=true;settleUntil=Long.MAX_VALUE
            runtime.execute(listOf(ManualRequest.SelectProgram(target)),false,true,{false},{ !closed && running===this@SpeechAutomaticService },{}) { _,success ->
                ownsPlayback=false
                if(!closed) {
                    val completed=SystemClock.elapsedRealtime()
                    state=completeMasking(state,token,success,completed)
                    if(!success) finish("Program playback failed or interrupted; check your aids before restarting")
                    else { lastSuccessMillis=state.lastSuccess;settleUntil=completed+request.settings.waitSeconds.coerceAtLeast(10)*1000L
                        detail="Sent ${label(target)}. Waiting before fresh checks.";publish() }
                }
            }
        }
    }
    private fun label(slot:Int?)=runtime.fitting?.programs?.find { it.slot==slot }?.name ?: "Unassigned"
    fun summary()=buildString {
        append("Speech-aware Automatic On\nMicrophone: ${if(watchMode) "watch" else "phone"}.\n$detail")
        append("\nSound type: ${latest.soundType.label}. Masking: ${latest.severity.label}.")
        val considered=request.choices.target(latest.severity)
        append("\nProgram considered: ${if(considered!=null) label(considered) else "keep current program"}.")
        if(ownsPlayback) append("\nProgram request in progress: ${label(proposal)}.")
        append("\nLast program requested: ${label(runtime.requested.program)}.")
        append("\nAid reception and improved understanding are not confirmed.")
        val remaining=state.lastSuccess?.let { ((request.settings.cooldownSeconds*1000L-(SystemClock.elapsedRealtime()-it)).coerceAtLeast(0)+999)/1000 } ?: 0
        if(remaining>0) append("\nNext change can happen in $remaining seconds")
        append("\n$samples completed checks. Automatic can send program-change tones.")
    }
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,7305,Intent(this,HomeActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val off=PendingIntent.getService(this,7305,Intent(this,javaClass).setAction(OFF),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).apply {
            if(Build.VERSION.SDK_INT>=31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }.setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("HeySig · speech-aware Automatic On")
            .setContentText(detail).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null,"Off",off).build()).build()
    }
    private fun publish() { if(!closed) getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification()) }
    private fun finish(why:String) {
        if(closed) return
        closed=true;lastStatus=why;if(!watchMode) audio.stop();watchEndpoint?.close();watchEndpoint=null;handler.removeCallbacksAndMessages(null)
        if(ownsPlayback) runtime.cancelled.set(true)
        if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
        if(running===this) running=null
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() { finish("Automatic Off; microphone stopped");super.onDestroy() }
    companion object {
        private const val CHANNEL="heysig-speech-automatic";private const val NOTIFICATION=7305
        private const val OFF="com.heysig.app.SPEECH_AUTOMATIC_OFF"
        private data class Request(val token:String,val settings:NoiseSettings,val choices:MaskingChoices,val fitting:String,val watchNode:String?)
        private var pending:Request?=null
        private var lastSuccessMillis:Long?=null
        @Volatile var running:SpeechAutomaticService?=null;private set
        var lastStatus="";private set
        val active get()=pending!=null || running!=null
        fun launch(activity:Activity,settings:NoiseSettings,choices:MaskingChoices,fitting:String,service:Class<out SpeechAutomaticService> = SpeechAutomaticService::class.java,watchNode:String?=null) {
            check(!active) { "Automatic is already On" }
            val token=java.util.UUID.randomUUID().toString();pending=Request(token,settings,choices,fitting,watchNode)
            try { activity.startForegroundService(Intent(activity,service).putExtra("token",token)) }
            catch(e:Exception) { pending=null;throw e }
        }
        fun cancel() { pending=null;running?.finish("Automatic Off; microphone stopped") }
    }
}
