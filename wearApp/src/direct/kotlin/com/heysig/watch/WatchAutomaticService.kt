package com.heysig.watch

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import com.heysig.app.SpeechSeparationDetector
import com.heysig.audio.EnvironmentAudio
import com.heysig.noise.*
import org.json.JSONObject
import java.util.UUID

/** Explicit bounded watch microphone session. The phone alone validates and executes aid commands. */
class WatchAutomaticService:Service() {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var closed=false
    private var connected=false
    private var pendingRpc=false
    private var deadline=0L
    private var notificationBy=0L
    private var connectBy=0L
    private var notificationSeen=false
    private var nextCapture=0L
    private var lastContact=0L
    private var waitSeconds=10
    @Volatile private var sequence=0L
    val sentSamples get()=sequence
    private var wakeLock:PowerManager.WakeLock?=null
    private val client by lazy { WatchAutomaticClient(this) { why -> finish(why) } }
    private val detector by lazy { SpeechSeparationDetector.load(this) }
    @Volatile private var evidence=MaskingEvidence()
    @Volatile private var rms=-120.0
    @Volatile var samples=0;private set
    @Volatile var cpuNanos=0L;private set
    var detail="Connecting to phone Automatic";private set
    private var phoneStatus=""
    private val audio by lazy { EnvironmentAudio(this,{ !closed && connected && notificationSeen },
        observeEvidence={ pcm,pool,frames ->
            evidence=detector.classify(pcm,pool,frames)
            rms=NoiseAccumulator(16000,3).apply { add(pcm,pcm.size) }.measurement().rmsDbfs
        },observeInferenceCpu={ cpuNanos+=it },microphoneSession={ !closed && running===this }) }
    val microphoneBusy get()=audio.busy()
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==OFF) { finish("Watch Automatic Off");return START_NOT_STICKY }
        if(running===this) return START_NOT_STICKY
        val token=pendingStart;pendingStart=null
        if(token==null || token!=intent?.getStringExtra("token")) { stopSelf();return START_NOT_STICKY }
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "Allow the watch microphone first" }
            check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock the watch to start Automatic" }
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"Watch Automatic noise",NotificationManager.IMPORTANCE_LOW))
            check(manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE) { "Allow watch Automatic notifications first" }
            WatchVoiceSessionService.stop(this);SleepingShakeService.stop(this)
            running=this;lastStatus="";deadline=SystemClock.elapsedRealtime()+8*60*60_000L;notificationBy=SystemClock.elapsedRealtime()+5_000;connectBy=SystemClock.elapsedRealtime()+15_000
            startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:WatchAutomatic").apply { acquire(8*60*60_000L+10_000) }
            pendingRpc=true
            client.connect { reply ->
                if(closed) return@connect
                pendingRpc=false
                if(accept(reply)) { connected=true;detail="Watch microphone ready" }
            }
            handler.post(tick)
        } catch(e:Exception) { finish(e.message ?: "Watch Automatic unavailable") }
        return START_NOT_STICKY
    }
    private fun accept(reply:JSONObject):Boolean {
        if(closed) return false
        if(!reply.optBoolean("active",false)) { finish(reply.optString("text","Phone Automatic stopped"));return false }
        check(reply.getString("profile")==WatchAutomaticProfile.ID)
        waitSeconds=reply.getInt("waitSeconds").also { require(it in 10..300) }
        lastContact=SystemClock.elapsedRealtime();phoneStatus=reply.optString("text")
        return true
    }
    private val tick=object:Runnable {
        override fun run() {
            if(closed) return
            val now=SystemClock.elapsedRealtime();val manager=getSystemService(NotificationManager::class.java)
            val present=manager.activeNotifications.any { it.id==ID };if(present) notificationSeen=true
            val why=when {
                !connected && now>=connectBy -> "Phone Automatic connection timed out"
                now>=deadline -> "Eight-hour watch Automatic session finished"
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED -> "Watch microphone permission removed"
                !manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE -> "Watch Automatic notification removed"
                !present && (notificationSeen || now>=notificationBy) -> "Watch Automatic notification removed"
                connected && now-lastContact>=WatchAutomaticProfile.CONTACT_TIMEOUT_MILLIS -> "Phone disconnected; restart explicitly"
                else -> null
            }
            if(why!=null) { finish(why);return }
            if(present && connected && !pendingRpc && !audio.busy() && (now>=nextCapture || now-lastContact>=10_000)) {
                pendingRpc=true
                client.request("status") { reply ->
                    if(closed) return@request
                    pendingRpc=false
                    if(accept(reply)) {
                        if(SystemClock.elapsedRealtime()>=nextCapture && reply.optBoolean("captureAllowed",false)) capture()
                        else detail=if(reply.optBoolean("captureAllowed",false)) "Microphone idle between checks" else "Paused for phone audio, settling or manual priority"
                        publish()
                    }
                }
            }
            handler.postDelayed(this,1000)
        }
    }
    private fun capture() {
        if(closed || audio.busy()) return
        evidence=MaskingEvidence();detail="Watch microphone active · three seconds";publish()
        audio.sample(3) { result,error,capturedAt -> handler.post {
            if(closed) return@post
            if(result==null || error!=null) { finish(error ?: "Watch microphone unavailable");return@post }
            samples++;nextCapture=SystemClock.elapsedRealtime()+waitSeconds*1000L
            val value=evidence;val level=rms;val id=UUID.randomUUID().toString()
            pendingRpc=true
            client.request("prepare",JSONObject().put("id",id)) leaseReply@{ lease ->
                if(closed) return@leaseReply
                if(!accept(lease)) return@leaseReply
                if(!lease.optBoolean("captureAllowed",false)) { pendingRpc=false;detail="Check discarded for phone priority; fresh watch checks needed";publish();return@leaseReply }
                val age=SystemClock.elapsedRealtime()-capturedAt
                if(age !in 0..WatchAutomaticProfile.MAX_AGE_MILLIS) { finish("Watch sample expired; restart explicitly");return@leaseReply }
                val body=JSONObject().put("profile",WatchAutomaticProfile.ID).put("sequence",sequence)
                    .put("ageMillis",age).put("severity",value.severity.name).put("type",value.soundType.name)
                    .put("score",value.score).put("rmsDbfs",level)
                client.request("sample",JSONObject().put("id",id).put("token",lease.getString("token")).put("body",body)) sampleReply@{ reply ->
                    if(closed) return@sampleReply
                    pendingRpc=false
                    if(accept(reply)) { sequence++;detail="${value.severity.label}. Microphone idle between checks.";publish() }
                }
            }
        } }
    }
    fun summary()="Watch Automatic On\n$detail\n$samples checks · watch microphone\n$phoneStatus"
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,7405,Intent(this,WatchAutomaticActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val off=PendingIntent.getService(this,7405,Intent(this,javaClass).setAction(OFF),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).apply { if(Build.VERSION.SDK_INT>=31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .setSmallIcon(com.heysig.watch.R.drawable.ic_launcher_foreground).setContentTitle("HeySig · Watch Automatic On").setContentText(detail)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Off",off).build()).build()
    }
    private fun publish() { if(!closed) getSystemService(NotificationManager::class.java).notify(ID,notification()) }
    private fun finish(why:String) {
        if(closed) return
        closed=true;lastStatus=why;audio.stop();handler.removeCallbacksAndMessages(null)
        client.stopPhone();client.close();if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
        if(running===this) running=null
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() { finish("Watch Automatic Off; microphone stopped");super.onDestroy() }
    override fun onTaskRemoved(rootIntent:Intent?) { finish("Watch app stopped");super.onTaskRemoved(rootIntent) }
    companion object {
        private const val CHANNEL="heysig-watch-automatic";private const val ID=7405;private const val OFF="com.heysig.watch.AUTOMATIC_OFF"
        @Volatile var running:WatchAutomaticService?=null;private set
        var lastStatus="";private set
        private var pendingStart:String?=null
        fun launch(activity:Activity) {
            if(running!=null) return
            val token=UUID.randomUUID().toString();pendingStart=token
            try { activity.startForegroundService(Intent(activity,WatchAutomaticService::class.java).putExtra("token",token)) }
            catch(e:Exception) { pendingStart=null;throw e }
        }
        fun stop(context:android.content.Context) { pendingStart=null;running?.finish("Watch Automatic Off; microphone stopped") }
    }
}
