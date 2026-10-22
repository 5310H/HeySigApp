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

/** Foreground noise adapter. Decisions and calibration remain in shared Kotlin. */
class NoiseFeature(
    private val activity: Activity,
    private val programs: () -> List<Program>,
    allowed: () -> Boolean,
    private val tell: (String,Boolean) -> Unit,
    private val safety: () -> NoiseSafety,
    private val lastRequested: () -> Int?,
    private val sendProgram: (Int, (Boolean) -> Unit) -> Unit,
    private val prefs: android.content.SharedPreferences = activity.getSharedPreferences("heysig-noise-v1",Activity.MODE_PRIVATE)
) {
    private var settings = NoiseSettings()
    private var stored = false
    private var loadError: String? = null
    private var draft = settings
    private var readEditor: (() -> NoiseSettings)? = null
    private var refreshEditor: (() -> Unit)? = null
    private var observation = NoiseObservation()
    private var measurement: NoiseMeasurement? = null
    private var calibration = NoiseCalibration()
    private var calibrating = false
    private var calibrationSource: String? = null
    private var calibrationCapture = 5
    private var proof: NoiseCalibrationProfile? = null
    private var savedProof: NoiseCalibrationProfile? = null
    private var automatic = NoiseAutomaticState()
    private var previewLast: Int? = null
    private var mode = "Off"
    private var detail = "No samples yet"
    private var generation = 0
    private var runtime: TextView? = null
    private var calibrationRuntime: TextView? = null
    private var refreshCalibration: (() -> Unit)? = null
    private fun automaticSettings() = settings.copy(sleepProgram=settings.sleepProgram ?:
        programs().firstOrNull { it.name.trim().equals("sleep",true) }?.slot)
    private fun fittingSignature() = programs().sortedBy { it.slot }.joinToString("|") { "${it.slot}:${it.name}" }
    private var reviewedFitting = ""
    @Volatile private var quietUntil = 0L
    private var lastAutomaticSuccessMillis: Long? = null
    private val sampler = PhoneNoiseSampler(activity) { allowed() && SystemClock.elapsedRealtime() >= quietUntil }
    init {
        try {
            prefs.getString("settings",null)?.let { raw ->
                val json = JSONObject(raw)
                check(json.getInt("version") == 1) { "Unsupported stored noise settings" }
                val slots = json.getJSONArray("programs")
                val boundaries = json.getJSONArray("boundaries")
                settings = NoiseSettings((0 until slots.length()).map { if(slots.isNull(it)) null else slots.getInt(it) },
                    (0 until boundaries.length()).map { boundaries.getDouble(it) },json.getDouble("margin"),json.getInt("confirmations"),
                    json.getInt("cooldown"),json.getInt("capture"),json.getInt("wait"),json.getInt("freshness"),
                    if(json.has("sleepProgram") && !json.isNull("sleepProgram")) json.getInt("sleepProgram") else null)
                settings.validate(programs().map { it.slot }.toSet())
                reviewedFitting = json.optString("fitting")
                stored = reviewedFitting == fittingSignature()
                if(!stored) loadError = "Program fitting changed; review and save noise settings"
            }
        } catch(e:Exception) { settings = NoiseSettings(); loadError = e.message; stored = false }
        draft = settings
        runCatching {
            val json = JSONObject(prefs.getString("calibration",null) ?: return@runCatching)
            check(json.getInt("version") == 1)
            val rows = json.getJSONArray("levels")
            proof = NoiseCalibrationProfile(NoiseCalibration((0 until rows.length()).map { i ->
                val row = rows.getJSONArray(i); (0 until row.length()).map { row.getDouble(it) }
            }),json.getString("source"),json.getInt("capture"),json.getDouble("margin"))
        } // Missing/invalid proof never permits Automatic; existing settings remain available.
        savedProof = proof
    }
    private fun calibrated(config: NoiseSettings = settings) = runCatching {
        proof?.matches(config,sampler.sourceIdentity()) == true
    }.getOrDefault(false)
    private fun calibrationText(): String = if(calibrating) {
        "Calibration: " + calibration.levels.mapIndexed { i,row -> "level ${i+1}: ${row.size}/3" }.joinToString(" · ")
    } else if(calibrated()) "Measured calibration matches this device and saved capture settings." else "Calibration required for automatic preview."

    fun detachEditor() {
        // Preserve invalid text by refusing navigation until it is fixed or discarded.
        readEditor?.let { draft = it() }
        readEditor = null; refreshEditor = null; runtime = null; calibrationRuntime = null; refreshCalibration = null
    }
    fun stop() {
        generation++; sampler.stop(); quietUntil = 0L; mode = "Off"; observation = NoiseObservation(); automatic = NoiseAutomaticState(); update()
    }
    fun fittingChanged() { stop(); stored = false; loadError = "Program fitting changed; review and save noise settings" }
    private fun label(slot: Int?) = if(slot == null) "Unused" else programs().find { it.slot == slot }?.let { "${it.name} (program $slot)" } ?: "Unavailable program $slot"
    private fun update() {
        runtime?.apply { text=summary(); contentDescription=status() }
        calibrationRuntime?.text=calibrationProgress()
        refreshCalibration?.invoke()
    }
    private fun calibrationProgress(): String {
        val level=calibration.levels.indexOfFirst { it.size < 3 }
        val progress=when {
            calibrating && level >= 0 -> "Level ${level+1} of 6 · ${calibration.levels[level].size}/3 samples"
            calibrating -> "18 samples ready to review"
            draft != settings && calibrated(draft) -> "Ready to save"
            calibrated() -> "Calibration saved"
            else -> "Not calibrated"
        }
        val extra=when {
            mode == "Calibration sample" -> "In progress · Stop to cancel"
            detail == "No samples yet" || detail.startsWith("Prepare level") || detail.startsWith("Captured level") -> ""
            else -> detail
        }
        return if(extra.isEmpty()) progress else activity.getString(R.string.two_lines,progress,extra)
    }
    private fun summary(): String = buildString {
        append(when {
            mode != "Off" -> "Sampling · $mode"
            loadError != null -> "Needs review · $loadError"
            !stored -> "Needs setup · Choose programs and save them in step 1."
            !calibrated() -> "Needs calibration · Take samples in step 2 for automatic preview."
            else -> "Ready for preview · No hearing-aid commands will be sent."
        })
        measurement?.let { if(it.flags.isEmpty()) append(" · %.1f dBFS".format(java.util.Locale.US,it.rmsDbfs)) else append(" · sample rejected") }
        observation.target?.let { append("\n${label(it)} · ${observation.count}/${settings.confirmations}") }
        if(detail != "No samples yet") append("\n$detail")
    }
    fun status(): String {
        val age = observation.lastSampleMillis?.let { (SystemClock.elapsedRealtime()-it)/1000 }
        return "Noise: $mode • Source: this device's built-in microphone\n$detail\n" +
            (measurement?.let { "RMS index: ${"%.1f".format(java.util.Locale.US,it.rmsDbfs)} dBFS (relative digital level, not sound-pressure dB)\n" } ?: "") +
            (observation.level?.let { "Noise level $it → ${label(observation.target)}; confirmations ${observation.count}/${settings.confirmations}; age ${age}s\n" } ?: "") +
            (observation.proposed?.takeIf { mode == "Observe" || mode == "Automatic preview" }?.let { "Would select ${label(it)}. No command sent.\n" } ?: "") +
            calibrationText()+if(NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED) "\nAutomatic requires reviewed calibration and an open, unlocked app." else "\nAutomatic switching remains locked pending physical acceptance."
    }
    fun render(content: LinearLayout, page: String = "Noise", navigate: (String) -> Unit = {}) {
        val firstView = content.childCount
        val calibrationButtons=mutableMapOf<String,Button>()
        var target = content
        fun line(text:String) = TextView(activity).apply {
            this.text = text; PhoneStyle.text(this,16f,color=PhoneStyle.secondary)
            setPadding(0,PhoneStyle.dp(activity,4),0,PhoneStyle.dp(activity,10)); target.addView(this)
        }
        fun button(text:String,action:()->Unit) = Button(activity).also {
            it.text = text; PhoneStyle.action(it); it.setOnClickListener { action() }; target.addView(it)
            calibrationButtons[text]=it
        }
        fun group(title:String,build:()->Unit) {
            val parent = target; target = PhoneStyle.card(activity,parent).also { it.tag = title }
            try { PhoneStyle.heading(activity,target,title); build() } finally { target = parent }
        }
        group("Noise observation") {
            line("Find a suitable program for your surroundings. This guided trial measures sound and shows suggestions without changing your aids.")
            runtime = line(summary()).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE; contentDescription=status() }
            line("Keep HeySig open and unlocked. Sampling uses this device's built-in microphone after your permission; audio is processed in memory and discarded. Stop cancels measurement. Noise sampling turns hands-free listening Off.")
            PhoneStyle.action(button("Show program suggestions") { handle(NoiseCommand.Observe) },"primary")
            button("Sample now") { handle(NoiseCommand.Sample) }
            PhoneStyle.action(button("Stop noise") { stop(); tell("Noise stopped",false) },"danger")
            val sample=checkNotNull(calibrationButtons["Sample now"])
            val stop=checkNotNull(calibrationButtons["Stop noise"])
            target.removeView(sample); target.removeView(stop)
            val row=LinearLayout(activity)
            row.addView(sample,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=PhoneStyle.dp(activity,6) })
            row.addView(stop,LinearLayout.LayoutParams(0,-2,1f))
            target.addView(row)
            line("Sample now measures once without setup. Program suggestions need saved program choices. Automatic switching is unavailable in this build; completing calibration enables preview only.")
        }
        group("Guided calibration") {
            calibrationRuntime = line(calibrationProgress())
            line("Step 2 of 3 · Learn this device's sound levels. Take three samples in each of six environments, from quietest to loudest. Use comfortable everyday sounds; do not create loud noise for this task. Saved choices remain unchanged until you review and save.")
            button("Start new calibration") {
                android.app.AlertDialog.Builder(activity).setTitle("Measure six listening levels?")
                    .setMessage("This replaces only unfinished calibration samples. Saved settings stay unchanged until you apply and save. Use naturally occurring, comfortable environments; if six levels cannot be separated, keep using Observe.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Start") { _,_ ->
                        runCatching {
                            readEditor?.let { draft = it() }; draft.validate(programs().map { it.slot }.toSet())
                            stop(); calibration = NoiseCalibration(); calibrating = true
                            calibrationSource = sampler.sourceIdentity(); calibrationCapture = draft.captureSeconds
                            detail = "Prepare level 1, the quietest environment. Capture three samples before moving to level 2."; update()
                        }.onFailure { tell("Calibration: ${it.message}",false) }
                    }.show()
            }
            button("Capture next calibration sample") {
                runCatching {
                    check(calibrating) { "Start calibration first" }
                    check(calibration.levels.any { it.size < 3 }) { "All samples captured; review and apply" }
                    start(false,calibrationSample=true)
                }.onFailure { tell("Calibration: ${it.message}",false) }
            }
            button("Review calibration thresholds") {
                runCatching {
                    check(calibrating && mode == "Off") { "Finish or stop the current sample first" }
                    readEditor?.let { draft = it() }
                    check(calibrationCapture == draft.captureSeconds && calibrationSource == sampler.sourceIdentity()) { "Capture settings or microphone changed; start calibration again" }
                    val boundaries = calibration.thresholds(draft.margin)
                    android.app.AlertDialog.Builder(activity).setTitle("Use measured thresholds?")
                        .setMessage(calibration.levels.mapIndexed { i,row -> "Level ${i+1}: ${row.joinToString { "%.1f".format(java.util.Locale.US,it) }} dBFS" }.joinToString("\n")+
                            "\nThresholds: ${boundaries.joinToString { "%.1f".format(java.util.Locale.US,it) }}\nThis updates the draft only. Review program assignments, then Save noise settings. No command is sent.")
                        .setNegativeButton("Keep measuring",null).setPositiveButton("Apply to draft") { _,_ ->
                            runCatching {
                                check(calibrating && mode == "Off") { "Calibration is no longer ready" }
                                readEditor?.let { draft = it() }
                                check(calibrationSource == sampler.sourceIdentity() && calibrationCapture == draft.captureSeconds && calibration.thresholds(draft.margin) == boundaries) { "Calibration settings changed; review again" }
                                draft = draft.copy(boundaries=boundaries)
                                proof = NoiseCalibrationProfile(calibration,checkNotNull(calibrationSource),calibrationCapture,draft.margin)
                                calibrating = false; refreshEditor?.invoke(); detail = "Measured thresholds applied to draft. Save noise settings when reviewed."; update()
                            }.onFailure { tell("Calibration: ${it.message}",false) }
                        }.show()
                }.onFailure { tell("Calibration: ${it.message}",false) }
            }
            button("Cancel calibration") { stop(); calibrating = false; calibration = NoiseCalibration(); detail = "Unfinished calibration cleared. Saved noise settings retained."; update() }
        }
        group("Your listening levels") {
            line("Step 1 of 3 · Choose which of your configured programs you would use as surroundings get louder. Reuse programs or leave levels unused. HeySig does not choose fitting values for you. Save these choices, then continue to sound samples.")
            loadError?.let { line("Review needed: $it") }
        }
        val choices = listOf<Int?>(null) + programs().map { it.slot } + draft.programs.filterNotNull().filter { slot -> programs().none { it.slot == slot } }.distinct()
        val pickers = (1..6).map { level ->
            var picker: Spinner? = null
            group("Level $level${if(level == 1) " · Quietest" else if(level == 6) " · Loudest" else ""}") {
                picker = Spinner(activity).apply {
                    contentDescription = "Program for noise level $level"
                    adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices.map { label(it) })
                    setSelection(choices.indexOf(draft.programs[level-1]).coerceAtLeast(0)); target.addView(this)
                }
            }
            checkNotNull(picker)
        }
        lateinit var sleepPicker: Spinner
        group("Protect Sleep") {
            line("Choose your Sleep program if you have one. Programs named Sleep are also protected. Automatic preview will never enter or leave it. Every manual request stops automatic sessions, including repeats. After Sleep, select a waking program manually before restarting.")
            sleepPicker = Spinner(activity).apply {
                contentDescription = "Protected Sleep program"
                adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices.map { if(it == null) "No Sleep program" else label(it) })
                setSelection(choices.indexOf(draft.sleepProgram).coerceAtLeast(0)); target.addView(this)
            }
        }
        val advanced = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        val expand = button("Show measurement settings") { advanced.visibility = if(advanced.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
        expand.setOnClickListener {
            val opening = advanced.visibility != android.view.View.VISIBLE
            advanced.visibility = if(opening) android.view.View.VISIBLE else android.view.View.GONE
            expand.text = if(opening) "Hide measurement settings" else "Show measurement settings"
        }
        content.addView(advanced)
        target = advanced
        fun field(label:String,value:Any): EditText {
            line(label)
            return EditText(activity).apply {
                contentDescription = label; setSingleLine(); setText(value.toString()); target.addView(this)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }
        }
        var boundaries = emptyList<EditText>()
        lateinit var margin: EditText; lateinit var confirmations: EditText; lateinit var cooldown: EditText
        lateinit var capture: EditText; lateinit var wait: EditText; lateinit var freshness: EditText
        group("Measurement settings") {
            line("Advanced options. Keep existing values unless you understand their effect. Thresholds are illustrative until calibrated on this device. These are relative microphone levels, not sound-pressure dB. Changing these settings may require new calibration.")
            boundaries = draft.boundaries.mapIndexed { i,value -> field("Threshold ${i+1} · levels ${i+1} and ${i+2}",value) }
            margin = field("Change buffer (hysteresis margin)",draft.margin)
            confirmations = field("Matching samples before a suggestion (1–20)",draft.confirmations)
            cooldown = field("Minimum seconds between preview changes",draft.cooldownSeconds)
            capture = field("Sample duration in seconds (1–10)",draft.captureSeconds)
            wait = field("Wait seconds (1–300)",draft.waitSeconds)
            freshness = field("Freshness limit seconds (must exceed capture + wait)",draft.freshnessSeconds)
        }
        target = content
        readEditor = {
            NoiseSettings(pickers.map { choices[it.selectedItemPosition] },boundaries.map { it.text.toString().toDoubleOrNull() ?: error("Enter numeric thresholds") },
                margin.text.toString().toDoubleOrNull() ?: error("Enter a numeric margin"),
                confirmations.text.toString().toIntOrNull() ?: error("Enter whole confirmations"),
                cooldown.text.toString().toIntOrNull() ?: error("Enter whole cooldown seconds"),
                capture.text.toString().toIntOrNull() ?: error("Enter whole capture seconds"),
                wait.text.toString().toIntOrNull() ?: error("Enter whole wait seconds"),
                freshness.text.toString().toIntOrNull() ?: error("Enter whole freshness seconds"),choices[sleepPicker.selectedItemPosition])
        }
        refreshEditor = {
            sleepPicker.setSelection(choices.indexOf(draft.sleepProgram).coerceAtLeast(0))
            pickers.forEachIndexed { i,picker -> picker.setSelection(choices.indexOf(draft.programs[i]).coerceAtLeast(0)) }
            boundaries.forEachIndexed { i,field -> field.setText(activity.getString(R.string.decimal_value,draft.boundaries[i].toString())) }
            margin.setText(activity.getString(R.string.decimal_value,draft.margin.toString())); confirmations.setText(activity.getString(R.string.integer_value,draft.confirmations.toString()))
            cooldown.setText(activity.getString(R.string.integer_value,draft.cooldownSeconds.toString())); capture.setText(activity.getString(R.string.integer_value,draft.captureSeconds.toString()))
            wait.setText(activity.getString(R.string.integer_value,draft.waitSeconds.toString())); freshness.setText(activity.getString(R.string.integer_value,draft.freshnessSeconds.toString()))
        }
        fun edited() {
            if(mode != "Off" && runCatching { readEditor?.invoke() != settings }.getOrDefault(true)) {
                stop(); detail = "Noise stopped because the draft changed. Review and save before restarting."; update()
            }
        }
        (boundaries + listOf(margin,confirmations,cooldown,capture,wait,freshness)).forEach { field ->
            field.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?, start:Int, count:Int, after:Int) = Unit
                override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) = Unit
                override fun afterTextChanged(s:android.text.Editable?) { edited() }
            })
        }
        (pickers + sleepPicker).forEach { picker -> picker.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?, view:android.view.View?, position:Int, id:Long) { edited() }
            override fun onNothingSelected(parent:AdapterView<*>?) { edited() }
        } }
        PhoneStyle.action(button("Save noise settings") { handle(NoiseCommand.Save) },"primary")
        button("Discard noise changes") { handle(NoiseCommand.Discard) }
        // Construct once per route so draft reading/validation is unchanged; show only its task.
        for(i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            val title = child.tag as? String
            val buttonText = (child as? Button)?.text?.toString()
            val visible = when(page) {
                "Noise" -> title == "Noise observation"
                "NoiseCalibration" -> title == "Guided calibration" || buttonText in listOf("Save noise settings","Discard noise changes")
                "NoiseSetup" -> title == "Your listening levels" || title?.startsWith("Level ") == true || title == "Protect Sleep" || buttonText in listOf("Save noise settings","Discard noise changes")
                "NoiseMeasure" -> child === advanced || buttonText in listOf("Save noise settings","Discard noise changes")
                else -> false
            }
            // Headers/status outside this adapter are owned by HomeActivity.
            if(i >= firstView) child.visibility = if(visible) android.view.View.VISIBLE else android.view.View.GONE
        }
        if(page == "Noise") {
            button("1. Choose programs") { navigate("NoiseSetup") }
            button("2. Learn sound levels") { navigate("NoiseCalibration") }
            button("3. Preview suggested changes") { navigate("NoiseDetails") }
        }
        if(page == "NoiseSetup") {
            button("Next: learn sound levels") { navigate("NoiseCalibration") }
            button("Advanced measurement settings") { navigate("NoiseMeasure") }
        }
        if(page == "NoiseCalibration") button("Next: preview suggested changes") { navigate("NoiseDetails") }
        refreshCalibration = {
            val complete=calibration.levels.all { it.size == 3 }
            fun show(label:String, visible:Boolean) { calibrationButtons[label]?.visibility=if(visible) android.view.View.VISIBLE else android.view.View.GONE }
            show("Start new calibration",!calibrating)
            show("Capture next calibration sample",calibrating && !complete)
            calibrationButtons["Capture next calibration sample"]?.isEnabled=mode == "Off"
            show("Review calibration thresholds",calibrating && complete)
            show("Cancel calibration",calibrating)
            if(page == "NoiseCalibration") {
                show("Save noise settings",draft != settings || proof != savedProof)
                show("Discard noise changes",draft != settings || proof != savedProof)
            }
        }
        refreshCalibration?.invoke()
        if(page == "NoiseDetails") {
            line("Step 3 of 3 · Preview before use")
            runtime=line(summary()).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
            line("Preview simulates program changes without sending commands. First save your program choices and measured calibration, then select a daytime program on Home. Keep HeySig open and unlocked. Leave the Sleep program before starting preview.")
            button("Preview suggested changes") { runCatching { start(true,preview=true) }.onFailure { tell("Noise: ${it.message}",false) } }
            button("Stop preview") { stop();tell("Preview stopped. No command sent.",false) }
            button("Review program choices") { navigate("NoiseSetup") }
            button("Review calibration") { navigate("NoiseCalibration") }
            val details=TextView(activity).apply { text=status();PhoneStyle.text(this,16f,color=PhoneStyle.secondary);visibility=android.view.View.GONE }
            button("Advanced status") { details.text=status();details.visibility=if(details.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
            content.addView(details)
            line("Automatic switching is unavailable until device testing is complete. Calibration alone does not enable it.")
            button("Start Automatic") { handle(NoiseCommand.Automatic) }.apply { isEnabled=NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED;visibility=if(isEnabled) android.view.View.VISIBLE else android.view.View.GONE }
        }

    }
    fun handle(command: NoiseCommand) {
        try {
            if(command != NoiseCommand.Discard && command != NoiseCommand.Stop) readEditor?.let { draft = it() }
            when(command) {
                NoiseCommand.Open -> tell("Noise settings. Assign a program to each of six noise levels. Guided calibration and automatic preview are available on the phone. Automatic remains locked pending physical acceptance.",true)
                NoiseCommand.Read -> tell(status()+"\nDraft assignments: "+draft.programs.mapIndexed { i,slot -> "${i+1}: ${label(slot)}" }.joinToString(". ")+"\nDraft thresholds: "+draft.boundaries.joinToString()+". Margin ${draft.margin}. Confirmations ${draft.confirmations}. Cooldown ${draft.cooldownSeconds}. Capture ${draft.captureSeconds}. Wait ${draft.waitSeconds}. Freshness ${draft.freshnessSeconds}.",true)
                NoiseCommand.Stop -> { stop(); tell("Noise stopped",true) }
                NoiseCommand.Automatic -> {
                    check(NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED) { "Automatic switching is locked pending physical acceptance. Use Preview Automatic on the phone; it sends no commands." }
                    start(true,automaticMode=true)
                }
                NoiseCommand.Discard -> { stop(); draft = settings; proof = savedProof; refreshEditor?.invoke(); update(); tell("Noise draft discarded",true) }
                NoiseCommand.Save -> {
                    draft.validate(programs().map { it.slot }.toSet())
                    val json = JSONObject().put("version",1).put("fitting",fittingSignature()).put("programs",JSONArray(draft.programs.map { it ?: JSONObject.NULL }))
                        .put("boundaries",JSONArray(draft.boundaries)).put("margin",draft.margin).put("confirmations",draft.confirmations)
                        .put("cooldown",draft.cooldownSeconds).put("capture",draft.captureSeconds).put("wait",draft.waitSeconds).put("freshness",draft.freshnessSeconds).put("sleepProgram",draft.sleepProgram ?: JSONObject.NULL)
                    val proofJson = proof?.takeIf { calibrated(draft) }?.let { p ->
                        JSONObject().put("version",1).put("source",p.source).put("capture",p.captureSeconds).put("margin",p.margin)
                            .put("levels",JSONArray(p.samples.levels.map { JSONArray(it) })).toString()
                    }
                    check(prefs.edit().putString("settings",json.toString()).putString("calibration",proofJson).commit()) { "Noise settings could not be saved; draft retained" }
                    settings = draft; if(proofJson == null) proof = null; savedProof = proof; reviewedFitting = fittingSignature(); stored = true; loadError = null; stop(); tell("Noise settings saved. Noise is off. "+calibrationText(),true)
                }
                is NoiseCommand.Assign -> {
                    require(command.slot == null || programs().any { it.slot == command.slot }) { "Configure that program in Setup first" }
                    stop(); draft = draft.copy(programs=draft.programs.toMutableList().also { it[command.level-1] = command.slot })
                    refreshEditor?.invoke(); update(); tell("Draft noise level ${command.level}: ${label(command.slot)}. Say save noise settings when reviewed.",true)
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
                    refreshEditor?.invoke(); update(); tell("Draft noise ${command.field}: $value. Say save noise settings when reviewed.",true)
                }
                NoiseCommand.Observe, NoiseCommand.Sample -> start(command == NoiseCommand.Observe)
            }
        } catch(e:Exception) { tell("Noise: ${e.message}",true) }
    }
    private fun start(repeat:Boolean, calibrationSample:Boolean = false, preview:Boolean = false, automaticMode:Boolean = false) {
        com.heysig.app.voice.HandsFreeService.running?.control(com.heysig.voice.HandsFreeControl.OFF)
        readEditor?.let { draft = it() }
        check(mode == "Off") { "Noise capture already active; Stop first" }
        if(preview || automaticMode) {
            check(calibrated()) { "Complete step 2: take calibration samples, review them and save noise settings before previewing" }
            check(lastRequested() != null) { "Select a waking program manually before starting automatic preview or switching" }
            val guards = safety().copy(calibrated=true,physicallyAccepted=preview || NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED,
                sleeping=safety().sleeping || (automaticSettings().sleepProgram != null && lastRequested() == automaticSettings().sleepProgram))
            check(guards.blocked() == null) { guards.blocked() ?: "Not ready" }
        }
        if(calibrationSample) {
            draft.validate(programs().map { it.slot }.toSet())
            check(calibrationSource == sampler.sourceIdentity() && calibrationCapture == draft.captureSeconds) { "Microphone or capture settings changed; start calibration again" }
        }
        if(repeat) {
            check(stored && draft == settings && reviewedFitting == fittingSignature()) { "Save your program choices in step 1 before starting suggestions" }
            settings.validate(programs().map { it.slot }.toSet())
            check(settings.programs.any { it != null }) { "Choose at least one program in step 1" }
        }
        if(!prefs.getBoolean("microphoneDisclosureV2",false)) {
            android.app.AlertDialog.Builder(activity).setTitle("Microphone for noise measurement")
                .setMessage("Noise sampling uses this device's built-in microphone while HeySig is open to calculate relative sound levels, calibrate thresholds and propose a program. Calibration saves 18 relative level numbers and microphone/capture configuration locally when you Save noise settings. Audio samples are processed in memory, discarded after measurement, and are not recorded or uploaded by this feature. Speech recognition is separate and may use the installed service's network connection. You can stop sampling at any time.")
                .setNegativeButton("Not now",null)
                .setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("microphoneDisclosureV2",true).commit()) {
                        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
                            tell("After allowing microphone access, select your sampling action again.",false)
                        } else try { start(repeat,calibrationSample,preview,automaticMode) } catch(e:Exception) { tell("Noise: ${e.message}",false) }
                    } else tell("Could not save microphone choice; capture remains off",false)
                }.show()
            return
        }
        check(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
            "Allow microphone access, then select your sampling action again"
        }
        val myGeneration = ++generation
        val captureSettings = if(calibrationSample) draft else if(stored) settings else NoiseSettings()
        val source = sampler.sourceIdentity()
        automatic = NoiseAutomaticState(lastSuccessMillis=if(preview) null else lastAutomaticSuccessMillis ?: SystemClock.elapsedRealtime()); previewLast = lastRequested()
        observation = NoiseObservation(); mode = when { calibrationSample -> "Calibration sample"; preview -> "Automatic preview"; automaticMode -> "Automatic"; repeat -> "Observe"; else -> "Sample" }; detail = "Waiting ${captureSettings.waitSeconds.coerceAtLeast(10)} seconds before capture. "+if(automaticMode) "Automatic may send confirmed program requests." else "No commands will be sent."
        val started = sampler.start(captureSettings,repeat) { value,error,capturedAt -> activity.runOnUiThread {
            if(generation == myGeneration) {
                val deliveredAt = SystemClock.elapsedRealtime()
                if(!isFreshNoiseSample(capturedAt,deliveredAt,captureSettings.freshnessSeconds)) {
                    observation = NoiseObservation(); automatic = automatic.copy(observation=NoiseObservation())
                    if(!repeat) mode = "Off"
                    detail = "Delayed sample rejected; confirmations cleared. Request another sample if needed."; update(); return@runOnUiThread
                }
                if(automaticMode && capturedAt < quietUntil) {
                    observation = NoiseObservation(); detail = "Waiting for command completion and microphone settling"; update(); return@runOnUiThread
                }
                measurement = value
                if(source != runCatching { sampler.sourceIdentity() }.getOrNull()) {
                    stop(); detail = "Microphone source changed; restart calibration or observation"; update(); return@runOnUiThread
                }
                if(value == null || value.flags.isNotEmpty()) {
                    if(preview || automaticMode) automatic = automatic.copy(observation=NoiseObservation())
                    observation = NoiseObservation()
                    val settling = automaticMode && quietUntil > SystemClock.elapsedRealtime()
                    if(value == null && !settling) stop()
                    detail = if(settling) "Waiting for command completion and microphone settling" else error ?: "Sample rejected: ${value!!.flags.joinToString()}"
                } else if(calibrationSample) {
                    val level = calibration.levels.indexOfFirst { it.size < 3 }+1
                    calibration = calibration.add(level,value)
                    val next = calibration.levels.indexOfFirst { it.size < 3 }+1
                    detail = if(next == 0) "All 18 samples captured. Review calibration thresholds." else
                        "Captured level $level. Prepare level $next (${calibration.levels[next-1].size}/3 captured), then request the next sample."
                } else if(preview || automaticMode) {
                    processAutomatic(value.rmsDbfs,preview,myGeneration,capturedAt)
                } else if(repeat) {
                    observation = observeNoise(observation,value.rmsDbfs,capturedAt,settings)
                    detail = "Observing experimental relative RMS index; no commands sent"
                } else detail = "Sample complete. Review this device's index before choosing thresholds."
                if(!repeat) mode = "Off"
                update()
            }
        } }
        if(!started) { mode = "Off"; error("The previous measurement is still stopping. Wait a moment, then try again") }
        update(); tell(if(preview) "Automatic preview started. No commands are sent." else if(automaticMode) "Automatic started; keep this app open." else if(calibrationSample) "Calibration sample requested. Stay quiet during measurement." else if(repeat) "Observe started. Proposals appear on Noise; no commands are sent." else "Sampling microphone. Result will appear on Noise.",false)
    }
    private fun processAutomatic(index:Double, preview:Boolean, session:Int, capturedAt:Long) {
        val guards = safety().copy(calibrated=calibrated(),physicallyAccepted=preview || NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED)
        val decision = automaticNoise(automatic,index,capturedAt,automaticSettings(),guards,if(preview) previewLast else lastRequested())
        automatic = decision.state; observation = automatic.observation
        detail = "${if(preview) "Preview" else "Automatic"}: ${decision.reason}"
        val target = decision.target ?: return
        val token = automatic.token
        if(preview) {
            previewLast = target
            automatic = completeNoise(automatic,token,true,SystemClock.elapsedRealtime())
            detail = "Would select ${label(target)}. No command sent. Simulating cooldown."
        } else {
            // Gate at the final dispatch boundary as well as at start. No preferences bypass it.
            check(NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED)
            quietUntil = Long.MAX_VALUE
            sendProgram(target) { success -> activity.runOnUiThread {
                if(generation == session) {
                    automatic = completeNoise(automatic,token,success,SystemClock.elapsedRealtime())
                    if(success) {
                        lastAutomaticSuccessMillis = automatic.lastSuccessMillis
                        quietUntil = SystemClock.elapsedRealtime()+settings.waitSeconds.coerceAtLeast(10)*1000L
                    } else stop()
                    detail = if(success) "Program command played; settling before fresh confirmations." else "Playback failed or interrupted. Automatic stopped; state may be uncertain."
                    update()
                }
            } }
        }
    }

}
