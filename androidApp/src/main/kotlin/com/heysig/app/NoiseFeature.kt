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
    private val prefs: android.content.SharedPreferences = activity.getSharedPreferences("heysig-noise-v1",Activity.MODE_PRIVATE),
    private val prepareAutomatic: (() -> (() -> Unit))?=null,
    private val openSoundType: (() -> Unit)?=null,
    private val prepareSpeechAutomatic: (() -> Unit)?=null
) {
    private val speechAutomatic by lazy { SpeechAutomaticFeature(activity,programs,{
        automaticSettings().let { if(stored && reviewedFitting==fittingSignature()) it else it.copy(programs=List(6) { null }) }
    },safety,{
        stop();prepareSpeechAutomatic?.invoke();checkNotNull(prepareAutomatic) { "Automatic needs the visible phone adapter" }.invoke()
    }) }
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
    private var calibrationStartUntil=0L
    private var calibrationReview: NoiseCalibrationReview?=null
    private var calibrationCapture = 5
    private var proof: NoiseCalibrationProfile? = null
    private var savedProof: NoiseCalibrationProfile? = null
    private var automatic = NoiseAutomaticState()
    private var previewLast: Int? = null
    private var learner: NoiseThresholdLearner? = null
    private fun learningContext()=NoiseLearningContext(settings,sampler.sourceIdentity(),fittingSignature())
    private fun syncLearning(): NoiseThresholdLearner {
        val context=learningContext()
        return learner?.takeIf { it.context==context } ?: NoiseThresholdLearner(context).also { learner=it }
    }
    private fun learningStatus(): String {
        AutomaticNoiseService.running?.learner?.let { learner=it }
        val learned=learner ?: return "Speech-aware Automatic uses a fixed model; it does not learn or change saved thresholds."
        return if(learned.suggest(SystemClock.elapsedRealtime())!=null) "Threshold suggestion ready · Review learned thresholds."
        else "Learning thresholds: ${learned.sampleCount} valid measurements · saved thresholds unchanged."
    }
    private fun reviewLearnedThresholds() {
        readEditor?.let { draft=it() }
        check(stored && draft==settings && reviewedFitting==fittingSignature()) { "Review and save existing noise choices first" }
        check(automatic.pendingTarget==null && safety().let { it.foreground && it.unlocked && it.audioFree }) { "Wait until playback and speech finish; keep HeySig open and unlocked" }
        val learned=syncLearning()
        val proposal=learned.suggest(SystemClock.elapsedRealtime()) ?: error(learningStatus()+" Suggestions need at least five minutes of varied measurements and ten in each adjacent level; unchanged or overlapping levels make no suggestion.")
        stop()
        fun number(value: Double)="%.1f".format(java.util.Locale.US,value)
        val changes=settings.boundaries.indices.joinToString("\n") { i ->
            "Boundary ${i+1}: ${number(settings.boundaries[i])} → ${number(proposal.boundaries[i])} dBFS" }
        android.app.AlertDialog.Builder(activity).setTitle("Review learned thresholds")
            .setMessage("$changes\n\nMeasurements per level: ${proposal.counts.joinToString()}. Only boundaries with enough separated measurements can change, by at most 2 dBFS per review. These suggestions reflect observed loudness, not your preferred program boundaries. Program assignments stay the same.\n\nAutomatic is now Off. Approve & save replaces thresholds and clears any guided calibration proof tied to the old thresholds. Assignments and timing stay unchanged; start Automatic again when ready. Review expires in one minute.")
            .setNegativeButton("Keep current",null)
            .setPositiveButton("Approve & save") { _,_ -> runCatching {
                readEditor?.let { draft=it() }
                check(mode=="Off" && draft==settings && learner===learned && reviewedFitting==fittingSignature()) { "Noise state changed; review again" }
                check(safety().let { it.foreground && it.unlocked && it.audioFree }) { "Keep HeySig open, unlocked and free of playback" }
                draft=proposal.approved(learningContext(),learned.revision,SystemClock.elapsedRealtime())
                refreshEditor?.invoke()
                saveNoiseSettings()
            }.onFailure { tell("Threshold learning: ${it.message}",false) } }.show()
    }
    fun automaticDispatchAllowed()=mode=="Automatic"
    private var mode = "Off"
    private var detail = "No samples yet"
    private var generation = 0
    private var runtime: TextView? = null
    private var automaticButton: Button? = null
    private var graph: NoiseGraphView? = null
    private val recentSamples = ArrayDeque<Pair<Long,Double>>()
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = object: Runnable {
        override fun run() {
            update()
            uiHandler.postDelayed(this,1000)
        }
    }
    fun resume() {
        AutomaticNoiseService.foregroundSince=SystemClock.elapsedRealtime()
        AutomaticNoiseService.running?.learner?.let { learner=it }
        uiHandler.removeCallbacks(refresh);uiHandler.post(refresh)
    }
    fun pause() {
        AutomaticNoiseService.foregroundSince=null
        uiHandler.removeCallbacks(refresh)
        stopLocal()
    }
    private var calibrationRuntime: TextView? = null
    private var refreshCalibration: (() -> Unit)? = null
    private fun automaticSettings() = settings.copy(sleepProgram=settings.sleepProgram ?:
        programs().firstOrNull { isTherapyProgramName(it.name) }?.slot)
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
        } // Calibration proof is optional; preserve it for measured threshold review.
        savedProof = proof
    }
    private fun calibrated(config: NoiseSettings = settings) = runCatching {
        proof?.matches(config,sampler.sourceIdentity()) == true
    }.getOrDefault(false)
    private fun calibrationText(): String = if(calibrating) {
        "Calibration: " + calibration.levels.mapIndexed { i,row -> "level ${i+1}: ${row.size}/3" }.joinToString(" · ")
    } else if(calibrated()) "Measured calibration matches this device and saved capture settings." else "Calibration optional; Automatic uses saved thresholds."

    fun detachEditor() {
        speechAutomatic.detach()
        // Preserve invalid text by refusing navigation until it is fixed or discarded.
        readEditor?.let { draft = it() }
        readEditor = null; refreshEditor = null; runtime = null; automaticButton = null; graph = null; calibrationRuntime = null; refreshCalibration = null
    }
    fun stop() {
        SpeechAutomaticService.cancel()
        AutomaticNoiseService.stop(activity)
        stopLocal()
    }
    private fun stopLocal() {
        generation++; sampler.stop(); quietUntil = 0L; mode = "Off"; observation = NoiseObservation(); automatic = NoiseAutomaticState(); update()
    }
    fun fittingChanged() { learner=null;stop(); stored = false; loadError = "Program fitting changed; review and save noise settings" }
    private fun label(slot: Int?) = if(slot == null) "Unused" else programs().find { it.slot == slot }?.let { "${it.name} (program $slot)" } ?: "Unavailable program $slot"
    private fun update() {
        runtime?.apply {
            val next=summary();if(text.toString()!=next) text=next
            val description=next;if(contentDescription?.toString()!=description) contentDescription=description
        }
        automaticButton?.apply {
            val on=SpeechAutomaticService.active || AutomaticNoiseService.active || mode=="Automatic"
            val next=if(on) "On" else "Off"
            if(text.toString()!=next) text=next
            contentDescription=if(on) "Automatic On. Tap to turn Off" else "Automatic Off. Tap to turn On"
            val role=if(on) "noiseOn" else "noiseOff"
            if(tag!=role) PhoneStyle.action(this,role)
        }
        AutomaticNoiseService.running?.samples?.takeIf { it.isNotEmpty() }?.let { samples ->
            recentSamples.clear();recentSamples.addAll(samples)
        }
        SpeechAutomaticService.running?.soundLevels?.takeIf { it.isNotEmpty() }?.let { samples -> recentSamples.clear();recentSamples.addAll(samples) }
        graph?.apply { boundaries=settings.boundaries;samples=recentSamples.toList() }
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
    private fun summary(): String {
        SpeechAutomaticService.running?.let { return it.summary() }
        if(SpeechAutomaticService.active) return "Speech-aware Automatic starting…"
        AutomaticNoiseService.running?.let { return it.summary() }
        if(AutomaticNoiseService.active) return "Automatic starting…"
        if(mode=="Off") speechAutomatic.offStatus()?.let { return "Automatic Off\n$it" }
        return buildString {
        append(when {
            mode == "Automatic" -> "Automatic is On"
            mode != "Off" -> "$mode is running"
            loadError != null -> "Needs review · $loadError"
            !stored -> "Choose your programs first, then tap Save."
            else -> "Automatic is Off. Ready when you are."
        })

        observation.target?.let { append("\n${label(it)} · ${observation.count}/${settings.confirmations}") }
        if(detail != "No samples yet") append("\n$detail")
        if(AutomaticNoiseService.lastStatus.isNotEmpty()) append("\n${AutomaticNoiseService.lastStatus}")
        }
    }
    fun status(): String {
        SpeechAutomaticService.running?.let { return it.summary() }
        if(SpeechAutomaticService.active) return "Speech-aware Automatic starting…"
        AutomaticNoiseService.running?.let { return it.summary(detailed=true)+"\n"+learningStatus() }
        val age = observation.lastSampleMillis?.let { (SystemClock.elapsedRealtime()-it)/1000 }
        return "Noise: $mode • Source: this device's built-in microphone\n$detail\n" +
            (measurement?.let { "RMS index: ${"%.1f".format(java.util.Locale.US,it.rmsDbfs)} dBFS (relative digital level, not sound-pressure dB)\n" } ?: "") +
            (observation.level?.let { "Noise level $it → ${label(observation.target)}; confirmations ${observation.count}/${settings.confirmations}; age ${age}s\n" } ?: "") +
            (observation.proposed?.takeIf { mode == "Observe" || mode == "Automatic preview" }?.let { "Would select ${label(it)}. No command sent.\n" } ?: "") +
            calibrationText()+"\n${learningStatus()}\nSpeech-aware Automatic uses its fixed masking model and chosen programs. Level-only tools retain your saved thresholds. Preview and calibration require an open, unlocked app. Physical aid reception is unconfirmed."
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
        group("Automatic noise") {
            line("HeySig chooses your program from how much other sound masks speech. Quiet or clear speech returns to Everyday after repeated checks. Unsure means keep the current program.")
            automaticButton=button(if(SpeechAutomaticService.active || AutomaticNoiseService.active || mode=="Automatic") "On" else "Off") { handle(if(SpeechAutomaticService.active || AutomaticNoiseService.active || mode=="Automatic") NoiseCommand.Stop else NoiseCommand.Automatic) }
            openSoundType?.let { open -> button("Check speech and noise",open) }
            runtime = line(summary()).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_NONE; contentDescription=summary() }
            graph=NoiseGraphView(activity).also { target.addView(it,LinearLayout.LayoutParams(-1,PhoneStyle.dp(activity,(180*activity.resources.configuration.fontScale).toInt()))) }
            line("Sound around you: the line rises when it gets louder and falls when it gets quieter. Each dot is one short sound check.")
            line("You can turn the screen off or use another app. Tap On here to stop. How to use explains how to stop from another screen.")
            line("When it gets quieter, HeySig waits briefly before changing back. The wait appears above.")
        }
        group("Guided calibration") {
            calibrationRuntime = line(calibrationProgress())
            line("Optional measurements for the older level-only tools. Speech-aware Automatic does not use calibration. Your saved measurements stay unchanged until you review and save.")
            button("Start new calibration") {
                android.app.AlertDialog.Builder(activity).setTitle("Measure six listening levels?")
                    .setMessage("This replaces only unfinished calibration samples. Saved settings stay unchanged until you apply and save. Use naturally occurring, comfortable environments; if six levels cannot be separated, keep using Observe.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Start") { _,_ ->
                        runCatching {
                            beginCalibration()
                        }.onFailure { tell("Calibration: ${it.message}",false) }
                    }.show()
            }
            button("Capture next calibration sample") {
                runCatching {
                    captureCalibration()
                }.onFailure { tell("Calibration: ${it.message}",false) }
            }
            button("Review calibration thresholds") {
                runCatching {
                    val review=reviewCalibration()
                    android.app.AlertDialog.Builder(activity).setTitle("Use measured thresholds?")
                        .setMessage(review)
                        .setNegativeButton("Keep measuring",null).setPositiveButton("Apply to draft") { _,_ ->
                            runCatching { applyCalibration() }.onFailure { tell("Calibration: ${it.message}",false) }
                        }.show()
                }.onFailure { tell("Calibration: ${it.message}",false) }
            }
            button("Cancel calibration") { cancelCalibration() }
        }
        group("Your listening levels") {
            line("Choose the hearing-aid program you want for quieter and louder places. You can use the same program in more than one box. Leaving a box unused means no change at that sound level. Tap Save noise settings when you are done.")
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
        group("Protect Therapy") {
            line("If you use Therapy, choose its program here. Automatic will not switch into or out of Therapy. After Therapy, choose your usual daytime program before turning Automatic on again.")
            sleepPicker = Spinner(activity).apply {
                contentDescription = "Protected Therapy program"
                adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,choices.map { if(it == null) "No Therapy program" else label(it) })
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
            if((mode != "Off" || AutomaticNoiseService.active) && runCatching { readEditor?.invoke() != settings }.getOrDefault(true)) {
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
                "Noise" -> title == "Automatic noise"
                "NoiseCalibration" -> title == "Guided calibration" || buttonText in listOf("Save noise settings","Discard noise changes")
                "NoiseSetup" -> title == "Your listening levels" || title?.startsWith("Level ") == true || title == "Protect Therapy" || buttonText in listOf("Save noise settings","Discard noise changes")
                "NoiseMeasure" -> child === advanced || buttonText in listOf("Save noise settings","Discard noise changes")
                else -> false
            }
            // Headers/status outside this adapter are owned by HomeActivity.
            if(i >= firstView) child.visibility = if(visible) android.view.View.VISIBLE else android.view.View.GONE
        }
        if(page == "Noise") {
            button("How to use") {
                android.app.AlertDialog.Builder(activity).setTitle("Using Automatic")
                    .setMessage("First time: on Home, choose your usual daytime program.\n\n1. Tap Choose programs. Pick Everyday, Noisy and Very Noisy from your own program names. Tap Save speech program choices before going back. Turning Automatic on here also saves your choices.\n\n2. Tap the red Off button to turn Automatic on. The button turns green and says On. The first time, follow the microphone and notification prompts.\n\n3. Keep the phone where your hearing aids can hear its program-change tones. You can lock the screen or use another app.\n\nWhen the room gets quieter, HeySig waits before changing back. With the usual settings, allow about a minute and a half after the last change. The screen shows the wait.\n\nTo stop, tap the green On button. If you are on another screen, swipe down from the top of the phone. Find the HeySig message and tap Off.\n\nAutomatic stops after 8 hours. Choosing a program yourself also stops it. Turn it on again when you want. Therapy stays under your control.\n\nNo recordings, calibration or learning period is needed. The model estimates speech masking, not just loudness. It can make mistakes; unsure means keep the current program.")
                    .setPositiveButton("Got it",null).show()
            }
            button("Choose programs") { navigate("NoiseSetup") }
            button("Compare saved programs") { navigate("NoiseComparison") }
            button("Optional sound setup") { navigate("NoiseCalibration") }
        }
        if(page == "NoiseSetup") {
            speechAutomatic.render(content,firstView)
            button("Optional: check sound levels") { navigate("NoiseCalibration") }
            button("Advanced measurement settings") { navigate("NoiseMeasure") }
        }
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
        update()
        if(page == "NoiseDetails") {
            line("Try it without changing your hearing aids")
            runtime=line(summary()).apply { accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_NONE }
            line("This shows which program HeySig would choose. It does not change your hearing aids. Save your program choices first and choose a daytime program on Home. Keep this screen open and the phone unlocked during the try-out.")
            button("Start try-out") { runCatching { start(true,preview=true) }.onFailure { tell("Noise: ${it.message}",false) } }
            button("Stop try-out") { stop();tell("Preview stopped. No command sent.",false) }
            button("Review program choices") { navigate("NoiseSetup") }
            button("Review calibration") { navigate("NoiseCalibration") }
            line("Automatic also learns from valid sound-level measurements already being taken. Up to 240 numbers stay in memory only, with no extra capture or upload. Nothing changes unless you review and approve a suggestion; closing the process loses the measurements.")
            button("Review learned thresholds") { runCatching { reviewLearnedThresholds() }.onFailure { tell("Threshold learning: ${it.message}",false) } }
            button("Discard learned measurements") { stop();learner=null;update();tell("Learned measurements discarded. Saved thresholds unchanged.",false) }
            val details=TextView(activity).apply { text=status();PhoneStyle.text(this,16f,color=PhoneStyle.secondary);visibility=android.view.View.GONE }
            button("Extra details") { details.text=status();details.visibility=if(details.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
            content.addView(details)
            automaticButton=button(if(SpeechAutomaticService.active || AutomaticNoiseService.active) "On" else "Off") { handle(if(SpeechAutomaticService.active || AutomaticNoiseService.active) NoiseCommand.Stop else NoiseCommand.Automatic) }
            update()

        }

    }
    private fun beginCalibration() {
        readEditor?.let { draft=it() };draft.validate(programs().map { it.slot }.toSet())
        val source=sampler.sourceIdentity()
        stop();calibration=NoiseCalibration();calibrating=true;calibrationReview=null;calibrationStartUntil=0
        calibrationSource=source;calibrationCapture=draft.captureSeconds
        detail="Prepare level 1, the quietest environment. Capture three samples before moving to level 2.";update()
    }
    private fun captureCalibration() {
        check(calibrating) { "Start calibration first" }
        check(calibration.levels.any { it.size<3 }) { "All samples captured; review and apply" }
        calibrationReview=null
        start(false,calibrationSample=true)
    }
    private fun reviewCalibration(): String {
        check(calibrating && mode=="Off") { "Finish or stop the current sample first" }
        readEditor?.let { draft=it() }
        check(calibrationCapture==draft.captureSeconds && calibrationSource==sampler.sourceIdentity()) { "Capture settings or microphone changed; start calibration again" }
        val profile=NoiseCalibrationProfile(calibration,checkNotNull(calibrationSource),calibrationCapture,draft.margin)
        val boundaries=calibration.thresholds(draft.margin)
        calibrationReview=NoiseCalibrationReview(draft,profile,fittingSignature(),SystemClock.elapsedRealtime())
        return calibration.levels.mapIndexed { i,row -> "Level ${i+1}: ${row.joinToString { "%.1f".format(java.util.Locale.US,it) }} dBFS" }.joinToString(". ")+
            ". Thresholds: ${boundaries.joinToString { "%.1f".format(java.util.Locale.US,it) }}. Say apply noise calibration within one minute, then save noise settings after reviewing assignments. No command is sent."
    }
    private fun applyCalibration() {
        val review=checkNotNull(calibrationReview) { "Say review noise calibration first" }
        calibrationReview=null
        check(calibrating && mode=="Off") { "Calibration is no longer ready" }
        readEditor?.let { draft=it() }
        check(calibrationSource==review.profile.source && calibrationCapture==review.profile.captureSeconds) { "Capture settings changed; review again" }
        draft=review.apply(draft,calibration,sampler.sourceIdentity(),fittingSignature(),SystemClock.elapsedRealtime());proof=review.profile
        calibrating=false;refreshEditor?.invoke();detail="Measured thresholds applied to draft. Review program assignments, then say save noise settings. No command sent.";update()
    }
    private fun cancelCalibration() {
        stop();calibrating=false;calibration=NoiseCalibration();calibrationReview=null;calibrationStartUntil=0
        detail="Unfinished calibration cleared. Saved noise settings retained.";update()
    }
    private fun saveNoiseSettings() {
        draft.validate(programs().map { it.slot }.toSet())
        val json = JSONObject().put("version",1).put("fitting",fittingSignature()).put("programs",JSONArray(draft.programs.map { it ?: JSONObject.NULL }))
            .put("boundaries",JSONArray(draft.boundaries)).put("margin",draft.margin).put("confirmations",draft.confirmations)
            .put("cooldown",draft.cooldownSeconds).put("capture",draft.captureSeconds).put("wait",draft.waitSeconds).put("freshness",draft.freshnessSeconds).put("sleepProgram",draft.sleepProgram ?: JSONObject.NULL)
        val proofJson = proof?.takeIf { calibrated(draft) }?.let { p ->
            JSONObject().put("version",1).put("source",p.source).put("capture",p.captureSeconds).put("margin",p.margin)
                .put("levels",JSONArray(p.samples.levels.map { JSONArray(it) })).toString()
        }
        check(prefs.edit().putString("settings",json.toString()).putString("calibration",proofJson).commit()) { "Noise settings could not be saved; draft retained" }
        learner=null;settings = draft; if(proofJson == null) proof = null; savedProof = proof; reviewedFitting = fittingSignature(); stored = true; loadError = null; stop(); tell("Your program choices are saved. Automatic is Off. Turn it on when you are ready.",true)
    }
    fun handle(command: NoiseCommand) {
        try {
            if(command !in listOf(NoiseCommand.Discard,NoiseCommand.Stop,NoiseCommand.CalibrationCancel)) readEditor?.let { draft = it() }
            when(command) {
                NoiseCommand.Open -> tell("Assign and save a program for each noise level. Say Automatic on to start noise-level switching, Automatic off to stop. Calibration is optional.",true)
                NoiseCommand.Read -> tell(status()+"\nDraft assignments: "+draft.programs.mapIndexed { i,slot -> "${i+1}: ${label(slot)}" }.joinToString(". ")+"\nDraft thresholds: "+draft.boundaries.joinToString()+". Margin ${draft.margin}. Confirmations ${draft.confirmations}. Cooldown ${draft.cooldownSeconds}. Capture ${draft.captureSeconds}. Wait ${draft.waitSeconds}. Freshness ${draft.freshnessSeconds}.",true)
                NoiseCommand.Stop -> { stop();calibrationStartUntil=0;calibrationReview=null;tell("Automatic off. Noise capture stopped. Already emitted tones cannot be undone.",true) }
                NoiseCommand.Preview -> start(true,preview=true)
                NoiseCommand.CalibrationStart -> {
                    stop();calibrationReview=null
                    calibrationStartUntil=SystemClock.elapsedRealtime()+60_000
                    tell("Noise calibration: this replaces unfinished samples only. Saved settings remain unchanged. Use comfortable everyday environments. Say confirm new noise calibration within one minute, or cancel noise calibration.",true)
                }
                NoiseCommand.CalibrationConfirmStart -> {
                    val deadline=calibrationStartUntil;calibrationStartUntil=0
                    check(deadline>0 && SystemClock.elapsedRealtime()<=deadline) { "Say start noise calibration first; confirmation lasts one minute" }
                    beginCalibration();tell("Noise calibration: $detail Say capture noise calibration sample when ready.",true)
                }
                NoiseCommand.CalibrationSample -> captureCalibration()
                NoiseCommand.CalibrationStatus -> tell("Calibration progress: ${calibrationProgress()}. $detail",true)
                NoiseCommand.CalibrationReview -> {
                    reviewCalibration()
                    val thresholds=calibration.thresholds(draft.margin).joinToString { "%.1f".format(java.util.Locale.US,it) }
                    tell("Noise calibration: 18 samples complete. Thresholds: $thresholds. Say apply noise calibration, then save noise settings after review.",true)
                }
                NoiseCommand.CalibrationApply -> { applyCalibration();tell(detail,true) }
                NoiseCommand.CalibrationCancel -> { cancelCalibration();tell(detail,true) }
                NoiseCommand.Automatic -> {
                    speechAutomatic.start()
                }
                NoiseCommand.Discard -> { stop();calibrationStartUntil=0;calibrationReview=null; draft = settings; proof = savedProof; refreshEditor?.invoke(); update(); tell("Noise draft discarded",true) }
                NoiseCommand.Save -> saveNoiseSettings()
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
        SpeechAutomaticService.cancel()
        com.heysig.app.voice.HandsFreeService.running?.control(com.heysig.voice.HandsFreeControl.OFF)
        readEditor?.let { draft = it() }
        check(mode == "Off" && !AutomaticNoiseService.active) { "Noise capture already active; turn Off first" }
        if(preview || automaticMode) {
            check(lastRequested() != null) { "On Home, choose your usual daytime program first" }
            val runtimeSafety=safety()
            val guards = runtimeSafety.copy(manualOverride=if(automaticMode) false else runtimeSafety.manualOverride,
                sleeping=(!automaticMode && runtimeSafety.sleeping) || (automaticSettings().sleepProgram != null && lastRequested() == automaticSettings().sleepProgram))
            check(guards.blocked() == null) { guards.blocked() ?: "Not ready" }
        }
        if(calibrationSample) {
            draft.validate(programs().map { it.slot }.toSet())
            check(calibrationSource == sampler.sourceIdentity() && calibrationCapture == draft.captureSeconds) { "Microphone or capture settings changed; start calibration again" }
        }
        if(repeat) {
            check(stored && draft == settings && reviewedFitting == fittingSignature()) { "Choose your programs and tap Save noise settings first" }
            settings.validate(programs().map { it.slot }.toSet())
            check(settings.programs.any { it != null }) { "Choose at least one program and save your choices first" }
        }
        if(!prefs.getBoolean("microphoneDisclosureV2",false)) {
            android.app.AlertDialog.Builder(activity).setTitle("Microphone for noise measurement")
                .setMessage("HeySig uses this phone's microphone to check the sound around you. Each check is short. Sound is processed in memory and discarded. Noise measurement does not save or upload recordings.\n\nAutomatic can send program-change tones to your hearing aids. The try-out option only shows what it would choose; it does not change your aids.\n\nOptional learning keeps up to 240 sound-level numbers in memory while the app is open. It never changes saved settings without your approval. Optional sound setup saves 18 level numbers and this phone's microphone settings locally when you tap Save noise settings.\n\nVoice commands are separate. Your installed speech service may use the internet. You can stop noise checks at any time.")
                .setNegativeButton("Not now",null)
                .setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("microphoneDisclosureV2",true).commit()) {
                        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
                            tell("After allowing microphone access, request Automatic on or your measurement action again.",false)
                        } else try { start(repeat,calibrationSample,preview,automaticMode) } catch(e:Exception) { tell("Noise: ${e.message}",false) }
                    } else tell("Could not save microphone choice; capture remains off",false)
                }.show()
            return
        }
        check(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),402)
            "Allow microphone access, then request Automatic on or your measurement action again"
        }
        if(automaticMode && !prefs.getBoolean("automaticServiceDisclosureV1",false)) {
            android.app.AlertDialog.Builder(activity).setTitle("Automatic with the screen off")
                .setMessage("Automatic can keep checking sound when you use another app or turn the screen off. It uses your saved choices to change hearing-aid programs, including changing back for quieter places.\n\nIt runs for up to 8 hours. A phone notification shows that it is on and has an Off button. It uses more battery while running.\n\nEach microphone check is short. Sound is processed in memory and discarded. Noise measurement does not save or upload recordings. Recent sound-level numbers stay in memory for the graph. Learning uses checks taken while HeySig is visible and still needs your approval before saving any changes.\n\nChoosing a program yourself, Therapy, microphone problems or stopping the app ends Automatic. It will not restart by itself.")
                .setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("automaticServiceDisclosureV1",true).commit()) handle(NoiseCommand.Automatic)
                }.show()
            return
        }
        if(automaticMode && android.os.Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),403)
            tell("Allow notifications so Automatic has a visible Off control, then tap Off to turn it On.",false)
            return
        }
        val captureSettings = if(calibrationSample) draft else if(stored) settings else NoiseSettings()
        val source = sampler.sourceIdentity()
        if(automaticMode) syncLearning()
        // Explicit On resumes after a manual choice only once capture can begin.
        // Permission/disclosure exits above leave the existing override untouched.
        val rollback = if(automaticMode) checkNotNull(prepareAutomatic) { "Automatic requires the visible phone adapter" }.invoke() else null
        try {
            if(automaticMode) {
                val guards=safety()
                check(guards.blocked()==null) { guards.blocked() ?: "Not ready" }
            }
            if(automaticMode) {
                AutomaticNoiseService.launch(activity,automaticSettings(),fittingSignature(),learner)
                update();tell("Automatic On. It can continue with the screen off; use Off here or in the notification to stop.",false)
                return
            }
            val myGeneration = ++generation
            automatic = NoiseAutomaticState(lastSuccessMillis=if(preview) null else lastAutomaticSuccessMillis ?: SystemClock.elapsedRealtime()); previewLast = lastRequested()
            observation = NoiseObservation(); mode = when { calibrationSample -> "Calibration sample"; preview -> "Automatic preview"; automaticMode -> "Automatic"; repeat -> "Observe"; else -> "Sample" }; detail = "Waiting ${captureSettings.waitSeconds.coerceAtLeast(10)} seconds before capture. "+if(automaticMode) "Automatic may send confirmed program requests." else "No commands will be sent."
            val started = sampler.start(captureSettings,repeat) { value,error,capturedAt,paused -> activity.runOnUiThread {
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
                    if(value!=null && value.flags.isEmpty()) {
                        recentSamples.addLast(capturedAt to value.rmsDbfs)
                        while(recentSamples.size>120) recentSamples.removeFirst()
                    }
                    if(source != runCatching { sampler.sourceIdentity() }.getOrNull()) {
                        stop(); detail = "Microphone source changed; restart calibration or observation"; update(); return@runOnUiThread
                    }
                    if(value == null || value.flags.isNotEmpty()) {
                        if(preview || automaticMode) {
                            automatic = automatic.copy(observation=NoiseObservation())
                        }
                        observation = NoiseObservation()
                        val settling = automaticMode && quietUntil > SystemClock.elapsedRealtime()
                        val retryPause = repeat && paused
                        if(value == null && !settling && !retryPause) {
                            stop()
                        }
                        detail = if(settling) "Waiting for command completion and microphone settling"
                            else if(retryPause) "Capture paused for audio priority; will retry with fresh confirmations"
                            else error ?: "Sample rejected: ${value!!.flags.joinToString()}"
                    } else if(calibrationSample) {
                        val level = calibration.levels.indexOfFirst { it.size < 3 }+1
                        calibration = calibration.add(level,value)
                        val next = calibration.levels.indexOfFirst { it.size < 3 }+1
                        detail = if(next == 0) "All 18 samples captured. Review calibration thresholds." else
                            "Captured level $level. Prepare level $next (${calibration.levels[next-1].size}/3 captured), then request the next sample."
                    } else if(preview || automaticMode) {
                        if(automaticMode && draft==settings && safety().blocked()==null && automatic.pendingTarget==null) {
                            syncLearning().add(value.rmsDbfs,capturedAt)
                        }
                        processAutomatic(value.rmsDbfs,preview,myGeneration,capturedAt)
                    } else if(repeat) {
                        observation = observeNoise(observation,value.rmsDbfs,capturedAt,settings)
                        detail = "Observing experimental relative RMS index; no commands sent"
                    } else detail = "Sample complete. Review this device's index before choosing thresholds."
                    if(!repeat) mode = "Off"
                    update()
                }
            } }
            if(!started) { stop(); error("The previous measurement is still stopping. Wait a moment, then try again") }
        } catch(error: Exception) {
            stop();rollback?.invoke();throw error
        }
        update(); tell(if(preview) "Automatic preview started. No commands are sent." else if(automaticMode) "Automatic started; keep this app open." else if(calibrationSample) "Calibration sample requested. Stay quiet for ${captureSettings.waitSeconds.coerceAtLeast(10)+captureSettings.captureSeconds} seconds, then say noise calibration status to hear the result." else if(repeat) "Observe started. Proposals appear on Noise; no commands are sent." else "Sampling microphone. Result will appear on Noise.",calibrationSample)
    }
    private fun processAutomatic(index:Double, preview:Boolean, session:Int, capturedAt:Long) {
        val guards = safety()
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
            check(automaticDispatchAllowed()) { "Automatic is off" }
            quietUntil = Long.MAX_VALUE
            sendProgram(target) { success -> activity.runOnUiThread {
                val completedAt=SystemClock.elapsedRealtime()
                if(success) lastAutomaticSuccessMillis=completedAt
                if(generation == session) {
                    automatic = completeNoise(automatic,token,success,completedAt)
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
