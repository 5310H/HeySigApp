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
    lateinit var sleepRoutine: SleepRoutineStore; private set
    lateinit var personalPhrases: PersonalPhraseStore; private set
    val fittingWorkflow = FittingWorkflow { saveFitting(it) }
    val busy=AtomicBoolean(false)
    val cancelled=AtomicBoolean(false)
    @Volatile var backgroundExecution=false; private set
    @Volatile var fitting: Fitting?=null
        set(value) { field=value; if(value != null && this::sleepRoutine.isInitialized) sleepRoutine.setupChanged(value); if(value != null && this::personalPhrases.isInitialized) personalPhrases.setupChanged(value) }
    @Volatile var requested=RequestedState()
    @Volatile var saved: RequestedState?=null
    var savedIssue: String?=null; private set
    var revision=0L; private set
    var stateChanged: (() -> Unit)?=null
    private var sleepStateChanged: (() -> Unit)?=null
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
            try {
                saved = decode(prefs.getString("saved",null))
                saved?.let { SavedSettings.plan(it,requireNotNull(fitting) { "Save aid setup first" }) }
            } catch(e: Exception) {
                saved=null
                savedIssue="Saved snapshot needs review: ${e.message}. Stored record preserved; no restore enabled."
            }
            if(!prefs.getBoolean("pending",false) && requested.program != null && requested.levels.isNotEmpty()) {
                val key="program-settings-${requested.program}"
                if(!prefs.contains(key)) {
                    SavedSettings.plan(requested,requireNotNull(fitting))
                    check(prefs.edit().putString(key,encode(requested)).commit()) { "Could not retain program settings" }
                }
            }
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

    /** Commit setup and archive prior fitting/snapshot bytes atomically; never plays audio. */
    fun saveFitting(next: Fitting): Boolean {
        if(busy.get()) return false
        val changed=next != fitting
        val json=JSONObject().put("control",next.controlAddress).put("left",next.leftAddress).put("right",next.rightAddress)
        next.programs.forEach { json.put("name${it.slot}",it.name);json.put("tin${it.slot}",it.tinnitus.name) }
        val edit=prefs.edit().putString("fitting",json.toString())
        if(changed) {
            if(prefs.contains("fitting")) {
                val archives=prefs.all.keys.count { it.startsWith("setup-archive-") }
                if(archives >= 20) return false // Never silently overwrite prior fitting evidence.
                val archive=JSONObject()
                listOf("fitting","requested","saved").plus((1..6).map { "program-settings-$it" }).forEach { key ->
                    prefs.getString(key,null)?.let { archive.put(key,it) }
                }
                var id=System.currentTimeMillis()
                while(prefs.contains("setup-archive-$id")) id++
                edit.putString("setup-archive-$id",archive.toString())
            }
            edit.remove("requested").remove("saved").putBoolean("pending",false)
            (1..6).forEach { edit.remove("program-settings-$it") }
        }
        if(!edit.commit()) return false
        try { fitting=next } catch(e: Exception) {
            lastResult="Setup saved, but dependent settings need review: ${e.message}. No playback."
        }
        if(changed) { requested=RequestedState();saved=null;savedIssue=null }
        revision++;stateChanged?.invoke()
        return true
    }

    /** Called only by explicit Save actions; no playback and no implicit snapshot migration. */
    fun saveSnapshot(): String {
        if(busy.get()) return "Busy; wait or Stop before saving settings"
        return try {
            val snapshot=SavedSettings.snapshot(requested,requireNotNull(fitting) { "Save aid setup first" })
            check(prefs.edit().putString("saved",encode(snapshot)).commit()) { "Could not save settings; previous snapshot retained" }
            saved=snapshot;savedIssue=null
            "Requested settings saved. Unknown values remain unknown. Later commands will not replace this snapshot."
        } catch(e: Exception) { e.message ?: "Could not save settings; previous snapshot retained" }
    }
    fun restorePlan(snapshot: RequestedState = requireNotNull(saved) { savedIssue ?: "No saved settings" }): List<ManualRequest> =
        SavedSettings.plan(snapshot,requireNotNull(fitting) { "Save aid setup first" })

    private fun programPlan(plan: List<ManualRequest>, fit: Fitting): List<ManualRequest> = buildList {
        plan.forEachIndexed { index, request ->
            add(request)
            if(request is ManualRequest.SelectProgram) {
                val stored = try { decode(prefs.getString("program-settings-${request.slot}",null)) }
                    catch(_: Exception) { null }
                if(stored?.program == request.slot) {
                    val explicit = plan.drop(index+1).takeWhile { it !is ManualRequest.SelectProgram }
                        .filterIsInstance<ManualRequest.SetLevel>().map { it.control }.toSet()
                    val state = stored.copy(levels=stored.levels.filterKeys { it !in explicit })
                    addAll(SavedSettings.plan(state,fit).drop(1))
                }
            }
        }
    }

    fun execute(plan: List<ManualRequest>, restoring: Boolean, automatic: Boolean,
        foreground: () -> Boolean, backgroundAllowed: () -> Boolean,
        progress: (String) -> Unit, sleepTarget: Boolean? = null, done: (String, Boolean) -> Unit) {
        fun recorded(text: String,success: Boolean) = if(sleepTarget!=null && this::sleepRoutine.isInitialized)
            sleepRoutine.recordActionResult(sleepTarget,success,text) else text
        fun reject(text: String) { val result=recorded(text,false);if(sleepTarget!=null) lastResult=result;done(result,false) }
        val fit = fitting ?: run { reject("Save setup first"); return }
        if(!foreground() && !backgroundAllowed()) { reject("Open HeySig before sending a command"); return }
        if(busy.get()) { reject("Busy; wait or Stop"); return }
        if(automatic && this::sleepRoutine.isInitialized && sleepRoutine.state.blocksNoise) { reject("Automatic paused by sleep or manual override"); return }
        val executionPlan = try { if(restoring) plan else programPlan(plan,fit) }
            catch(e: Exception) { reject("Stored program settings need review: ${e.message}"); return }
        try {
            var simulated = requested
            executionPlan.forEach { ManualValidation.validate(it,fit,simulated); simulated = simulated.completed(it) }
            player.inspect(backgroundAllowed())
            check(prefs.edit().putBoolean("pending",true).commit()) { "Cannot journal command; no playback" }
            if(this::sleepRoutine.isInitialized) {
                if(sleepTarget != null) sleepRoutine.transition(sleepRoutine.state.begin())
                else if(!automatic && plan.any { it !is ManualRequest.Battery }) sleepRoutine.transition(sleepRoutine.state.manual())
            }
        } catch(e:Exception) { reject("Blocked: ${e.message}"); return }
        if(!busy.compareAndSet(false,true)) { reject("Busy; wait or Stop"); return }
        backgroundExecution=backgroundAllowed(); cancelled.set(false); lastResult = "Transmitting…"; revision++;stateChanged?.invoke();progress(lastResult)
        runCatching { sleepStateChanged?.invoke() }
        Thread({
            val completed = mutableListOf<String>()
            var result: String
            var durable = true
            var playbackSucceeded = false
            try {
                executionPlan.forEachIndexed { index, request ->
                    check(!cancelled.get()) { "Stopped" }
                    val stages: List<Pair<String,ByteArray>> = when(request) {
                        is ManualRequest.SelectProgram -> listOf("program ${request.slot}" to ProtoRequest.programPcm(request.slot,fit.controlAddress,profile))
                        is ManualRequest.SetLevel -> listOf("${if(request.control == LevelControl.SHARPNESS) "balance" else request.control.name.lowercase()} ${request.level}" to when(request.control) {
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
                    val edit = prefs.edit().putString("requested",encode(requested))
                    if(request is ManualRequest.SetLevel) {
                        val key="program-settings-${requested.program}"
                        val previous = try { decode(prefs.getString(key,null)) } catch(_: Exception) { null }
                        val remembered = RequestedState(requested.program,
                            (previous?.levels ?: emptyMap()) + requested.levels)
                        edit.putString(key,encode(remembered))
                    }
                    durable = edit.commit() && durable
                    if(index < executionPlan.lastIndex) gap()
                }
                // Playback updates only last-requested state. Explicit Save owns the restoration record.
                playbackSucceeded = !cancelled.get()
                result = "Played: ${completed.joinToString()}."
            } catch(e:Exception) { result = "${if(completed.isEmpty()) "No completed stages" else "Completed: ${completed.joinToString()}"}. ${e.message}. Current aid state may be uncertain." }
            finally {
                durable = prefs.edit().putString("requested",encode(requested)).putBoolean("pending",false).commit() && durable
                busy.set(false); backgroundExecution=false
            }
            if(!durable) result += " Storage failed; remembered state may not survive restart."
            main.post {
                var success=playbackSucceeded && durable && !cancelled.get()
                if(sleepTarget != null && this::sleepRoutine.isInitialized) {
                    try { sleepRoutine.transition(sleepRoutine.state.completed(sleepTarget,success)) }
                    catch(e: Exception) { success=false;result+=" Sleep state could not be saved; review routine before continuing." }
                }
                result=recorded(result,success)
                runCatching { sleepStateChanged?.invoke() }
                lastResult=result;revision++;stateChanged?.invoke(); done(result,success)
            }
        },"heysig-manual").start()
    }
    private fun gap() { repeat(100) { check(!cancelled.get()) { "Stopped" }; Thread.sleep(10) } }
    companion object {
        internal fun isolated(prefs: android.content.SharedPreferences, transport: CommandTransport, sleepPrefs: android.content.SharedPreferences? = null) = ManualRuntime(prefs,transport).also {
            if(sleepPrefs != null) it.sleepRoutine=SleepRoutineStore(sleepPrefs)
        }
        @Volatile private var instance: ManualRuntime?=null
        fun get(context: Context): ManualRuntime = instance ?: synchronized(this) {
            instance ?: ManualRuntime(context.applicationContext.getSharedPreferences("heysig-core-v1",Context.MODE_PRIVATE),SpeakerCommandTransport(context.applicationContext)).also {
                it.sleepRoutine=SleepRoutineStore(context);it.personalPhrases=PersonalPhraseStore(context)
                it.fitting?.let(it.personalPhrases::setupChanged);it.fitting?.let(it.sleepRoutine::setupChanged)
                val app=context.applicationContext
                it.sleepStateChanged={ if(AutomaticSleepService.running==null) AutomaticSleepScheduler(app).publishStatus() }
                instance=it
            }
        }
    }
}
