package com.heysig.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.*
import com.heysig.control.Program
import com.heysig.noise.*
import org.json.JSONArray
import org.json.JSONObject

/** Six-level draft editor and experimental Observe mode. It has no playback dependency. */
class NoiseFeature(
    private val activity: Activity,
    private val programs: () -> List<Program>,
    allowed: () -> Boolean,
    private val tell: (String,Boolean) -> Unit
) {
    private val prefs = activity.getSharedPreferences("heysig-noise-v1",Activity.MODE_PRIVATE)
    private var settings = NoiseSettings()
    private var stored = false
    private var loadError: String? = null
    private var draft = settings
    private var readEditor: (() -> NoiseSettings)? = null
    private var refreshEditor: (() -> Unit)? = null
    private var observation = NoiseObservation()
    private var measurement: NoiseMeasurement? = null
    private var mode = "Off"
    private var detail = "No samples yet"
    private var generation = 0
    private var runtime: TextView? = null
    private fun fittingSignature() = programs().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    private var reviewedFitting = ""
    private val sampler = PhoneNoiseSampler(activity,allowed)
    init {
        try {
            prefs.getString("settings",null)?.let { raw ->
                val json = JSONObject(raw)
                check(json.getInt("version") == 1) { "Unsupported stored noise settings" }
                val slots = json.getJSONArray("programs")
                val boundaries = json.getJSONArray("boundaries")
                settings = NoiseSettings((0 until slots.length()).map { if(slots.isNull(it)) null else slots.getInt(it) },
                    (0 until boundaries.length()).map { boundaries.getDouble(it) },json.getDouble("margin"),json.getInt("confirmations"),
                    json.getInt("cooldown"),json.getInt("capture"),json.getInt("wait"),json.getInt("freshness"))
                settings.validate(programs().map { it.slot }.toSet())
                reviewedFitting = json.optString("fitting")
                stored = reviewedFitting == fittingSignature()
                if(!stored) loadError = "Program fitting changed; review and save noise settings"
            }
        } catch(e:Exception) { settings = NoiseSettings(); loadError = e.message; stored = false }
        draft = settings
    }
    fun detachEditor() {
        // Preserve invalid text by refusing navigation until it is fixed or discarded.
        readEditor?.let { draft = it() }
        readEditor = null; refreshEditor = null; runtime = null
    }
    fun stop() {
        generation++; sampler.stop(); mode = "Off"; observation = NoiseObservation(); update()
    }
    fun fittingChanged() { stop(); stored = false; loadError = "Program fitting changed; review and save noise settings" }
    private fun label(slot: Int?) = if(slot == null) "Unused" else programs().find { it.slot == slot }?.let { "${it.name} (program $slot)" } ?: "Unavailable program $slot"
    private fun update() { runtime?.text = status() }
    fun status(): String {
        val age = observation.lastSampleMillis?.let { (SystemClock.elapsedRealtime()-it)/1000 }
        return "Noise: $mode • Source: this device's built-in microphone\n$detail\n" +
            (measurement?.let { "RMS index: ${"%.1f".format(java.util.Locale.US,it.rmsDbfs)} dBFS (relative digital level, not sound-pressure dB)\n" } ?: "") +
            (observation.level?.let { "Noise level $it → ${label(observation.target)}; confirmations ${observation.count}/${settings.confirmations}; age ${age}s\n" } ?: "") +
            (observation.proposed?.let { "Would select ${label(it)}. No command sent.\n" } ?: "") +
            "Automatic switching is not enabled in this build."
    }
    fun render(content: LinearLayout) {
        fun line(text:String) = TextView(activity).apply { this.text = text; textSize = 17f; content.addView(this) }
        fun button(text:String,action:()->Unit) { content.addView(Button(activity).apply { this.text = text; setOnClickListener { action() } }) }
        runtime = line(status())
        button("Sample now") { handle(NoiseCommand.Sample) }
        button("Start Observe") { handle(NoiseCommand.Observe) }
        button("Stop noise") { stop(); tell("Noise stopped",false) }
        line("Assign programs to noise levels 1 (quietest) through 6 (loudest). Repeated programs and unused levels are allowed. Thresholds below are illustrative until measured on this device. Save before observing. Leaving the app stops capture.")
        loadError?.let { line("Stored settings need review: $it. Save reviewed settings before Observe.") }
        val choices = listOf<Int?>(null) + programs().map { it.slot } + draft.programs.filterNotNull().filter { slot -> programs().none { it.slot == slot } }.distinct()
        val pickers = (1..6).map { level ->
            line("Noise level $level${if(level==1) " — quietest" else if(level==6) " — loudest" else ""}")
            Spinner(activity).apply {
                adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices.map { label(it) })
                setSelection(choices.indexOf(draft.programs[level-1]).coerceAtLeast(0)); content.addView(this)
            }
        }
        fun field(label:String,value:Any): EditText { line(label); return EditText(activity).apply { setSingleLine(); setText(value.toString()); content.addView(this) } }
        val boundaries = draft.boundaries.mapIndexed { i,value -> field("Threshold ${i+1}: between levels ${i+1} and ${i+2}",value) }
        val margin = field("Hysteresis margin",draft.margin)
        val confirmations = field("Consecutive confirmations (1–20)",draft.confirmations)
        val cooldown = field("Cooldown seconds (reserved for Automatic)",draft.cooldownSeconds)
        val capture = field("Capture seconds (1–10)",draft.captureSeconds)
        val wait = field("Wait seconds (1–300)",draft.waitSeconds)
        val freshness = field("Freshness limit seconds (must exceed capture + wait)",draft.freshnessSeconds)
        readEditor = {
            NoiseSettings(pickers.map { choices[it.selectedItemPosition] },boundaries.map { it.text.toString().toDoubleOrNull() ?: error("Enter numeric thresholds") },
                margin.text.toString().toDoubleOrNull() ?: error("Enter a numeric margin"),
                confirmations.text.toString().toIntOrNull() ?: error("Enter whole confirmations"),
                cooldown.text.toString().toIntOrNull() ?: error("Enter whole cooldown seconds"),
                capture.text.toString().toIntOrNull() ?: error("Enter whole capture seconds"),
                wait.text.toString().toIntOrNull() ?: error("Enter whole wait seconds"),
                freshness.text.toString().toIntOrNull() ?: error("Enter whole freshness seconds"))
        }
        refreshEditor = {
            pickers.forEachIndexed { i,picker -> picker.setSelection(choices.indexOf(draft.programs[i]).coerceAtLeast(0)) }
            boundaries.forEachIndexed { i,field -> field.setText(draft.boundaries[i].toString()) }
            margin.setText(draft.margin.toString()); confirmations.setText(draft.confirmations.toString())
            cooldown.setText(draft.cooldownSeconds.toString()); capture.setText(draft.captureSeconds.toString())
            wait.setText(draft.waitSeconds.toString()); freshness.setText(draft.freshnessSeconds.toString())
        }
        button("Save noise settings") { handle(NoiseCommand.Save) }
        button("Discard noise changes") { handle(NoiseCommand.Discard) }
        line("Voice: assign program three to noise level five; leave noise level four unused; set noise threshold one to minus sixty; save noise settings; observe noise; noise status; stop noise.")
    }
    fun handle(command: NoiseCommand) {
        try {
            if(command != NoiseCommand.Discard && command != NoiseCommand.Stop) readEditor?.let { draft = it() }
            when(command) {
                NoiseCommand.Open -> tell("Noise settings. Assign a program to each of six noise levels, review five thresholds, then say save noise settings. Automatic remains unavailable.",true)
                NoiseCommand.Read -> tell(status()+"\nDraft assignments: "+draft.programs.mapIndexed { i,slot -> "${i+1}: ${label(slot)}" }.joinToString(". ")+"\nDraft thresholds: "+draft.boundaries.joinToString()+". Margin ${draft.margin}. Confirmations ${draft.confirmations}. Cooldown ${draft.cooldownSeconds}. Capture ${draft.captureSeconds}. Wait ${draft.waitSeconds}. Freshness ${draft.freshnessSeconds}.",true)
                NoiseCommand.Stop -> { stop(); tell("Noise stopped",true) }
                NoiseCommand.Automatic -> tell("Automatic switching is not enabled. Use Observe to review proposed choices first.",true)
                NoiseCommand.Discard -> { draft = settings; refreshEditor?.invoke(); tell("Noise draft discarded",true) }
                NoiseCommand.Save -> {
                    draft.validate(programs().map { it.slot }.toSet())
                    val json = JSONObject().put("version",1).put("fitting",fittingSignature()).put("programs",JSONArray(draft.programs.map { it ?: JSONObject.NULL }))
                        .put("boundaries",JSONArray(draft.boundaries)).put("margin",draft.margin).put("confirmations",draft.confirmations)
                        .put("cooldown",draft.cooldownSeconds).put("capture",draft.captureSeconds).put("wait",draft.waitSeconds).put("freshness",draft.freshnessSeconds)
                    check(prefs.edit().putString("settings",json.toString()).commit()) { "Noise settings could not be saved; draft retained" }
                    settings = draft; reviewedFitting = fittingSignature(); stored = true; loadError = null; stop(); tell("Noise settings saved. Noise is off. Thresholds still need device measurement.",true)
                }
                is NoiseCommand.Assign -> {
                    require(command.slot == null || programs().any { it.slot == command.slot }) { "Configure that program in Setup first" }
                    stop(); draft = draft.copy(programs=draft.programs.toMutableList().also { it[command.level-1] = command.slot })
                    refreshEditor?.invoke(); tell("Draft noise level ${command.level}: ${label(command.slot)}. Say save noise settings when reviewed.",true)
                }
                is NoiseCommand.SetField -> {
                    val value = command.value
                    require(value.isFinite()) { "Use a finite number" }
                    if(command.field !in listOf("margin") && !command.field.startsWith("threshold ")) require(value%1==0.0 && value in 0.0..3600.0) { "Use whole seconds or confirmations within supported bounds" }
                    stop()
                    draft = when(command.field) {
                        "margin" -> draft.copy(margin=value)
                        "confirmations" -> draft.copy(confirmations=value.toInt())
                        "cooldown" -> draft.copy(cooldownSeconds=value.toInt())
                        "capture" -> draft.copy(captureSeconds=value.toInt())
                        "wait" -> draft.copy(waitSeconds=value.toInt())
                        "freshness" -> draft.copy(freshnessSeconds=value.toInt())
                        else -> draft.copy(boundaries=draft.boundaries.toMutableList().also { it[command.field.removePrefix("threshold ").toInt()-1]=value })
                    }
                    refreshEditor?.invoke(); tell("Draft noise ${command.field}: $value. Say save noise settings when reviewed.",true)
                }
                NoiseCommand.Observe, NoiseCommand.Sample -> start(command == NoiseCommand.Observe)
            }
        } catch(e:Exception) { tell("Noise: ${e.message}",true) }
    }
    private fun start(repeat:Boolean) {
        check(mode == "Off") { "Noise capture already active; Stop first" }
        if(repeat) {
            check(stored && draft == settings && reviewedFitting == fittingSignature()) { "Save reviewed noise settings before Observe" }
            settings.validate(programs().map { it.slot }.toSet())
            check(settings.programs.any { it != null }) { "Assign at least one program" }
        }
        if(!prefs.getBoolean("microphoneDisclosureV1",false)) {
            android.app.AlertDialog.Builder(activity).setTitle("Microphone for noise measurement")
                .setMessage("Noise sampling uses this device's built-in microphone while HeySig is open to calculate relative sound levels and propose a program. Audio samples are processed in memory, discarded after measurement, and are not recorded or uploaded by this feature. Speech recognition is separate and may use the installed service's network connection. You can stop sampling at any time.")
                .setNegativeButton("Not now",null)
                .setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("microphoneDisclosureV1",true).commit()) {
                        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
                            tell("After allowing microphone access, select Sample or Observe again.",false)
                        } else try { start(repeat) } catch(e:Exception) { tell("Noise: ${e.message}",false) }
                    } else tell("Could not save microphone choice; capture remains off",false)
                }.show()
            return
        }
        check(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
            "Allow microphone access, then select Sample or Observe again"
        }
        val myGeneration = ++generation
        val captureSettings = if(stored) settings else NoiseSettings()
        observation = NoiseObservation(); mode = if(repeat) "Observe" else "Sample"; detail = "Waiting ${captureSettings.waitSeconds.coerceAtLeast(10)} seconds before capture; no commands will be sent"
        val started = sampler.start(captureSettings,repeat) { value,error -> activity.runOnUiThread {
            if(generation == myGeneration) {
                measurement = value
                if(value == null || value.flags.isNotEmpty()) {
                    observation = NoiseObservation(); detail = error ?: "Sample rejected: ${value!!.flags.joinToString()}"
                } else if(repeat) {
                    observation = observeNoise(observation,value.rmsDbfs,SystemClock.elapsedRealtime(),settings)
                    detail = "Observing experimental relative RMS index; no commands sent"
                } else detail = "Sample complete. Review this device's index before choosing thresholds."
                if(!repeat) mode = "Off"
                update()
            }
        } }
        if(!started) { mode = "Off"; error("Previous capture is stopping; try again shortly") }
        update(); tell(if(repeat) "Observe started. Proposals appear on Noise; no commands are sent." else "Sampling microphone. Result will appear on Noise.",false)
    }
}
