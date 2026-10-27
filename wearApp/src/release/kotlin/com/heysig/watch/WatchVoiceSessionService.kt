package com.heysig.watch

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.heysig.companion.PrivacyNotice
import com.heysig.voice.*
import com.heysig.voice.WatchVoiceTurn.Stage.*
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** User-started bounded microphone foreground session; idle between deliberate shakes. */
class WatchVoiceSessionService : Service(), SensorEventListener {
    private val handler=Handler(Looper.getMainLooper())
    private val turn=WatchVoiceTurn()
    private val shakes=ShakeGate.watch()
    private val handoff=WatchSpeechHandoff()
    private lateinit var sensors: SensorManager
    private lateinit var client: WatchVoiceClient
    private lateinit var wake: PowerManager.WakeLock
    private var speech: TextToSpeech?=null
    private var speechReady=false
    private var stopping=false
    @Volatile private var motionSamples=0L
    @Volatile private var motionMaxG=0.0
    @Volatile private var lastMotionElapsed=0L
    @Volatile private var shakeTriggers=0L
    private val session = GestureSession()
    private var recognizer: SpeechRecognizer?=null
    private var afterSpeech: (() -> Unit)?=null
    private var utterance: String?=null
    private val conversation=UUID.randomUUID().toString()
    private val turnTimeout=Runnable { endTurn("Timed out. Check last result if a command was sent; shake for a new request.") }
    private val captureTimeout=Runnable { endTurn("No speech received. Shake to try again.",speak=true) }
    private val expire=Runnable { stopSelf() }
    var status="Shake voice ready"; private set
    val busy get()=turn.stage!=IDLE
    val choices get()=client.choices
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        sensors=getSystemService(SensorManager::class.java)
        client=WatchVoiceClient(this)
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:watchVoiceTurn").apply { setReferenceCounted(false) }
        speech=TextToSpeech(this) { result ->
            handler.post { speechReady=result==TextToSpeech.SUCCESS && (speech?.setLanguage(Locale.US) ?: -1)>=TextToSpeech.LANG_AVAILABLE }
        }
        speech?.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { finishSpeech(id) } }
            @Deprecated("Platform callback") override fun onError(id: String?) { handler.post { finishSpeech(id) } }
        })
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==STOP) { stopListening();return START_NOT_STICKY }
        if(intent?.action!=START || !startAuthorized) { if(instance==null) stopSelf();return START_NOT_STICKY }
        startAuthorized=false
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
            !SpeechRecognizer.isRecognitionAvailable(this) || !getSystemService(NotificationManager::class.java).areNotificationsEnabled()) { stopSelf();return START_NOT_STICKY }
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true)
        if(sensor==null) { stopSelf();return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Watch shake voice session",NotificationManager.IMPORTANCE_LOW))
        if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) { stopSelf();return START_NOT_STICKY }
        try {
            val notification=notification("Shake voice ready · microphone idle")
            if(Build.VERSION.SDK_INT>=34) startForeground(NOTIFICATION,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION,notification)
            if(!sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME,100_000)) { stopSelf();return START_NOT_STICKY }
            session.start(SystemClock.elapsedRealtime())
            instance=this;SleepingShakeService.stop(this)
            handler.postDelayed(expire,8*60*60*1000L)
            publish("Shake voice ready. Speech follows the phone settings.")
            if(intent.getBooleanExtra("capture",false)) capture()
        } catch(e: RuntimeException) { stopSelf() }
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val open=PendingIntent.getActivity(this,91,Intent(this,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,92,Intent(this,WatchVoiceSessionService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("HeySig shake voice").setContentText(text.take(160)).setContentIntent(open)
            .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true).addAction(0,"Stop",stop)
        OngoingActivity.Builder(this,NOTIFICATION,builder).setStaticIcon(android.R.drawable.ic_btn_speak_now).setTouchIntent(open).build().apply(this)
        return builder.build()
    }
    private fun usable(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED &&
        getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
        getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    private fun publish(text: String) {
        status=text;observer?.invoke(text,choices)
        if(instance!==this) return
        if(!usable() || (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) { stopSelf();return }
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(if(stopping) "Stopping voice session" else if(turn.stage==LISTENING) "Listening · up to ten seconds" else if(busy) "Processing voice command" else "Ready to shake · microphone idle"))
        } catch(e: SecurityException) { stopSelf() }
    }
    private fun access() = WatchVoiceAccess(session.active(SystemClock.elapsedRealtime()),
        checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
        usable(),getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    private fun begin(): Long? {
        if(stopping) return null
        if(!access().mayCapture) { stopSelf();return null }
        val id=turn.begin(SystemClock.elapsedRealtime()) ?: return null
        wake.acquire(60_000L)
        handler.postDelayed(turnTimeout,60_000)
        publish("Connecting…")
        return id
    }
    fun capture() {
        val id=begin() ?: return
        ShakeTrigger.acknowledge(this)
        client.connect { reply ->
            if(!turn.fresh(id,SystemClock.elapsedRealtime())) return@connect
            if(reply.optString("phase")!="READY") { endTurn(reply.optString("text","Phone unavailable"));return@connect }
            if(!turn.advance(id,CONNECTING,PROMPTING)) return@connect
            publish(if(choices.prompts) "Say a command" else "Ready to listen")
            speakIf("Say a command",choices.prompts) { listen(id) }
        }
    }
    private fun listen(id: Long) {
        if(!turn.fresh(id,SystemClock.elapsedRealtime()) || !turn.advance(id,PROMPTING,LISTENING)) return
        if(!access().mayCapture || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            endTurn("Voice session or microphone permission is unavailable. Start a new session on the watch.");return
        }
        publish("Listening…")
        handoff.begin(SystemClock.elapsedRealtime())
        try {
            val recorder=SpeechRecognizer.createSpeechRecognizer(this)
            recognizer=recorder
            recorder.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int,params: Bundle?) {}
                override fun onError(error: Int) {
                    if(turn.active(id) && turn.stage==LISTENING) endTurn(if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Microphone permission is off. Enable it on the watch." else "No speech recognized. Shake to try again.",speak=true)
                }
                override fun onResults(results: Bundle?) {
                    if(!turn.active(id) || turn.stage!=LISTENING) return
                    handler.removeCallbacks(captureTimeout)
                    val words=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if(!handoff.receive(words,SystemClock.elapsedRealtime())) { endTurn("No valid speech recognized. Shake to try again.",speak=true);return }
                    val result=handoff.take(SystemClock.elapsedRealtime()) ?: return
                    releaseRecognizer()
                    if(!turn.advance(id,LISTENING,SENDING)) return
                    submit(id,result.alternatives) { result.fresh(SystemClock.elapsedRealtime()) }
                }
            })
            handler.postDelayed(captureTimeout,10_000)
            recorder.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
            })
        } catch(e: RuntimeException) { endTurn("Watch speech service unavailable. Use Type command.",speak=true) }
    }
    fun typed(text: String,alternatives: List<String>) {
        if(alternatives.firstOrNull()!=text) { publish("Invalid command alternatives. Nothing sent.");return }
        val id=begin() ?: return
        val captured=SystemClock.elapsedRealtime()
        client.connect { reply ->
            if(!turn.fresh(id,SystemClock.elapsedRealtime())) return@connect
            if(reply.optString("phase")!="READY") { endTurn(reply.optString("text","Phone unavailable"));return@connect }
            if(turn.advance(id,CONNECTING,SENDING)) submit(id,alternatives) { SystemClock.elapsedRealtime()-captured in 0 until 15_000L }
        }
    }
    fun checkLastResult() {
        val id=begin() ?: return
        client.connect { reply ->
            if(!turn.fresh(id,SystemClock.elapsedRealtime())) return@connect
            if(reply.optString("phase")!="READY") { endTurn(reply.optString("text","Phone unavailable"));return@connect }
            if(!turn.advance(id,CONNECTING,REPLYING)) return@connect
            client.lastResult { result ->
                if(!turn.active(id)) return@lastResult
                val text=result.optString("text","No retained result")
                publish(text);speakIf(text,choices.responses) { endTurn(text) }
            }
        }
    }
    private fun submit(id: Long,alternatives: List<String>,fresh: ()->Boolean) {
        publish("Sending…")
        client.command(alternatives.first(),alternatives,conversation,{ turn.fresh(id,SystemClock.elapsedRealtime()) && fresh() }) { answer ->
            if(!turn.advance(id,SENDING,REPLYING)) return@command
            val text=answer.optString("text","No confirmed result. Check the phone.")
            publish(text)
            val prompt=answer.optBoolean("prompt",answer.optString("phase")=="CLARIFY")
            speakIf(text,if(prompt) choices.prompts else choices.responses) { endTurn(text) }
        }
    }
    private fun speakIf(text: String,enabled: Boolean,done: ()->Unit) {
        if(!enabled) { done();return }
        val id=UUID.randomUUID().toString()
        utterance=id;afterSpeech=done
        startSpeech(id,text,0)
    }
    private fun startSpeech(id: String,text: String,attempt: Int) {
        if(utterance!=id || stopping) return
        if(!speechReady && attempt<15) {
            handler.postDelayed({ startSpeech(id,text,attempt+1) },200);return
        }
        if(!speechReady) { publish("$status · Watch speech output unavailable");finishSpeech(id);return }
        if(speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,id)!=TextToSpeech.SUCCESS) finishSpeech(id)
        else handler.postDelayed({ finishSpeech(id) },if(turn.stage==PROMPTING) 5_000 else 25_000)
    }
    private fun finishSpeech(id: String?) {
        if(id==null || utterance!=id) return
        utterance=null;val next=afterSpeech;afterSpeech=null
        speech?.stop();next?.invoke()
    }
    private fun releaseRecognizer() {
        handler.removeCallbacks(captureTimeout)
        val old=recognizer;recognizer=null
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
        handoff.cancel()
    }
    private fun endTurn(text: String,speak: Boolean=false) {
        releaseRecognizer()
        if(speak && choices.responses && turn.stage!=REPLYING) {
            // Consume this capture before speaking an error; callbacks cannot submit it later.
            turn.cancel();val id=turn.begin(SystemClock.elapsedRealtime())!!
            turn.advance(id,CONNECTING,REPLYING)
            publish(text);speakIf(text,true) { endTurn(text) };return
        }
        turn.cancel();utterance=null;afterSpeech=null;speech?.stop()
        handler.removeCallbacks(turnTimeout)
        client.close()
        if(wake.isHeld) wake.release()
        publish(text)
    }
    fun stopListening() {
        if(stopping) return
        stopping=true
        val commandPending=turn.stage==SENDING
        turn.cancel();releaseRecognizer();utterance=null;afterSpeech=null;speech?.stop()
        handler.removeCallbacks(turnTimeout);client.close()
        publish("Shake voice stopped")
        if(!commandPending) { stopSelf();return }
        // Fresh Stop only. The previous request ID remains available for result lookup.
        client.connect { reply ->
            if(reply.optString("phase")!="READY") stopSelf()
            else client.command("stop",listOf("stop"),conversation,{ true }) { stopSelf() }
        }
        handler.postDelayed({ stopSelf() },10_000)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(!access().mayCapture) { stopSelf();return }
        val v=event.values
        val g=kotlin.math.sqrt((v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).toDouble())/SensorManager.GRAVITY_EARTH
        motionSamples++;lastMotionElapsed=SystemClock.elapsedRealtime()
        if(g.isFinite() && g>motionMaxG) motionMaxG=g
        if(stopping || busy || instance!==this) return
        if(shakes.sample(g,event.timestamp/1_000_000L)) { shakeTriggers++;capture() }
    }
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        // Volatile aggregate counters only: no audio, transcript, raw samples or device identifiers.
        writer.println("watchVoiceSession active=${session.active(SystemClock.elapsedRealtime())} stage=${turn.stage}")
        writer.println("motionSamples=$motionSamples maxG=$motionMaxG shakeTriggers=$shakeTriggers lastMotionAgeMs=${if(lastMotionElapsed==0L) -1L else SystemClock.elapsedRealtime()-lastMotionElapsed}")
        writer.println("speechPrompts=${choices.prompts} speechResponses=${choices.responses}")
    }
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) {}
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        if(instance===this) instance=null
        startAuthorized=false;session.stop();turn.cancel();releaseRecognizer();utterance=null;afterSpeech=null
        speech?.shutdown();sensors.unregisterListener(this);client.close();handler.removeCallbacksAndMessages(null)
        if(wake.isHeld) wake.release()
        observer?.invoke("Shake voice stopped",choices)
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    companion object {
        private const val CHANNEL="watch-voice-session"
        private const val NOTIFICATION=91
        private const val START="start-watch-voice"
        private const val STOP="stop-watch-voice"
        private var startAuthorized=false
        var instance: WatchVoiceSessionService?=null;private set
        var observer: ((String,WatchSpeechChoices)->Unit)?=null
        fun start(activity: Activity,capture: Boolean) {
            instance?.let { if(capture) it.capture();return }
            startAuthorized=true
            try { activity.startForegroundService(Intent(activity,WatchVoiceSessionService::class.java).setAction(START).putExtra("capture",capture)) }
            catch(e: RuntimeException) { startAuthorized=false;throw e }
        }
        fun stop(context: android.content.Context) { instance?.stopListening() ?: context.stopService(Intent(context,WatchVoiceSessionService::class.java)) }
    }
}
