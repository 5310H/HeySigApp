package com.heysig.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import com.heysig.app.voice.HandsFreeService
import com.heysig.control.ManualRequest
import com.heysig.noise.*

/** Explicit eight-hour session. No persisted enable flag, boot start, sticky restart or replay. */
open class AutomaticNoiseService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    protected open val runtime by lazy { ManualRuntime.get(this) }
    private lateinit var settings: NoiseSettings
    private lateinit var fitting: String
    private lateinit var source: String
    private var state = NoiseAutomaticState()
    private var endsAt = 0L
    @Volatile private var settleUntil = 0L
    @Volatile private var closed = false
    private var ownsPlayback = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var settingsBytes: String? = null
    var learner: NoiseThresholdLearner? = null; private set
    var observation = NoiseObservation(); private set
    val samples = ArrayDeque<Pair<Long,Double>>()
    var detail = "Starting microphone session"; private set
    private val sampler by lazy { PhoneNoiseSampler(this) { !closed && safety().blocked()==null && SystemClock.elapsedRealtime()>=settleUntil } }
    private fun signature() = runtime.fitting?.programs.orEmpty().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    private fun safety(): NoiseSafety = NoiseSafety(
        foreground=false, unlocked=false, microphoneSession=!closed && running===this,
        audioFree=!runtime.busy.get() && !HandsFreeService.manualSpeechBusy &&
            HandsFreeService.externalSpeechBusy?.invoke()!=true && HandsFreeService.running?.isListening!=true &&
            !runtime.fittingWorkflow.pending && !runtime.personalPhrases.awaitingConfirmation && !runtime.sleepRoutine.hasDraft,
        manualOverride=runtime.sleepRoutine.state.override, sleeping=runtime.sleepRoutine.state.blocksNoise,
        protectedPrograms=runtime.fitting?.programs.orEmpty().filter { isTherapyProgramName(it.name) }.map { it.slot }.toSet())
    private val watchdog = object: Runnable {
        override fun run() {
            if(closed) return
            val manager=getSystemService(NotificationManager::class.java)
            val reason=when {
                SystemClock.elapsedRealtime()>=endsAt -> "Eight-hour session finished; turn On to start another"
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED -> "Microphone permission removed"
                !manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE -> "Notifications disabled"
                manager.activeNotifications.none { it.id==NOTIFICATION } -> "Automatic notification dismissed"
                signature()!=fitting -> "Program setup changed; review noise assignments"
                getSharedPreferences("heysig-noise-v1",MODE_PRIVATE).getString("settings",null)!=settingsBytes -> "Saved noise settings changed"
                runtime.sleepRoutine.state.blocksNoise -> "Manual control or Therapy has priority"
                else -> null
            }
            if(reason!=null) { finish(reason);return }
            handler.postDelayed(this,1000)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==OFF) { stop(this);return START_NOT_STICKY }
        if(running===this) return START_NOT_STICKY
        val request=pending?.takeIf { it.token==intent?.getStringExtra("token") }
        pending=null
        if(request==null) { stopSelf();return START_NOT_STICKY }
        try {
            settings=request.settings; fitting=request.fitting
            check(signature()==fitting) { "Program setup changed" }
            settings.validate(runtime.fitting?.programs.orEmpty().map { it.slot }.toSet())
            settingsBytes=getSharedPreferences("heysig-noise-v1",MODE_PRIVATE).getString("settings",null)
            check(runtime.requested.program!=null && !runtime.sleepRoutine.state.blocksNoise) { "Choose a waking program before starting" }
            state=NoiseAutomaticState(lastSuccessMillis=lastSuccessMillis)
            running=this; lastStatus=""; endsAt=SystemClock.elapsedRealtime()+8*60*60*1000L
            val notifications=getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Automatic noise",NotificationManager.IMPORTANCE_LOW))
            check(notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE) { "Allow Automatic noise notifications first" }
            if(Build.VERSION.SDK_INT>=30) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:AutomaticNoise").apply { acquire(8*60*60*1000L+10_000L) }
            source=sampler.sourceIdentity()
            learner=request.learner
            detail="Waiting for first measurement · you can lock the screen"
            check(sampler.start(settings,true) { value,error,capturedAt,paused -> handler.post {
                if(closed) return@post
                if(!isFreshNoiseSample(capturedAt,SystemClock.elapsedRealtime(),settings.freshnessSeconds)) {
                    state=state.copy(observation=NoiseObservation());observation=state.observation;detail="Delayed sample discarded; measuring again";publish();return@post
                }
                if(capturedAt<settleUntil) return@post
                if(source!=runCatching { sampler.sourceIdentity() }.getOrNull()) { finish("Microphone route changed; turn On again when ready");return@post }
                if(value==null || value.flags.isNotEmpty()) {
                    state=state.copy(observation=NoiseObservation());observation=state.observation
                    if(value==null && !paused) { finish(error ?: "Microphone failed");return@post }
                    detail=if(paused) "Paused while the phone is speaking or playing sound. It will try again." else "Sample rejected: ${value!!.flags.joinToString()}; measuring again"
                    publish();return@post
                }
                samples.addLast(capturedAt to value.rmsDbfs)
                while(samples.size>120) samples.removeFirst()
                // Learning remains foreground-only and adds no microphone windows.
                if(foregroundSince?.let { it<=capturedAt-(value.durationSeconds*1000).toLong() }==true && safety().blocked()==null && state.pendingTarget==null) learner?.add(value.rmsDbfs,capturedAt)
                val decision=automaticNoise(state,value.rmsDbfs,capturedAt,settings,safety(),runtime.requested.program)
                state=decision.state;observation=state.observation
                detail=when(decision.reason) {
                    "Confirming target" -> "Checking the sound before changing programs"
                    "Already last requested; no command" -> "This program already matches the sound"
                    "Cooldown; confirmations cleared" -> "Waiting a little before another change"
                    "Confirmed target" -> "Changing program"
                    else -> if(decision.reason.startsWith("Level ") && decision.reason.contains("no assigned program")) "No program chosen for this sound level. Keeping the current program." else decision.reason
                }
                decision.target?.let { target ->
                    val token=state.token
                    ownsPlayback=true;settleUntil=Long.MAX_VALUE
                    runtime.execute(listOf(ManualRequest.SelectProgram(target)),false,true,{false},{ !closed && running===this },{}) { _,success ->
                        ownsPlayback=false
                        if(!closed) {
                            state=completeNoise(state,token,success,SystemClock.elapsedRealtime())
                            if(!success) finish("Program playback failed or interrupted; review aids before restarting")
                            else {
                                lastSuccessMillis=state.lastSuccessMillis
                                settleUntil=SystemClock.elapsedRealtime()+settings.waitSeconds.coerceAtLeast(10)*1000L
                                detail="Sent ${label(target)}. Listening again shortly.";publish()
                            }
                        }
                    }
                }
                publish()
            } }) { "Previous microphone capture is stopping; try again" }
            handler.post(watchdog);publish()
        } catch(e: Exception) { finish(e.message ?: "Could not start Automatic") }
        return START_NOT_STICKY
    }
    private fun label(slot: Int?)=runtime.fitting?.programs?.find { it.slot==slot }?.name ?: "Unassigned"
    fun summary(detailed: Boolean = false): String = buildString {
        append("Automatic is On")
        append("\nProgram last sent: ${runtime.requested.program?.let { label(it) } ?: "change in progress"}")
        observation.target?.let { append("\nChecking ${label(it)} · ${observation.count} of ${settings.confirmations} sound checks") }
        append("\n$detail")
        val remaining=state.lastSuccessMillis?.let { ((settings.cooldownSeconds*1000L-(SystemClock.elapsedRealtime()-it)).coerceAtLeast(0)+999)/1000 } ?: 0
        if(remaining>0) append("\nNext change can happen in ${remaining} seconds")
        val minutes=((endsAt-SystemClock.elapsedRealtime()).coerceAtLeast(0)+59_999)/60_000
        append("\nStops automatically in ${minutes/60} hr ${minutes%60} min")
        if(detailed) {
            samples.lastOrNull()?.let { append("\n%.1f dBFS · relative microphone level, not sound-pressure dB".format(java.util.Locale.US,it.second)) }
            observation.level?.let { append("\nMeasured level $it of 6") }
        }
    }
    private fun notification(): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,HomeActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val off=PendingIntent.getService(this,1,Intent(this,javaClass).setAction(OFF),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("HeySig · Automatic On")
            .setContentText(detail).setStyle(Notification.BigTextStyle().bigText(detail+". Microphone measures noise; audio is discarded. Tap Off to stop."))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Off",off).build()).build()
    }
    private fun publish() {
        if(closed) return
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            finish("Notification permission removed");return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification())
    }
    private fun finish(reason: String) {
        if(closed) return
        closed=true;lastStatus=reason;sampler.stop();handler.removeCallbacksAndMessages(null)
        if(ownsPlayback) runtime.cancelled.set(true)
        if(wakeLock?.isHeld==true) wakeLock?.release()
        wakeLock=null
        if(running===this) running=null
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() { finish("Automatic stopped by Android; turn On to restart");super.onDestroy() }
    companion object {
        private const val CHANNEL="heysig-automatic-noise"
        private const val NOTIFICATION=7304
        private const val OFF="com.heysig.app.AUTOMATIC_NOISE_OFF"
        private data class Request(val token:String,val settings:NoiseSettings,val fitting:String,val learner:NoiseThresholdLearner?)
        private var pending: Request?=null
        private var lastSuccessMillis: Long?=null
        @Volatile var running: AutomaticNoiseService?=null; private set
        var lastStatus=""; private set
        // Monotonic start of the current visible interval. No Activity reference is retained.
        @Volatile var foregroundSince: Long?=null
        val active get()=pending!=null || running!=null
        fun launch(activity: Activity, settings: NoiseSettings, fitting: String, learner: NoiseThresholdLearner?, service: Class<out AutomaticNoiseService> = AutomaticNoiseService::class.java) {
            SpeechAutomaticService.cancel()
            check(!active) { "Automatic is already On" }
            val token=java.util.UUID.randomUUID().toString()
            pending=Request(token,settings,fitting,learner)
            try { activity.startForegroundService(Intent(activity,service).putExtra("token",token)) }
            catch(e:Exception) { pending=null;throw e }
        }
        fun cancel() {
            pending=null
            running?.finish("Automatic Off · microphone stopped")
        }
        fun stop(context: Context) {
            cancel()
            context.stopService(Intent(context,AutomaticNoiseService::class.java))
        }
    }
}
