package com.heysig.app.voice

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import com.heysig.app.*
import com.heysig.control.*
import com.heysig.voice.*
import com.heysig.noise.*
import java.util.Locale

/** Explicit, non-sticky microphone session. The UI, not a receiver/boot event, starts it. */
class HandsFreeService : Service() {
    private val handler=Handler(Looper.getMainLooper())
    private val session=HandsFreeSession()
    private val runtime by lazy { ManualRuntime.get(this) }
    private val prefs by lazy { getSharedPreferences("heysig-core-v1",MODE_PRIVATE) }
    private var capture: WakeCapture?=null
    private var captureClosing=false
    private var recognizer: SpeechRecognizer?=null
    private var synthesis: TextToSpeech?=null
    private var speechReady=false
    private var speaking=false
    private var currentUtterance: String?=null
    private val shake by lazy { ShakeTrigger(this) {
        if(session.mode==HandsFreeSession.Mode.WAKE && !runtime.busy.get() && !speaking && !manualSpeechBusy && externalSpeechBusy?.invoke()!=true) {
            val token=session.generation
            holdCommandLock();stopInputs()
            if(session.wake(token)) {
                val generation=session.generation
                awaitGestureCapture(generation,0)
            }
        }
    } }
    private var wakeLock: PowerManager.WakeLock?=null
    private var commandWakeLock: PowerManager.WakeLock?=null
    private var pendingWake: Long?=null
    private var connection: HandsFreeWatchConnection?=null
    private var pending: Pair<String,AppIntent>?=null
    private var pendingUntil=0L
    private var restoreSnapshot: RequestedState?=null
    fun isPromptFor(origin: String) = runtime.sleepRoutine.isDraftFor(origin) || pending?.first==origin || runtime.personalPhrases.isPromptFor(origin) || runtime.fittingWorkflow.isPromptFor(origin)
    private var repeatRequest: ManualRequest?=null
    private var foreground=false
    private var sessionEndsAt=0L
    private var closed=false
    val isListening get()=session.mode in listOf(HandsFreeSession.Mode.WAKE,HandsFreeSession.Mode.COMMAND)
    val commandInProgress get()=session.mode==HandsFreeSession.Mode.COMMAND || pending!=null
    val listeningLabel get()=when(session.mode) {
        HandsFreeSession.Mode.WAKE -> "Listening for Hey Sig"
        HandsFreeSession.Mode.COMMAND -> "Listening for command"
        HandsFreeSession.Mode.PAUSED -> "Listening paused"
        HandsFreeSession.Mode.OFF -> "Listening Off"
    }
    var status="Listening Off"; private set
    private val permissionWatchdog=object: Runnable {
        override fun run() {
            if(closed) return
            if(SystemClock.elapsedRealtime()>=sessionEndsAt) { end();return }
            val notifications=getSystemService(NotificationManager::class.java)
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
                !notifications.areNotificationsEnabled() || notifications.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) {
                end();return
            }
            handler.postDelayed(this,5000)
        }
    }
    private val commandTimeout=Runnable { recognizer?.cancel(); recognizer?.destroy(); recognizer=null; completeCommand("No command heard") }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Hands-free listening",NotificationManager.IMPORTANCE_LOW))
        synthesis=TextToSpeech(this) { speechReady=it==TextToSpeech.SUCCESS; if(speechReady) synthesis?.language=Locale.US }
        synthesis?.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) { handler.post { if(id==currentUtterance) speaking=true } }
            override fun onDone(id: String?) { finish(id,true) }
            @Deprecated("Platform callback") override fun onError(id: String?) { finish(id,true) }
            override fun onStop(id: String?,interrupted: Boolean) { finish(id,false) }
            private fun finish(id: String?,proceed: Boolean) { handler.post {
                if(id==null || id!=currentUtterance) return@post
                currentUtterance=null;speaking=false
                val token=pendingWake?.takeIf { id=="handsfree-wake-$it" };pendingWake=null
                if(proceed && token!=null && session.accepts(token)) recognize(token) else resumeWhenQuiet()
            } }

        })
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent==null) { stopSelf(); return START_NOT_STICKY }
        val action=intent.action
        if(foreground && action==START) { connection?.close();connection=null }
        if(!foreground) {
            // No background service or PendingIntent can create a new session.
            if(action!=START || !uiStartAuthorized || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                stopSelf(); return START_NOT_STICKY
            }
            uiStartAuthorized=false
            if(!getSharedPreferences("heysig-handsfree",MODE_PRIVATE).getBoolean("disclosureV1",false)) { stopSelf();return START_NOT_STICKY }
            try {
                if(Build.VERSION.SDK_INT>=30) startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                else startForeground(ID,notification())
                sessionEndsAt=SystemClock.elapsedRealtime()+8*60*60*1000L
                foreground=true; running=this
                connection=HandsFreeWatchConnection(this,::handleWatch).also { it.start() }
                handler.post(permissionWatchdog)
                // A session is bounded to eight hours; process death/reboot always leaves it off.
                handler.postDelayed({ end() },8*60*60*1000L)
            } catch(e: Exception) { stopSelf(); return START_NOT_STICKY }
        }
        if(action==START && connection==null) connection=HandsFreeWatchConnection(this,::handleWatch).also { it.start() }
        when(action) {
            START, ON -> control(HandsFreeControl.ON)
            OFF -> control(HandsFreeControl.OFF)
            PAUSE -> control(HandsFreeControl.PAUSE)
            END -> end()
            RESUME -> {
                if(session.resume(SystemClock.elapsedRealtime(),intent.getLongExtra("generation",-1))) {
                    update("Listening for Hey Sig");refreshShake();resumeWhenQuiet()
                }
            }
        }
        return START_NOT_STICKY
    }
    fun refreshShake() {
        shake.stop()
        if(isListening && getSharedPreferences("heysig-handsfree",MODE_PRIVATE).getBoolean("shake",false)) shake.start()
    }
    fun control(control: HandsFreeControl) {
        if(control != HandsFreeControl.STATUS) { runtime.personalPhrases.cancel();runtime.sleepRoutine.cancel();runtime.fittingWorkflow.cancel() }
        currentUtterance=null;synthesis?.stop();speaking=false
        if(foreground && SystemClock.elapsedRealtime()>=sessionEndsAt) { end();return }
        when(control) {
            HandsFreeControl.ON -> { pending=null;restoreSnapshot=null;releaseIdleCommandLock();cancelResumeAlarm();stopNoise?.invoke(); stopInputs();session.on();update("Listening for Hey Sig");resumeWhenQuiet() }
            HandsFreeControl.OFF -> { releaseIdleCommandLock();cancelResumeAlarm();session.off();pending=null;stopInputs();currentUtterance=null;synthesis?.stop();speaking=false;update("Hands-free Off · microphone released") }
            HandsFreeControl.PAUSE -> {
                releaseIdleCommandLock();stopInputs();pending=null;session.pause(SystemClock.elapsedRealtime())
                val token=session.generation
                val alarm=getSystemService(AlarmManager::class.java)
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,session.resumeAt,resumeIntent(token))
                update("Listening paused for 30 minutes")
            }
            HandsFreeControl.STATUS -> update(status)
            HandsFreeControl.END -> end()
        }
        refreshShake()
    }
    private fun cancelResumeAlarm() { getSystemService(AlarmManager::class.java).cancel(resumeIntent(0)) }
    private fun resumeIntent(generation: Long) = PendingIntent.getService(this,90,Intent(this,HandsFreeService::class.java).setAction(RESUME).putExtra("generation",generation),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun stopSpokenReply() { currentUtterance=null;synthesis?.stop();speaking=false;resumeWhenQuiet() }
    fun suspendMicrophone(invalidateConfirmation: Boolean = false) {
        if(invalidateConfirmation) { pending=null;restoreSnapshot=null }
        releaseCommandLock()
        if(session.mode==HandsFreeSession.Mode.COMMAND) { session.on();pending=null }
        stopInputs()
    }
    private fun stopInputs() {
        pendingWake=null
        handler.removeCallbacks(commandTimeout)
        recognizer?.cancel();recognizer?.destroy();recognizer=null
        capture?.let { captureClosing=true;it.close();capture=null }
        if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
    }
    fun resumeWhenQuiet() {
        if(foreground && SystemClock.elapsedRealtime()>=sessionEndsAt) { end();return }
        if(closed || !foreground || session.mode!=HandsFreeSession.Mode.WAKE || capture!=null || captureClosing) return
        if(runtime.busy.get() || manualSpeechBusy || externalSpeechBusy?.invoke()==true || speaking || synthesis?.isSpeaking==true || (getSystemService(AUDIO_SERVICE) as AudioManager).mode!=AudioManager.MODE_NORMAL) {
            handler.postDelayed({ resumeWhenQuiet() },500);return
        }
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { control(HandsFreeControl.OFF);update("Microphone permission unavailable · listening Off");return }
        val token=session.generation
        wakeLock=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:Listening").apply { acquire(8*60*60*1000L) }
        val worker=WakeCapture(this) { detected,error -> handler.post {
            capture=null;captureClosing=false
            if(wakeLock?.isHeld==true) wakeLock?.release();wakeLock=null
            if(closed || token!=session.generation) { resumeWhenQuiet();return@post }
            if(error!=null) { control(HandsFreeControl.OFF);update(error);return@post }
            if(detected && session.wake(token)) wakeCommand(session.generation) else resumeWhenQuiet()
        } }
        capture=worker;worker.start();releaseCommandLock()
    }
    private fun awaitGestureCapture(token: Long,attempt: Int) {
        if(!session.accepts(token)) return
        if(!captureClosing) wakeCommand(token)
        else if(attempt<40) handler.postDelayed({ awaitGestureCapture(token,attempt+1) },50)
        else { control(HandsFreeControl.OFF);update("Microphone did not release; listening Off") }
    }
    private fun releaseIdleCommandLock() { if(!runtime.backgroundExecution || !runtime.busy.get()) releaseCommandLock() }
    private fun releaseCommandLock() { if(commandWakeLock?.isHeld==true) commandWakeLock?.release();commandWakeLock=null }
    private fun holdCommandLock() {
        releaseCommandLock()
        commandWakeLock=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"HeySig:Command").apply { acquire(30_000) }
    }
    private fun wakeCommand(token: Long) {
        holdCommandLock()
        ShakeTrigger.acknowledge(this)
        if(speechReady && prefs.getBoolean("voicePrompts",BuildConfig.DEBUG)) {
            pendingWake=token;currentUtterance="handsfree-wake-$token";speaking=true;update("Ready for your command")
            if(synthesis?.speak("Ready",TextToSpeech.QUEUE_FLUSH,null,"handsfree-wake-$token")!=TextToSpeech.SUCCESS) {
                pendingWake=null;speaking=false;recognize(token);return
            }
            handler.postDelayed({
                if(pendingWake==token && session.accepts(token)) { pendingWake=null;currentUtterance=null;synthesis?.stop();speaking=false;recognize(token) }
            },3000)
        } else recognize(token)
    }
    private fun recognize(token: Long) {
        if(closed || !session.accepts(token)) return
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { control(HandsFreeControl.OFF);return }
        update("Listening for your command")
        try {
            // Prefer explicit on-device recognition; the disclosure covers provider fallback.
            val recognizer=if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)
            this.recognizer=recognizer
            recognizer.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partial: Bundle?) {}
                override fun onEvent(type: Int,params: Bundle?) {}
                override fun onError(error: Int) { if(session.accepts(token)) completeCommand("Command recognition unavailable or no speech heard") }
                override fun onResults(results: Bundle?) {
                    if(!session.accepts(token)) return
                    val choices=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().take(5)
                    handler.removeCallbacks(commandTimeout);this@HandsFreeService.recognizer?.destroy();this@HandsFreeService.recognizer=null
                    if(choices.isEmpty()) { completeCommand("No command heard");return }
                    val fit=runtime.fitting
                    val identities=choices.map { identity(it,fit) }.filterNot { it is AppIntent.Unsupported }.distinct()
                    if(identities.size>1) { completeCommand("Speech alternatives disagree; nothing sent");return }
                    val text=choices.firstOrNull { identity(it,fit)==identities.firstOrNull() } ?: choices[0]
                    handle(text,"phone") { result ->
                        if(session.accepts(token)) completeCommand(result) else { update(result);resumeWhenQuiet() }
                    }
                }
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)
            })
            handler.postDelayed(commandTimeout,10_000)
        } catch(e: Exception) { completeCommand("Speech service unavailable · use phone or watch controls") }
    }
    private fun completeCommand(text: String) {
        handler.removeCallbacks(commandTimeout);recognizer?.destroy();recognizer=null
        val token=session.generation
        if(session.complete(token)) {
            update(text)
            val prompt=text.startsWith("Noise calibration:") || runtime.sleepRoutine.isDraftFor("phone") || pending!=null || (runtime.personalPhrases.awaitingConfirmation || runtime.fittingWorkflow.pending)
            if(speechReady && prefs.getBoolean(if(prompt) "voicePrompts" else "spokenResponses",if(prompt) BuildConfig.DEBUG else prefs.getBoolean("spokenResults",BuildConfig.DEBUG))) {
                speaking=true;currentUtterance="handsfree-result-${session.generation}"
                if(synthesis?.speak(text,TextToSpeech.QUEUE_FLUSH,null,currentUtterance)!=TextToSpeech.SUCCESS) speaking=false
                val generation=session.generation
                handler.postDelayed({ if(generation==session.generation && speaking) stopSpokenReply() },15_000)
            }
            handler.postDelayed({ resumeWhenQuiet() },1000)
        }
    }
    private fun identity(text: String,fit: Fitting?): Any = EnvironmentVoice.parse(text) ?: NoiseVoice.parse(text) ?: DataVoice.parse(text) ?: if(FittingWorkflow.accepts(text)) "setup:${FittingWorkflow.normalize(text)}" else runtime.personalPhrases.identity(text,fit)
    private fun handleWatch(text: String,origin: String,reply: (String)->Unit) {
        if(session.mode==HandsFreeSession.Mode.COMMAND) { session.on();stopInputs() }
        handle(text,origin) { result -> reply(result.replace("Listening for Hey Sig","Phone listening On"));update(result);resumeWhenQuiet() }
    }
    private fun handle(text: String,origin: String,reply: (String)->Unit) {
        if(closed || !foreground || SystemClock.elapsedRealtime()>=sessionEndsAt) { reply("Hands-free session expired; start it on the phone again");end();return }
        HandsFreeControl.parse(text)?.let { if(it==HandsFreeControl.END) { reply("Hands-free session ending");handler.post { end() } } else { control(it);reply(status) };return }
        if(text.lowercase().trim() in listOf("stop","cancel")) { if(text.lowercase().trim()=="stop") runCatching { AutomaticSleepScheduler(this).disable() };stopNoise?.invoke();runtime.cancelled.set(true);pending=null;runtime.personalPhrases.cancel();runtime.sleepRoutine.cancel();runtime.fittingWorkflow.cancel();reply("Stop requested");return }
        VoicePreference.parse(text)?.let {
            val ok=prefs.edit().putBoolean(it.key,it.enabled).commit()
            if(!it.enabled) { currentUtterance=null;synthesis?.stop();speaking=false }
            reply(if(ok) it.description else "Voice setting could not be saved");return
        }
        EnvironmentVoice.parse(text)?.let { command ->
            if(foregroundEnvironment?.invoke(command,reply)!=true) reply("Open and unlock HeySig on the phone for automatic program settings or monitoring.")
            return
        }
        NoiseVoice.parse(text)?.let { command ->
            if(foregroundNoise?.invoke(command,reply)!=true) reply("Open and unlock HeySig on the phone for noise setup, calibration or sampling. No microphone capture or aid command started.")
            return
        }
        DataVoice.parse(text)?.let { reply("Open HeySig on the phone and say ${text.trim()}, or use Aid setup / Voice and speech to choose a document. Nothing sent.");return }
        if(text=="status") { reply("$status. ${runtime.lastResult}");return }
        if(FittingWorkflow.accepts(text) && !runtime.fittingWorkflow.pending &&
            (runtime.personalPhrases.awaitingConfirmation || runtime.sleepRoutine.hasDraft)) {
            reply("Finish or cancel the current phrase or sleep review before starting aid setup.");return
        }
        if(!runtime.busy.get() && pending == null && (runtime.fittingWorkflow.pending || FittingWorkflow.accepts(text))) {
            runtime.fittingWorkflow.handle(text,runtime.fitting,origin,SystemClock.elapsedRealtime())?.let {
                if(it.startsWith("Aid setup saved")) runCatching { SleepReminders(this).refresh() }
                reply(it);return
            }
        }
        val fit=runtime.fitting ?: run { reply("Save aid setup first");return }
        if(runtime.busy.get()) { reply("Busy; wait or Stop");return }
        pending?.let { (actor,intent) ->
            if(actor!=origin) { reply("Finish or cancel the confirmation on the device that started it");return }
            pending=null
            val snapshot=restoreSnapshot;restoreSnapshot=null
            if(SystemClock.elapsedRealtime()>pendingUntil) { reply("Confirmation expired; nothing sent");return }
            val yes=text.lowercase().trim().trimEnd('.','!','?') in listOf("yes","confirm","confirm program","confirm restore","restore known settings","confirm refresh","refresh current settings")
            if(!yes) { reply("Cancelled; nothing sent");return }
            when(intent) {
                is AppIntent.ClarifyProgram -> play(listOf(ManualRequest.SelectProgram(intent.slot)),false,reply)
                AppIntent.RestoreSettings -> restore(snapshot,reply)
                AppIntent.Refresh -> {
                    val plan=try { runtime.restorePlan(requireNotNull(snapshot)) }
                        catch(e: Exception) { reply(e.message ?: "Refresh unavailable");return }
                    play(plan,true,{ reply("Refresh result. $it") })
                }
                else -> reply("Confirmation expired")
            };return
        }
        if(text.equals("send again",true)) {
            val request=repeatRequest ?: run { reply("No repeated target to resend");return }
            play(listOf(request),false,reply);return
        }
        com.heysig.sleep.SleepVoice.parse(text,fit)?.let { command ->
            if(command is com.heysig.sleep.SleepCommand.Now) {
                val slot=runCatching { runtime.sleepRoutine.target(command.sleep,fit) }.getOrElse { reply(it.message ?: "Review routine first");return }
                play(listOf(ManualRequest.SelectProgram(slot)),false,reply,command.sleep)
            } else if(command==com.heysig.sleep.SleepCommand.Enable) reply(AutomaticSleepScheduler(this).enable())
            else if(command==com.heysig.sleep.SleepCommand.Disable) { val result=AutomaticSleepScheduler(this).disable();SleepReminders(this).setEnabled(false);reply(result) }
            else {
                val result=runtime.sleepRoutine.handle(command,fit,origin)
                runCatching { SleepReminders(this).refresh() }
                if(command in listOf(com.heysig.sleep.SleepCommand.Resume,com.heysig.sleep.SleepCommand.Override) || result.startsWith("Routine saved.")) runCatching { AutomaticSleepScheduler(this).refresh() }
                reply(result)
            }
            return
        }
        runtime.personalPhrases.manage(text,fit,origin)?.let { reply(it);return }
        when(val intent=runtime.personalPhrases.resolve(text,fit)) {
            is AppIntent.RelativeLevel -> {
                val request=try { LevelSteps.request(intent.control,intent.delta,runtime.requested,fit) }
                    catch(e: Exception) { reply(e.message ?: "Choose an exact value first");return }
                repeatRequest=null
                play(listOf(request),false,reply)
            }
            is AppIntent.RelativeVolume -> {
                val request=try { VolumeSteps.request(intent.delta,runtime.requested,fit) }
                    catch(e: Exception) { reply(e.message ?: "Choose an exact volume first");return }
                repeatRequest=null
                play(listOf(request),false,reply)
            }
            is AppIntent.Control -> {
                val request=intent.request
                try { ManualValidation.validate(request,fit,runtime.requested) } catch(e: Exception) { reply(e.message ?: "Invalid request");return }
                if(request !is ManualRequest.Battery) {
                    try { runtime.sleepRoutine.transition(runtime.sleepRoutine.state.manual()) }
                    catch(e: Exception) { reply(e.message ?: "Could not pause routine");return }
                }
                val same=when(request) {
                    is ManualRequest.SelectProgram -> runtime.requested.program==request.slot
                    is ManualRequest.SetLevel -> runtime.requested.levels[request.control]==request.level
                    is ManualRequest.Battery -> false
                }
                if(same) {
                    repeatRequest=request;reply("Already last requested. Say send again after the wake phrase to repeat.")
                } else {
                    repeatRequest=null;play(listOf(request),false,reply)
                }
            }
            is AppIntent.ClarifyProgram -> { pending=origin to intent;pendingUntil=SystemClock.elapsedRealtime()+60000;reply("Did you mean program ${intent.slot}? Say yes or cancel after the wake phrase.") }
            AppIntent.Refresh -> {
                val current=runtime.requested.copy(levels=runtime.requested.levels.toMap())
                try { runtime.restorePlan(current) }
                catch(e: Exception) { reply(e.message ?: "Select a program before Refresh");return }
                restoreSnapshot=current;pending=origin to intent;pendingUntil=SystemClock.elapsedRealtime()+60000
                reply("Refresh current program ${current.program} and known levels? Unknown levels are skipped. Say confirm refresh or cancel after the wake phrase.")
            }
            AppIntent.RestoreSettings -> {
                val saved=runtime.saved
                if(saved?.program==null) reply(runtime.savedIssue ?: "No known saved settings")
                else { restoreSnapshot=saved;pending=origin to intent;pendingUntil=SystemClock.elapsedRealtime()+60000;reply("Restore saved program ${saved.program} and known levels? Say confirm restore or cancel after the wake phrase.") }
            }
            AppIntent.SaveSettings -> reply(runtime.saveSnapshot())
            AppIntent.ReadSavedSettings -> {
                val saved=runtime.saved
                reply(saved?.let { "Saved snapshot: program ${it.program}. " + LevelControl.entries.joinToString(". ") { key -> "${key.name.lowercase()} ${it.levels[key] ?: "unknown"}" } } ?: runtime.savedIssue ?: "No saved settings")
            }
            AppIntent.ReadProgram -> reply("Program: ${fit.programs.find { it.slot==runtime.requested.program }?.name ?: "unknown"}")
            is AppIntent.ReadLevel -> reply("${intent.control.name.lowercase().replaceFirstChar { it.uppercase() }}: ${runtime.requested.levels[intent.control] ?: "unknown"}")
            AppIntent.ListPrograms -> reply(fit.programs.joinToString(". ") { "${it.slot}: ${it.name}" })
            AppIntent.Cancel -> { runtime.sleepRoutine.cancel();pending=null;runtime.cancelled.set(true);reply("Cancelled") }
            is AppIntent.Unsupported -> reply(intent.explanation)
        }
    }
    private fun restore(snapshot: RequestedState?,reply: (String)->Unit) {
        val saved=snapshot ?: run { reply("No saved settings");return }
        val program=saved.program ?: run { reply("Saved program unknown");return }
        val plan=try { runtime.restorePlan(saved) } catch(e: Exception) { reply(e.message ?: "Saved settings cannot be restored");return }
        play(plan,true,reply)
    }
    private fun play(plan: List<ManualRequest>,restoring: Boolean,reply: (String)->Unit,sleepTarget: Boolean? = null) {
        stopNoise?.invoke();stopInputs();currentUtterance=null;synthesis?.stop();speaking=false
        holdCommandLock()
        runtime.execute(plan,restoring,false,{false},{foreground && !closed && running===this && SystemClock.elapsedRealtime()<sessionEndsAt},::update,sleepTarget=sleepTarget) { text,_ -> if(!isListening) releaseCommandLock();reply(text);resumeWhenQuiet() }
    }
    private fun update(text: String) { status=if(text.startsWith(listeningLabel)) text else "$listeningLabel · $text"; statusListener?.invoke(text); android.service.quicksettings.TileService.requestListeningState(this,ComponentName(this,HandsFreeTile::class.java)); if(foreground) {
            if(Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) getSystemService(NotificationManager::class.java).notify(ID,notification())
            else end()
        } }
    private fun notification(): Notification {
        fun action(label: String,action: String)=Notification.Action.Builder(null,label,PendingIntent.getService(this,action.hashCode(),Intent(this,HandsFreeService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        val open=PendingIntent.getActivity(this,0,Intent(this,HomeActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("HeySig hands-free")
            .setContentText(status).setStyle(Notification.BigTextStyle().bigText(status)).setContentIntent(open)
            .setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(action("Off",OFF)).addAction(action("Pause 30 min",PAUSE)).addAction(action("End session",END)).build()
    }
    private fun end() { if(closed) return;closed=true;runtime.personalPhrases.cancel();runtime.sleepRoutine.cancel();runtime.fittingWorkflow.cancel();if(runtime.backgroundExecution) runtime.cancelled.set(true);shake.stop();releaseCommandLock();manualSpeechBusy=false;cancelResumeAlarm();session.off();pending=null;stopInputs();connection?.close();connection=null;currentUtterance=null;synthesis?.stop();speaking=false;foreground=false;if(running===this) running=null;statusListener?.invoke("Hands-free session ended");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy() { end();handler.removeCallbacksAndMessages(null);synthesis?.shutdown();super.onDestroy() }
    companion object {
        const val START="com.heysig.handsfree.START"
        const val ON="com.heysig.handsfree.ON"
        const val OFF="com.heysig.handsfree.OFF"
        const val PAUSE="com.heysig.handsfree.PAUSE"
        const val END="com.heysig.handsfree.END"
        const val RESUME="com.heysig.handsfree.RESUME"
        private const val CHANNEL="handsfree-v1"
        private const val ID=73
        @Volatile var running: HandsFreeService?=null; private set
        var stopNoise: (() -> Unit)?=null
        // Activity-owned adapter only; cleared on pause. No background noise authority.
        var foregroundEnvironment: ((EnvironmentCommand,(String)->Unit)->Boolean)?=null
        var foregroundNoise: ((NoiseCommand,(String)->Unit)->Boolean)?=null
        var externalSpeechBusy: (() -> Boolean)?=null
        var statusListener: ((String) -> Unit)?=null
        var manualSpeechBusy=false
        internal var uiStartAuthorized=false
        fun startFromVisibleActivity(activity: Activity) {
            uiStartAuthorized=true
            try { activity.startForegroundService(Intent(activity,HandsFreeService::class.java).setAction(START)) }
            catch(e: Exception) { uiStartAuthorized=false;throw e }
        }
    }
}
