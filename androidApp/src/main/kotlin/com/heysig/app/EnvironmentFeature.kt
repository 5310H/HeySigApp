package com.heysig.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import com.heysig.audio.EnvironmentAudio
import com.heysig.control.Program
import com.heysig.noise.*
import org.json.JSONObject

/** New semantic path. Legacy level settings/calibration are never migrated or overwritten. */
class EnvironmentFeature(
    private val activity: Activity,
    private val programs: ()->List<Program>,
    private val current: ()->Int?,
    private val safety: ()->NoiseSafety,
    private val prepare: ()->Unit,
    private val automaticSend: (Int,(Boolean)->Unit)->Unit,
    private val manualSend: (Int)->Unit,
    private val report: (String)->Unit
) {
    private val prefs=activity.getSharedPreferences("heysig-noise-v1",Activity.MODE_PRIVATE)
    var settings=EnvironmentSettings(); private set
    private var state=EnvironmentState()
    private var classification: EnvironmentClassification?=null
    private var simulation=false
    private var beforeSimulation: EnvironmentState?=null
    private var simulatedCurrent: Int?=null
    private var sampling=false
    private var session=0
    private var nextSample=0L
    private var lastPlaybackFinishedAt: Long?=null
    private var watchLink: WatchEnvironmentSession?=null
    private var source="phone"
    private var statusView: TextView?=null
    private val handler=Handler(Looper.getMainLooper())
    private val audio=EnvironmentAudio(activity,{ captureAllowed() },{ cancelled -> MicrophoneLease.acquire(cancelled) },{ MicrophoneLease.release() })
    init {
        try { prefs.getString("environmentSettingsV1",null)?.let { settings=EnvironmentSettingsCodec.decode(it) } }
        catch(e: Exception) { state=state.copy(phase=SwitchingPhase.ERROR,reason="Saved environment settings need review") }
    }
    private fun now()=SystemClock.elapsedRealtime()
    private fun available()=programs().map { EnvironmentProgram(it.slot,it.name) }.toSet()
    private fun sleepSlots()=programs().filter { it.name.equals("sleep",true) }.map { it.slot }.toSet() +
        listOfNotNull(runCatching { JSONObject(prefs.getString("settings","{}")!!).optInt("sleepProgram",0).takeIf { it in 1..6 } }.getOrNull())
    private fun captureAllowed()=state.monitoring && source=="phone" && safety().let { it.foreground && it.unlocked && it.audioFree }
    fun dispatchAllowed()=state.monitoring && !simulation && EnvironmentReleaseGate.qualified()
    fun detach() { statusView=null }
    fun stop(reason: String="Monitoring off") {
        session++;audio.stop();sampling=false;watchLink=null
        beforeSimulation?.let { state=it };beforeSimulation=null;simulation=false
        state=EnvironmentSwitching.stop(state).copy(reason=reason)
        handler.removeCallbacksAndMessages(null);update()
    }
    fun fittingChanged() { stop("Program fitting changed; review assignments") }
    fun connectionLost() { if(source=="watch") stop("Watch connection closed; restart monitoring explicitly") }
    fun manualCompleted(success: Boolean) {
        if(success) state=EnvironmentSwitching.manual(state,now(),settings)
        update()
    }
    fun playbackFinished() { lastPlaybackFinishedAt=now() }
    fun manualPriority() { stop("Manual command has priority; restart monitoring explicitly") }
    private fun save(value: EnvironmentSettings) {
        check(prefs.edit().putString("environmentSettingsV1",EnvironmentSettingsCodec.encode(value)).commit()) { "Could not save environment settings" }
        stop("Settings saved; monitoring off");settings=value;update()
    }
    fun status(): String {
        val program=programs().find { it.slot==current() }?.name ?: "Unknown"
        val result=classification
        return "${if(simulation) "SIMULATION · no commands sent\n" else ""}Automatic ${if(settings.enabled) "on" else "off"} · ${state.phase}\n"+
            "Monitoring ${if(state.monitoring) "on · $source" else "off"}${if(sampling) " · microphone active" else ""}\n"+
            "Detected: ${result?.environment?.label ?: "Uncertain / not sampled"} · score ${((result?.confidence ?: 0.0)*100).toInt()}%\n"+
            "Last requested program: $program (aid reception unconfirmed)\n"+
            (if(simulation) "Simulation baseline: ${programs().find { it.slot==simulatedCurrent }?.name ?: "Unknown"}\n" else "")+
            "Cooldown: ${state.remainingSeconds(now())} seconds remaining\n${state.reason}"+
            if(!EnvironmentReleaseGate.qualified() && settings.enabled && !simulation) "\nReal automatic dispatch awaits classifier and physical acceptance. Monitoring is available." else ""
    }
    private fun update() { statusView?.text=status() }
    private fun start(preview: Boolean=false, watch: Boolean=false) {
        check(!state.monitoring) { "Stop monitoring before changing source" }
        check(safety().let { it.foreground && it.unlocked }) { "Keep the phone open and unlocked" }
        if(!watch && !prefs.getBoolean("environmentDisclosureV1",false)) {
            AlertDialog.Builder(activity).setTitle("Microphone for listening environments")
                .setMessage("HeySig samples your phone microphone in short foreground windows to estimate speech and environmental sounds with an experimental offline model. PCM stays in memory and is discarded; this feature stores no recording or transcript and uploads no audio. Group/location estimates can be uncertain. Monitoring stops when you leave or lock HeySig. Calibration is optional. Speech recognition is separate and its provider may use a network.")
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("environmentDisclosureV1",true).commit()) runCatching { start(preview) }.onFailure { report(it.message ?: "Could not start") }
                }.show()
            return
        }
        if(!watch && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
            report("Allow microphone access, then tap Start monitoring again");return
        }
        prepare()
        if(preview) { beforeSimulation=state;state=EnvironmentState() }
        simulation=preview;source=if(watch) "watch" else "phone";simulatedCurrent=current() ?: if(preview) available().minByOrNull { it.slot }?.slot else null
        classification=null;state=EnvironmentSwitching.start(state,now());nextSample=now();session++
        handler.removeCallbacks(tick);handler.post(tick);update()
    }
    private val tick=object: Runnable {
        override fun run() {
            if(!state.monitoring) return
            if(!safety().let { it.foreground && it.unlocked }) { stop("Foreground monitoring ended");return }
            if(settings.schedule.expired(state.startedAt,now())) { stop("Monitoring session expired");return }
            if(source=="watch" && watchLink?.expired(now(),settings.schedule)==true) {
                stop("Watch samples stopped; reconnect and restart explicitly");return
            }
            if(source=="phone" && !sampling && now()>=nextSample && captureAllowed()) {
                val generation=session
                sampling=true;nextSample=now()+settings.schedule.intervalSeconds*1000L
                val started=audio.sample(settings.schedule.durationSeconds) { result,error,capturedAt -> activity.runOnUiThread {
                    if(generation!=session) return@runOnUiThread
                    sampling=false
                    if(result==null) stop(error ?: "Audio classification failed")
                    else if(now()<capturedAt || now()-capturedAt>5000) stop("Classification expired; restart monitoring")
                    else accept(result,capturedAt)
                } }
                if(!started) { sampling=false;nextSample=now()+250 }
            }
            update();handler.postDelayed(this,1000)
        }
    }
    private fun accept(result: EnvironmentClassification, capturedAt: Long=now(), uncertaintyMillis: Long=0) {
        classification=result
        if(!simulation && !environmentSampleAfterPlayback(capturedAt,settings.schedule.durationSeconds,uncertaintyMillis,lastPlaybackFinishedAt)) {
            state=state.copy(candidate=null,lastSampleAt=null,reason="Discarded sample near command playback; waiting for fresh audio")
            update();return
        }
        val guard=safety()
        val decision=EnvironmentSwitching.sample(state,result,now(),settings,available(),
            if(simulation) simulatedCurrent else current(),guard.foreground && guard.unlocked && guard.audioFree && !guard.manualOverride && !guard.sleeping,sleepSlots())
        state=decision.state
        if(!state.monitoring) { stop(state.reason);return }
        decision.target?.let { target ->
            val token=state.token
            if(simulation) {
                simulatedCurrent=target.slot
                state=EnvironmentSwitching.complete(state,token,true,now(),settings).copy(reason="Would request ${target.name}; no command sent")
            } else if(!EnvironmentReleaseGate.qualified()) {
                state=state.copy(pending=null,phase=SwitchingPhase.MONITORING,reason="Stable ${result.environment?.label}; classifier and physical acceptance required before automatic dispatch")
            } else {
                val generation=session
                automaticSend(target.slot) { success -> activity.runOnUiThread {
                    // Even a stopped session must retain completion cooldown; never replay.
                    state=EnvironmentSwitching.complete(state,token,success,now(),settings)
                    if(generation==session && !success) stop("Playback failed; restart explicitly")
                    update()
                } }
            }
        }
        update()
    }
    private fun simulate(environment: ListeningEnvironment) {
        stop();prepare()
        check(settings.enabled) { "Save automatic switching On to simulate decisions" }
        check(safety().let { it.foreground && it.unlocked }) { "Keep HeySig open and unlocked" }
        beforeSimulation=state;state=EnvironmentState()
        simulation=true;source="simulation";simulatedCurrent=current() ?: available().minByOrNull { it.slot }?.slot
        state=EnvironmentSwitching.start(state,now())
        val generation=++session
        val result=EnvironmentClassification(environment,0.99,"Explicit simulated environment, not microphone classification")
        accept(result)
        handler.post(tick)
        // Feed stable evidence at the configured sampling cadence without microphone access.
        val feed=object: Runnable {
            override fun run() {
                if(generation!=session || !state.monitoring) return
                accept(result)
                handler.postDelayed(this,settings.schedule.intervalSeconds*1000L)
            }
        }
        handler.postDelayed(feed,settings.schedule.intervalSeconds*1000L)
    }
    /** Authenticated selected-node RPC only. Phone time and session/sequence prevent stale replay. */
    fun watch(request: JSONObject): JSONObject {
        when(request.getString("action")) {
            "start" -> {
                stop("New watch monitoring session")
                val id=request.getString("session");com.heysig.companion.CompanionSafety.id(id)
                start(watch=true);watchLink=WatchEnvironmentSession(id,now())
            }
            "stop" -> stop("Watch monitoring stopped")
            "sample" -> {
                check(state.monitoring && source=="watch") { "Watch session expired; start again" }
                watchLink=checkNotNull(watchLink).receive(request.getString("session"),request.getLong("sequence"),request.getLong("ageMillis"),now())
                val id=request.optInt("environment",0)
                val environment=if(id==0) null else ListeningEnvironment.entries.firstOrNull { it.id==id } ?: error("Invalid environment")
                accept(EnvironmentClassification(environment,request.getDouble("confidence"),"Watch experimental model result"),
                    now()-request.getLong("ageMillis"),5000)
            }
            "status" -> Unit
            else -> error("Unsupported monitoring action")
        }
        return JSONObject().put("phase","FINAL").put("text",status())
            .put("interval",settings.schedule.intervalSeconds).put("duration",settings.schedule.durationSeconds)
            .put("timeout",settings.schedule.timeoutMinutes).put("monitoring",state.monitoring)
            .put("programs",org.json.JSONArray(programs().map { JSONObject().put("slot",it.slot).put("name",it.name) }))
    }
    fun handle(command: EnvironmentCommand) {
        runCatching {
            when(command) {
                is EnvironmentCommand.Automatic -> save(settings.copy(enabled=command.enabled))
                is EnvironmentCommand.Cooldown -> save(settings.copy(cooldownSeconds=command.seconds))
                is EnvironmentCommand.Monitor -> if(command.enabled) start() else stop()
                is EnvironmentCommand.Select -> {
                    val target=settings.assignments[command.environment.ordinal] ?: error("${command.environment.label} is unassigned")
                    check(target in available()) { "Assigned program changed; review assignment" }
                    manualPriority();manualSend(target.slot)
                }
                EnvironmentCommand.Status -> Unit
            }
            report(status())
        }.onFailure { report(it.message ?: "Environment command failed") }
    }
    fun render(layout: LinearLayout, watchStatus: String) {
        val card=PhoneStyle.card(activity,layout)
        PhoneStyle.heading(activity,card,"Automatic program switching")
        fun line(text: String)=TextView(activity).apply { this.text=text;PhoneStyle.text(this,16f);card.addView(this) }
        fun button(text: String, action: ()->Unit) { card.addView(Button(activity).apply {
            this.text=text;PhoneStyle.action(this);setOnClickListener { runCatching(action).onFailure { report(it.message ?: "Review settings") } }
        }) }
        statusView=line(status()).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        line("Watch connection: $watchStatus")
        line("Choose existing programs for each environment. Reuse a program or leave an environment unassigned. Calibration is optional. Estimates are experimental and uncertain results keep the current program.")
        val enabled=Switch(activity).apply { text="Enable automatic switching";isChecked=settings.enabled;card.addView(this) }
        val choices=listOf<EnvironmentProgram?>(null)+available().sortedBy { it.slot }+
            settings.assignments.filterNotNull().filter { it !in available() }.distinct()
        val pickers=ListeningEnvironment.entries.map { environment ->
            line(environment.label)
            Spinner(activity).apply {
                contentDescription="Program for ${environment.label}"
                adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices.map {
                    if(it==null) "Unassigned" else "${it.name} (program ${it.slot})${if(it !in available()) " · unavailable / renamed" else ""}"
                });setSelection(choices.indexOf(settings.assignments[environment.ordinal]).coerceAtLeast(0));card.addView(this)
            }
        }
        fun field(label: String,value: Int)=EditText(activity).apply {
            line(label);contentDescription=label;setText(value.toString());setSingleLine()
            inputType=android.text.InputType.TYPE_CLASS_NUMBER;card.addView(this)
        }
        val cooldown=field("Cooldown seconds (0–600)",settings.cooldownSeconds)
        val stability=field("Stable environment seconds (0–120)",settings.stabilitySeconds)
        val override=field("Manual override seconds (0–600)",settings.overrideSeconds)
        val restart=Switch(activity).apply { text="Restart cooldown after manual change";isChecked=settings.restartAfterManual;card.addView(this) }
        fun picker(label: String,values: List<Int>,selected: Int)=Spinner(activity).apply {
            line(label);contentDescription=label;adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,values.map { if(it==0) "Until stopped" else it.toString() })
            setSelection(values.indexOf(selected));card.addView(this)
        }
        val interval=picker("Sampling interval seconds",listOf(15,30,60),settings.schedule.intervalSeconds)
        val duration=picker("Sampling duration seconds",listOf(3,5,10),settings.schedule.durationSeconds)
        val timeout=picker("Session timeout minutes",listOf(15,30,60,0),settings.schedule.timeoutMinutes)
        button("Save automatic settings") {
            save(settings.copy(assignments=pickers.map { choices[it.selectedItemPosition] },enabled=enabled.isChecked,
                cooldownSeconds=cooldown.text.toString().toInt(),stabilitySeconds=stability.text.toString().toInt(),
                overrideSeconds=override.text.toString().toInt(),restartAfterManual=restart.isChecked,
                schedule=SamplingSchedule(listOf(15,30,60)[interval.selectedItemPosition],listOf(3,5,10)[duration.selectedItemPosition],listOf(15,30,60,0)[timeout.selectedItemPosition])))
            report("Automatic settings saved. Monitoring off.")
        }
        button("Start monitoring") { handle(EnvironmentCommand.Monitor(true)) }
        button("Stop monitoring") { stop();report(status()) }
        button("Start simulation · no aid commands") { check(settings.enabled) { "Save automatic switching On to simulate decisions" };start(preview=true) }
        button("Simulate an environment · no microphone") {
            AlertDialog.Builder(activity).setTitle("Simulated environment")
                .setItems(ListeningEnvironment.entries.map { it.label }.toTypedArray()) { _,i ->
                    runCatching { simulate(ListeningEnvironment.entries[i]) }.onFailure { report(it.message ?: "Simulation unavailable") }
                }.show()
        }
        line("Monitoring stops when HeySig leaves the foreground. Watch settings apply to the next watch session. Use the watch's Monitoring control to sample its microphone. Real automatic dispatch remains gated pending physical acceptance. Optional sound-level calibration is available in Noise.")
    }
}
