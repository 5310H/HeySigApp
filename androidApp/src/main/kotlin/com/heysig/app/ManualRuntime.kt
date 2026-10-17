package com.heysig.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.heysig.control.*
import com.heysig.research.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** One production executor for UI, watch and hands-free; no durable queue or automatic replay. */
class ManualRuntime private constructor(
    private val prefs: android.content.SharedPreferences,
    private val player: CommandTransport
) {
    private val main=Handler(Looper.getMainLooper())
    val busy=AtomicBoolean(false)
    val cancelled=AtomicBoolean(false)
    @Volatile var backgroundExecution=false; private set
    @Volatile var fitting: Fitting?=null
    @Volatile var requested=RequestedState()
    @Volatile var saved: RequestedState?=null
    var revision=0L; private set
    var stateChanged: (() -> Unit)?=null
    var lastResult="Ready. Nothing is sent until you ask."
        private set
    private val profile=AcousticProfile(44100,15360,512,0.0625,1.2,true)
    init { load() }
    private fun load() {
        try {
            if(prefs.contains("fitting")) {
                val json = JSONObject(prefs.getString("fitting", "{}")!!)
                val programs = (1..6).mapNotNull { slot ->
                    val name = json.optString("name$slot").trim()
                    if(name.isEmpty()) null else Program(slot,name,TinnitusCapability.valueOf(json.getString("tin$slot")))
                }
                fitting = Fitting(json.getInt("control"),json.getInt("left"),json.getInt("right"),programs)
            }
            requested = decode(prefs.getString("requested",null)) ?: RequestedState()
            saved = decode(prefs.getString("saved",null))
            if(prefs.getBoolean("pending", false)) {
                requested = RequestedState()
                lastResult = "Previous command was interrupted. Aid state and requested values are uncertain. No replay."
            }
        } catch(e: Exception) {
            fitting = null; requested = RequestedState(); saved = null
            lastResult = "Stored setup needs review: ${e.message}. No commands enabled."
        }
    }
    fun encode(state: RequestedState): String = JSONObject().apply {
        state.program?.let { put("program",it) }
        state.levels.forEach { (key,value) -> put(key.name,value) }
    }.toString()
    private fun decode(text: String?): RequestedState? {
        if(text == null) return null
        val json = JSONObject(text)
        val program = if(json.has("program")) json.getInt("program") else return RequestedState()
        return RequestedState(program,LevelControl.entries.mapNotNull { key ->
            if(json.has(key.name)) key to json.getInt(key.name) else null
        }.toMap())
    }

    fun execute(plan: List<ManualRequest>, restoring: Boolean, automatic: Boolean,
        foreground: () -> Boolean, backgroundAllowed: () -> Boolean,
        progress: (String) -> Unit, done: (String, Boolean) -> Unit) {
        fun reject(text: String) { done(text,false) }
        val fit = fitting ?: run { reject("Save setup first"); return }
        if(!foreground() && !backgroundAllowed()) { reject("Open HeySig before sending a command"); return }
        if(busy.get()) { reject("Busy; wait or Stop"); return }
        try {
            var simulated = requested
            plan.forEach { ManualValidation.validate(it,fit,simulated); simulated = simulated.completed(it) }
            player.inspect(backgroundAllowed())
            check(prefs.edit().putBoolean("pending",true).commit()) { "Cannot journal command; no playback" }
        } catch(e:Exception) { reject("Blocked: ${e.message}"); return }
        if(!busy.compareAndSet(false,true)) { reject("Busy; wait or Stop"); return }
        backgroundExecution=backgroundAllowed(); cancelled.set(false); lastResult = "Transmitting…"; revision++;stateChanged?.invoke();progress(lastResult)
        Thread({
            val completed = mutableListOf<String>()
            var result: String
            var durable = true
            var playbackSucceeded = false
            try {
                plan.forEachIndexed { index, request ->
                    check(!cancelled.get()) { "Stopped" }
                    val stages: List<Pair<String,ByteArray>> = when(request) {
                        is ManualRequest.SelectProgram -> listOf("program ${request.slot}" to ProtoRequest.programPcm(request.slot,fit.controlAddress,profile))
                        is ManualRequest.SetLevel -> listOf("${request.control.name.lowercase()} ${request.level}" to when(request.control) {
                            LevelControl.VOLUME -> ProtoRequest.volumePcm(request.level,fit.controlAddress,profile)
                            LevelControl.SHARPNESS -> ProtoRequest.balancePcm(request.level,fit.controlAddress,profile)
                            LevelControl.TINNITUS -> ProtoRequest.tinnitusPcm(request.level,fit.controlAddress,profile)
                        })
                        is ManualRequest.Battery -> buildList {
                            if(request.left) add("left battery" to ProtoRequest.pcm(BatterySide.LEFT,fit.leftAddress,profile))
                            if(request.right) add("right battery" to ProtoRequest.pcm(BatterySide.RIGHT,fit.rightAddress,profile))
                        }
                    }
                    // A partially emitted command must never leave a confident old baseline.
                    requested = when(request) {
                        is ManualRequest.SelectProgram -> RequestedState()
                        is ManualRequest.SetLevel -> requested.copy(levels=requested.levels - request.control)
                        is ManualRequest.Battery -> requested
                    }
                    for((stageIndex,stage) in stages.withIndex()) {
                        check(!cancelled.get()) { "Stopped" }
                        main.post { progress("Transmitting ${stage.first}…") }
                        player.play(stage.second,profile.sampleRate,cancelled,backgroundAllowed())
                        completed.add(stage.first)
                        if(stageIndex < stages.lastIndex) gap()
                    }
                    requested = requested.completed(request)
                    durable = prefs.edit().putString("requested",encode(requested)).commit() && durable
                    if(index < plan.lastIndex) gap()
                }
                if(!restoring && !automatic && requested.program != null && plan.any { it !is ManualRequest.Battery }) {
                    if(prefs.edit().putString("saved",encode(requested)).commit()) saved = requested else durable = false
                }
                playbackSucceeded = !cancelled.get()
                result = "Played: ${completed.joinToString()}."
            } catch(e:Exception) { result = "${if(completed.isEmpty()) "No completed stages" else "Completed: ${completed.joinToString()}"}. ${e.message}. Current aid state may be uncertain." }
            finally {
                durable = prefs.edit().putString("requested",encode(requested)).putBoolean("pending",false).commit() && durable
                busy.set(false); backgroundExecution=false
            }
            if(!durable) result += " Storage failed; remembered state may not survive restart."
            main.post { lastResult=result;revision++;stateChanged?.invoke(); done(result,playbackSucceeded && durable && !cancelled.get()) }
        },"heysig-manual").start()
    }
    private fun gap() { repeat(100) { check(!cancelled.get()) { "Stopped" }; Thread.sleep(10) } }
    companion object {
        internal fun isolated(prefs: android.content.SharedPreferences, transport: CommandTransport) = ManualRuntime(prefs,transport)
        @Volatile private var instance: ManualRuntime?=null
        fun get(context: Context): ManualRuntime = instance ?: synchronized(this) {
            instance ?: ManualRuntime(context.applicationContext.getSharedPreferences("heysig-core-v1",Context.MODE_PRIVATE),SpeakerCommandTransport(context.applicationContext)).also { instance=it }
        }
    }
}
