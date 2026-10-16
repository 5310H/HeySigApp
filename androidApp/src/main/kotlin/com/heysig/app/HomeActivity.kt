package com.heysig.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.*
import com.heysig.control.*
import com.heysig.research.*
import com.heysig.noise.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** First native manual app slice. Does not start listeners, schedules or automatic playback. */
class HomeActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("heysig-core-v1", MODE_PRIVATE) }
    private val player by lazy { SpeakerPlayback(this) }
    private var fitting: Fitting? = null
    @Volatile private var requested = RequestedState()
    private var saved: RequestedState? = null
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private var resumed = false
    private var pendingVoice = false
    private var pendingText: String? = null
    private var pendingRestore: List<ManualRequest>? = null
    private var pendingProgram: Int? = null
    private var confirmationDeadline = 0L
    private var lastHeard = ""
    private lateinit var heardStatus: TextView
    private var repeatRequest: ManualRequest? = null
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private var page = "Home"
    private var setupChanged: (() -> Boolean)? = null
    private var saveSetupAction: (() -> Unit)? = null
    private var message = "Ready. No command is sent at startup."
    private var watchBridge: WatchBridge? = null
    private var watchToggle: CheckBox? = null
    private var remoteReply: ((JSONObject) -> Unit)? = null
    private var commandOrigin: String? = null
    private var confirmationOrigin: String? = null
    private val remotePrefs by lazy { getSharedPreferences("heysig-watch-results",MODE_PRIVATE) }
    private fun remoteResult(phase: String, text: String) = JSONObject().put("phase",phase).put("text",text)
    private val profile = AcousticProfile(44100,15360,512,0.0625,1.2,true)
    private val noise by lazy { NoiseFeature(this,{ fitting?.programs.orEmpty() },
        { resumed && !busy.get() && !pendingVoice && tts?.isSpeaking != true },{ text,speak -> tell(text,speak) }) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        load()
        tts = TextToSpeech(this) { code ->
            speechReady = code == TextToSpeech.SUCCESS
            if(speechReady) tts?.language = Locale.US
        }
        show("Home")
    }
    override fun onResume() { super.onResume(); resumed = true; watchToggle?.isChecked = watchBridge != null }
    override fun onPostResume() {
        super.onPostResume()
        pendingText?.let { pendingText = null; route(it) }
    }
    override fun onPause() { noise.stop(); watchBridge?.close(); watchBridge = null; resumed = false; pendingVoice = false; cancelled.set(true); tts?.stop(); super.onPause() }
    override fun onDestroy() { noise.stop(); cancelled.set(true); tts?.shutdown(); super.onDestroy() }

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
                message = "Previous command was interrupted. Aid state and requested values are uncertain. No replay."
            }
        } catch(e: Exception) {
            fitting = null; requested = RequestedState(); saved = null
            message = "Stored setup needs review: ${e.message}. No commands enabled."
        }
    }
    private fun encode(state: RequestedState): String = JSONObject().apply {
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
    private fun line(text: String) = TextView(this).also { it.text = text; it.textSize = 17f; it.setPadding(0,8,0,8); content.addView(it) }
    private fun button(text: String, enabled: Boolean = true, action: () -> Unit) = Button(this).also {
        it.text = text; it.isEnabled = enabled
        it.setOnClickListener { action() }; content.addView(it)
    }
    private fun navigate(target: String) {
        if(page == "Setup" && target != "Setup" && setupChanged?.invoke() == true) {
            android.app.AlertDialog.Builder(this).setTitle("Setup changes are not saved")
                .setMessage("Save the reviewed setup before leaving, or discard the changes.")
                .setPositiveButton("Save setup") { _,_ -> saveSetupAction?.invoke() }
                .setNegativeButton("Discard") { _,_ -> show(target) }
                .setNeutralButton("Keep editing",null).show()
        } else show(target)
    }
    private fun show(destination: String = page) {
        try { noise.detachEditor() } catch(e:Exception) { tell("Noise draft: ${e.message}. Correct the field or discard noise changes."); return }
        page = destination
        setupChanged = null; saveSetupAction = null
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        line("HeySig • $page").textSize = 25f
        line("Hearing-aid controls • experimental noise observation")
        val nav = LinearLayout(this)
        listOf("Home","Setup","Saved","Voice","Noise").forEach { target ->
            nav.addView(Button(this).apply { text = target; isEnabled = !busy.get(); setOnClickListener { navigate(target) } }, LinearLayout.LayoutParams(0,-2,1f))
        }; content.addView(nav)
        status = line(message)
        heardStatus = line(if(lastHeard.isEmpty()) "" else "Speech heard: $lastHeard")
        heardStatus.visibility = if(lastHeard.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        if(page == "Home") {
            val remote = CheckBox(this).apply {
                text = "Watch Wi-Fi (development)"; isChecked = watchBridge != null
                setOnCheckedChangeListener { box, enabled ->
                    if(enabled) {
                        if(getSharedPreferences("heysig-watch-dev",MODE_PRIVATE).getString("token",null) == null) {
                            box.isChecked = false; tell("Set up the Chromebook watch relay first")
                        } else try { watchBridge = WatchBridge(this@HomeActivity) { request, reply -> runOnUiThread { try { receiveWatch(request,reply) } catch(e:Exception) { reply(remoteResult("FAILED","Watch request could not be processed")) } } }; tell("Watch Wi-Fi ready while HeySig stays open") }
                        catch(e:Exception) { box.isChecked = false; tell("Watch bridge could not start: ${e.message}") }
                    } else { watchBridge?.close(); watchBridge = null }
                }
            }; watchToggle = remote; content.addView(remote)
        }
        button("Speak command") { beginVoice() }
        button("Stop") { noise.stop(); tts?.stop(); pendingProgram = null; pendingRestore = null; pendingVoice = false; cancelled.set(true); tell("Stop requested. Already emitted tones cannot be undone.") }
        if(page == "Setup") button("Save reviewed setup",!busy.get()) { saveSetupAction?.invoke() }
        when(page) {
            "Home" -> home()
            "Setup" -> setup()
            "Saved" -> snapshot()
            "Voice" -> voicePage()
            "Noise" -> noise.render(content)
        }
        val scroll = ScrollView(this).apply { addView(content, ViewGroup.LayoutParams(-1,-2)) }
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets
        }
        setContentView(scroll)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun programLabel(slot: Int?) = fitting?.programs?.find { it.slot == slot }?.let { "${it.name}, slot ${it.slot}" } ?: "unknown"
    private fun stateText(state: RequestedState) = "Program: ${programLabel(state.program)}\n" +
        LevelControl.entries.joinToString("\n") { "$it: ${state.levels[it]?.toString() ?: "unknown"}" }
    private fun home() {
        line(if(fitting == null) "Save your aid setup before sending commands." else "LAST SENT\n${stateText(requested)}")
        fitting?.programs?.forEach { program -> button("${program.slot}. ${program.name}",!busy.get()) { dispatch(ManualRequest.SelectProgram(program.slot)) } }
        for(control in LevelControl.entries) {
            line(when(control) { LevelControl.SHARPNESS -> "Sound Sharpness (balance)"; else -> control.name.lowercase().replaceFirstChar { it.uppercase() } })
            val values = control.range.toList()
            val picker = Spinner(this).apply {
                adapter = ArrayAdapter(this@HomeActivity,android.R.layout.simple_spinner_dropdown_item,values.map { "$it" })
                setSelection(values.indexOf(requested.levels[control] ?: if(control == LevelControl.VOLUME) 8 else 0)); isEnabled = !busy.get()
            }; content.addView(picker)
            val supported = control != LevelControl.TINNITUS || fitting?.programs?.find { it.slot == requested.program }?.tinnitus == TinnitusCapability.SUPPORTED
            button("Send ${control.name.lowercase()}", !busy.get() && fitting != null && requested.program != null && supported) {
                dispatch(ManualRequest.SetLevel(control,values[picker.selectedItemPosition]))
            }
            if(control == LevelControl.TINNITUS) {
                val program = fitting?.programs?.find { it.slot == requested.program }
                line(when(program?.tinnitus) {
                    TinnitusCapability.SUPPORTED -> "${program.name}: tinnitus support confirmed in saved setup."
                    TinnitusCapability.UNSUPPORTED -> "${program.name}: tinnitus unavailable in saved setup."
                    TinnitusCapability.UNKNOWN -> "${program.name}: tinnitus support unknown. Confirm and Save in Setup."
                    null -> "Select a program before setting tinnitus."
                })
            }
        }
        repeatRequest?.let { request -> button("Send last target again",!busy.get()) { dispatch(request,true) } }
        button("Check left battery",!busy.get() && fitting != null) { dispatch(ManualRequest.Battery(true,false)) }
        button("Check both batteries",!busy.get() && fitting != null) { dispatch(ManualRequest.Battery(true,true)) }
        button("Check right battery",!busy.get() && fitting != null) { dispatch(ManualRequest.Battery(false,true)) }
        button("Check device audio",!busy.get()) { try { player.inspect(); tell("Speaker audio checks passed. No command sent.",true) } catch(e:Exception) { tell("Blocked: ${e.message}",true) } }
        button("Mark aid settings unknown",!busy.get()) {
            requested = RequestedState()
            if(!prefs.edit().putString("requested",encode(requested)).commit()) message = "Could not save unknown state" else message = "Requested state cleared; saved restoration record retained."
            show()
        }
    }
    private fun setup() {
        line("Review and save. For your tested fitting, addresses were 12. This is not a universal default. Blank program names disable those slots. Tinnitus support must be confirmed per program.")
        fun field(label: String, value: String): EditText { line(label); return EditText(this).apply { setSingleLine(); setText(value); content.addView(this) } }
        val control = field("Program/level address",fitting?.controlAddress?.toString() ?: "12")
        val left = field("Left battery address",fitting?.leftAddress?.toString() ?: "12")
        val right = field("Right battery address",fitting?.rightAddress?.toString() ?: "12")
        val defaults = listOf("Universal","Sleep","Noisy","Outdoor","TV","Very Noisy")
        val names = mutableListOf<EditText>(); val capabilities = mutableListOf<Spinner>()
        for(slot in 1..6) {
            val existing = fitting?.programs?.find { it.slot == slot }
            names.add(field("Program $slot",existing?.name ?: if(fitting == null) defaults[slot-1] else ""))
            line("Tinnitus availability, slot $slot")
            capabilities.add(Spinner(this).apply {
                adapter = ArrayAdapter(this@HomeActivity,android.R.layout.simple_spinner_dropdown_item,TinnitusCapability.entries.map { it.name.lowercase() })
                setSelection((existing?.tinnitus ?: TinnitusCapability.UNKNOWN).ordinal); content.addView(this)
            })
        }
        line("Validated waveform: 44100 Hz • 15360 Hz center • 512 Hz spacing • 0.0625 s bit • divisor 1.2 • address-XOR on. Other profiles remain in the research prototype.")
        setupChanged = {
            val existing = fitting
            existing == null || control.text.toString().trim() != existing.controlAddress.toString() ||
                left.text.toString().trim() != existing.leftAddress.toString() || right.text.toString().trim() != existing.rightAddress.toString() ||
                (1..6).any { slot ->
                    val program = existing.programs.find { it.slot == slot }
                    names[slot-1].text.toString().trim() != (program?.name ?: "") ||
                        (program != null && capabilities[slot-1].selectedItemPosition != program.tinnitus.ordinal)
                }
        }
        val saveButton = button("Save reviewed setup",!busy.get()) {
            try {
                val programs = (1..6).mapNotNull { slot -> names[slot-1].text.toString().trim().takeIf { it.isNotEmpty() }?.let { Program(slot,it,TinnitusCapability.entries[capabilities[slot-1].selectedItemPosition]) } }
                val next = Fitting(Integer.decode(control.text.toString().trim()),Integer.decode(left.text.toString().trim()),Integer.decode(right.text.toString().trim()),programs)
                val json = JSONObject().put("control",next.controlAddress).put("left",next.leftAddress).put("right",next.rightAddress)
                next.programs.forEach { json.put("name${it.slot}",it.name); json.put("tin${it.slot}",it.tinnitus.name) }
                val changed = next != fitting
                val edit = prefs.edit().putString("fitting",json.toString())
                if(changed) edit.remove("requested").remove("saved").putBoolean("pending",false)
                check(edit.commit()) { "Setup could not be saved; previous setup retained" }
                fitting = next
                if(changed) { noise.fittingChanged(); requested = RequestedState(); saved = null }
                message = "Setup saved. ${if(changed) "Previous request/snapshot assumptions cleared." else "Setup unchanged."} No playback."
                show("Home")
            } catch(e:Exception) { tell("Setup: ${e.message}",true) }
        }
        saveSetupAction = { saveButton.performClick(); Unit }
    }
    private fun snapshot() {
        line(saved?.let { "Saved requested settings\n${stateText(it)}\nUnknown fields will not be sent." } ?: "No saved settings")
        button("Save requested settings",!busy.get()) { saveRequested() }
        button("Restore known saved settings",!busy.get() && saved?.program != null) { confirmRestore() }
        line("Completed manual changes save automatically. Restore does not replace its source record. This app cannot read external changes made in Signia.")
    }
    private fun saveRequested() {
        if(busy.get()) { tell("Busy; wait or Stop"); return }
        if(requested.program == null) { tell("Select a program before saving",true); return }
        if(prefs.edit().putString("saved",encode(requested)).commit()) { saved = requested; tell("Requested settings saved. Unknown values remain unknown.",true) }
        else tell("Could not save settings; previous record retained",true)
        if(page == "Saved") show()
    }
    private fun voicePage() {
        for((label,key) in listOf("Voice prompts (confirmation and guidance)" to "voicePrompts","Spoken responses (status, results and errors)" to "spokenResponses")) {
            content.addView(CheckBox(this).apply {
                text = label; isChecked = voiceEnabled(key)
                setOnCheckedChangeListener { box,checked ->
                    if(!prefs.edit().putBoolean(key,checked).commit()) { box.isChecked = !checked; tell("Could not save voice setting") }
                    if(!checked) tts?.stop()
                }
            })
        }
        line("Saved separately on this device and the watch. Text replies remain visible. Say voice prompts off/on or voice responses off/on. The installed speech-recognition service may have its own sounds.")
        line("Speak on this Chromebook using the Android speech service. Recognition may use the service's network connection. Cancel sends nothing. Typed commands exercise the same parser.")
        val input = EditText(this).apply { hint = "program two / volume ten / current tin"; setSingleLine(); content.addView(this) }
        button("Run typed command",!busy.get()) { route(input.text.toString()) }
        line("Programs by name or number • volume 0–15 • balance −8 to +7 • tin 0–15 • current program/volume/balance/tin • battery check • save settings • restore settings • cancel.\nNoise: configure noise, read noise settings, assign program three to noise level five, set noise threshold one to minus sixty, save noise settings, noise sample, observe noise, noise status, stop noise. Automatic switching, calibration wizard and schedules remain pending. Watch Wi-Fi is experimental.")
    }
    private fun voiceEnabled(key:String) = prefs.getBoolean(key,if(key == "spokenResponses") prefs.getBoolean("spokenResults",true) else true)
    private fun tell(text: String, speak: Boolean = false) {
        message = text
        val fromWatch = remoteReply != null
        val prompt = pendingProgram != null || pendingRestore != null || text.startsWith("Noise settings.") || text.startsWith("Draft noise")
        if(!busy.get() && remoteReply != null) {
            val reply = remoteReply; remoteReply = null
            reply?.invoke(remoteResult(if(pendingProgram != null || pendingRestore != null) "CLARIFY" else "FINAL",text).put("prompt",prompt))
        }
        if(::status.isInitialized) status.text = text
        if(speak && !fromWatch && speechReady && resumed && !busy.get() && voiceEnabled(if(prompt) "voicePrompts" else "spokenResponses")) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-result")
    }
    private fun beginVoice() {
        noise.stop()
        tts?.stop()
        if(busy.get()) { pendingVoice = true; cancelled.set(true); tell("Stopping playback before voice input"); return }
        launchVoice()
    }
    private fun launchVoice() {
        pendingVoice = false
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                if(voiceEnabled("voicePrompts")) putExtra(RecognizerIntent.EXTRA_PROMPT,"HeySig: say a command")
            },401)
        } catch(e:Exception) { tell("Speech service unavailable. Use typed commands on Voice.",true) }
    }
    @Deprecated("Platform recognition result adapter")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode == 401 && resultCode == RESULT_OK) {
            val alternatives = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if(alternatives.isNotEmpty()) {
                lastHeard = alternatives[0]
                heardStatus.text = "Speech heard: $lastHeard"; heardStatus.visibility = android.view.View.VISIBLE
            }
            // Do not guess between conflicting recognized targets.
            val fit = fitting
            if(fit == null) { tell("Save aid setup first"); return }
            val parsed = alternatives.map { voiceIdentity(it,fit) }.filter { it !is AppIntent.Unsupported }.distinct()
            if(parsed.size > 1) tell("Speech alternatives disagree. Please say the exact program or level again.",true)
            else if(alternatives.isNotEmpty()) pendingText = alternatives.firstOrNull { voiceIdentity(it,fit) == parsed.firstOrNull() } ?: alternatives[0]
        }
    }
    private fun receiveWatch(request: JSONObject, callback: (JSONObject) -> Unit) {
        if(!resumed || watchBridge == null) { callback(remoteResult("BLOCKED","Open HeySig and enable Watch Wi-Fi")); return }
        val id = request.getString("id")
        remotePrefs.getString(id,null)?.let { callback(JSONObject(it)); return }
        val record: (JSONObject) -> Unit = { result ->
            val editor = remotePrefs.edit().putString(id,result.toString())
            val old = remotePrefs.all.keys.filter { it != id }.sorted()
            if(old.size >= 128) old.take(old.size-127).forEach { editor.remove(it) }
            if(!editor.commit()) callback(remoteResult("UNKNOWN","App result could not be stored; do not automatically retry")) else callback(result)
        }
        val text = request.getString("text")
        if(text.lowercase() == "status") { record(remoteResult("FINAL", "Last sent. ${stateText(requested)}")); return }
        if(text.trim().lowercase() in listOf("stop","cancel")) {
            noise.stop(); tts?.stop()
            cancelled.set(true); pendingProgram = null; pendingRestore = null
            record(remoteResult("FINAL","Stop requested; already emitted tones cannot be undone")); return
        }
        if(busy.get() || remoteReply != null) { record(remoteResult("BUSY","HeySig is busy. Wait for its result or say Stop")); return }
        if(page == "Setup") { record(remoteResult("BLOCKED","Return to Home after reviewing and saving Setup")); return }
        if(!remotePrefs.edit().putString(id,remoteResult("UNKNOWN","An earlier request started; do not replay automatically").toString()).commit()) {
            callback(remoteResult("BLOCKED","Cannot journal watch command")); return
        }
        val origin = request.getString("conversation")
        val alternatives = request.optJSONArray("alternatives")
        if(pendingProgram == null && pendingRestore == null && alternatives != null && fitting != null) {
            val intents = (0 until minOf(5,alternatives.length())).map { voiceIdentity(alternatives.getString(it),fitting!!) }.filter { it !is AppIntent.Unsupported }.distinct()
            if(intents.size > 1) { record(remoteResult("CLARIFY","Speech alternatives disagree. Say the exact program or number again")); return }
        }
        remoteReply = record; commandOrigin = origin
        try { lastHeard = "Watch: $text"; route(text,origin) }
        catch(e:Exception) { remoteReply = null; record(remoteResult("FAILED","Watch command could not be processed")) }
        finally { commandOrigin = null }
    }
    private fun voiceIdentity(text:String,fit:Fitting): Any = VoicePreference.parse(text) ?: NoiseVoice.parse(text) ?: VoiceCommands.parse(text,fit)
    private fun route(text: String, origin: String? = null) {
        VoicePreference.parse(text)?.let { preference ->
            if(!prefs.edit().putBoolean(preference.key,preference.enabled).commit()) { tell("Could not save voice setting"); return }
            tts?.stop(); tell(preference.description,true)
            if(page == "Voice") show()
            return
        }
        val fit = fitting ?: run { tell("Save aid setup first",true); return }
        if(text.trim().lowercase() in listOf("stop","cancel")) { noise.stop(); tts?.stop(); cancelled.set(true); pendingProgram = null; pendingRestore = null; tell("Cancelled"); return }
        if((pendingRestore != null || pendingProgram != null) && origin != confirmationOrigin) {
            tell("Complete or cancel the existing confirmation on the device that started it",true); return
        }
        if((pendingRestore != null || pendingProgram != null) && SystemClock.elapsedRealtime() > confirmationDeadline) {
            pendingRestore = null; pendingProgram = null
            tell("Confirmation expired. Please request the action again.",true); return
        }
        if(pendingProgram != null) {
            val slot = pendingProgram!!; pendingProgram = null
            if(text.trim().lowercase().trimEnd('.', '!', '?') in listOf("yes","confirm","confirm program")) dispatch(ManualRequest.SelectProgram(slot))
            else tell("Program selection cancelled. Say the exact name or slot to try again.",true)
            return
        }
        if(pendingRestore != null) {
            val confirmed = text.trim().lowercase().trimEnd('.', '!', '?') in listOf("yes","confirm","confirm restore","restore known settings")
            val plan = pendingRestore!!; pendingRestore = null
            if(confirmed) execute(plan,true) else tell("Restore cancelled. Say restore settings to review again.",true)
            return
        }
        if(text.trim().equals("send again",true)) {
            val request = repeatRequest
            if(request == null) tell("No repeated target to resend",true) else dispatch(request,true)
            return
        }
        if(text.trim().equals("stop",true) || text.trim().equals("cancel",true)) { cancelled.set(true); tell("Cancelled"); return }
        if(busy.get()) { tell("Busy; wait or Stop"); return }
        NoiseVoice.parse(text)?.let { command ->
            if(command == NoiseCommand.Open && origin == null) { navigate("Noise") }
            noise.handle(command)
            return
        }
        when(val intent = VoiceCommands.parse(text,fit)) {
            is AppIntent.Control -> dispatch(intent.request)
            is AppIntent.ClarifyProgram -> clarifyProgram(intent.slot)
            is AppIntent.ReadLevel -> tell("Last sent ${intent.control.name.lowercase()}: ${requested.levels[intent.control]?.toString() ?: "unknown"}.",true)
            AppIntent.ReadProgram -> tell("Last sent program: ${programLabel(requested.program)}.",true)
            AppIntent.ListPrograms -> tell(fit.programs.joinToString(". ") { "${it.slot}: ${it.name}" },true)
            AppIntent.SaveSettings -> saveRequested()
            AppIntent.RestoreSettings -> confirmRestore()
            AppIntent.Cancel -> { cancelled.set(true); tell("Cancelled") }
            is AppIntent.Unsupported -> tell(intent.explanation,true)
        }
    }
    private fun clarifyProgram(slot: Int) {
        pendingProgram = slot; pendingRestore = null; confirmationOrigin = commandOrigin
        confirmationDeadline = SystemClock.elapsedRealtime() + 60000
        tell("Did you mean ${programLabel(slot)}? Say yes or cancel.",true)
        if(commandOrigin != null) return
        android.app.AlertDialog.Builder(this).setTitle("Confirm program")
            .setMessage("Did you mean ${programLabel(slot)}? Nothing has been sent.")
            .setNegativeButton("Cancel") { _,_ -> pendingProgram = null }
            .setNeutralButton("Speak answer") { _,_ -> beginVoice() }
            .setPositiveButton("Yes") { _,_ -> pendingProgram = null; if(SystemClock.elapsedRealtime() <= confirmationDeadline) dispatch(ManualRequest.SelectProgram(slot)) else tell("Confirmation expired; nothing sent",true) }
            .setOnCancelListener { pendingProgram = null }.show()
    }
    private fun confirmRestore() {
        val source = saved ?: run { tell("No saved settings",true); return }
        val slot = source.program ?: run { tell("Saved program unknown",true); return }
        val plan = listOf<ManualRequest>(ManualRequest.SelectProgram(slot)) + LevelControl.entries.mapNotNull { key -> source.levels[key]?.let { ManualRequest.SetLevel(key,it) } }
        pendingRestore = plan; pendingProgram = null; confirmationOrigin = commandOrigin
        confirmationDeadline = SystemClock.elapsedRealtime() + 60000
        tell("Restore ${stateText(source)}. Unknown fields will be skipped. Say confirm restore or cancel.",true)
        if(commandOrigin != null) return
        android.app.AlertDialog.Builder(this).setTitle("Restore known requests?")
            .setMessage("${stateText(source)}\nUnknown fields are skipped.")
            .setNegativeButton("Cancel") { _,_ -> pendingRestore = null }
            .setNeutralButton("Speak answer") { _,_ -> beginVoice() }
            .setPositiveButton("Restore") { _,_ -> pendingRestore = null; if(SystemClock.elapsedRealtime() <= confirmationDeadline) execute(plan,true) else tell("Confirmation expired; nothing sent",true) }
            .setOnCancelListener { pendingRestore = null }.show()
    }
    private fun dispatch(request: ManualRequest, force: Boolean = false) {
        if(busy.get()) { tell("Busy; wait or Stop"); return }
        val fit = fitting ?: run { tell("Save setup first",true); return }
        try { ManualValidation.validate(request,fit,requested) } catch(e:Exception) { tell(e.message ?: "Invalid command",true); return }
        val same = when(request) {
            is ManualRequest.SelectProgram -> requested.program == request.slot
            is ManualRequest.SetLevel -> requested.levels[request.control] == request.level
            is ManualRequest.Battery -> false
        }
        if(same && !force) {
            repeatRequest = request; message = "Already last requested. Use Send last target again or say send again."
            show(); tell(message,true); return
        }
        repeatRequest = null
        execute(listOf(request),false)
    }
    private fun execute(plan: List<ManualRequest>,restoring: Boolean) {
        noise.stop()
        val fit = fitting ?: run { tell("Save setup first"); return }
        if(!resumed) { tell("Open HeySig before sending a command"); return }
        if(busy.get()) { tell("Busy; wait or Stop"); return }
        try {
            var simulated = requested
            plan.forEach { ManualValidation.validate(it,fit,simulated); simulated = simulated.completed(it) }
            player.inspect()
            check(prefs.edit().putBoolean("pending",true).commit()) { "Cannot journal command; no playback" }
        } catch(e:Exception) { tell("Blocked: ${e.message}",true); return }
        if(!busy.compareAndSet(false,true)) return
        cancelled.set(false); tts?.stop(); message = "Transmitting…"; show()
        Thread({
            val completed = mutableListOf<String>()
            var result: String
            var durable = true
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
                        runOnUiThread { tell("Transmitting ${stage.first}…") }
                        player.play(stage.second,profile.sampleRate,cancelled)
                        completed.add(stage.first)
                        if(stageIndex < stages.lastIndex) gap()
                    }
                    requested = requested.completed(request)
                    durable = prefs.edit().putString("requested",encode(requested)).commit() && durable
                    if(index < plan.lastIndex) gap()
                }
                if(!restoring && requested.program != null && plan.any { it !is ManualRequest.Battery }) {
                    if(prefs.edit().putString("saved",encode(requested)).commit()) saved = requested else durable = false
                }
                result = "Played: ${completed.joinToString()}."
            } catch(e:Exception) { result = "${if(completed.isEmpty()) "No completed stages" else "Completed: ${completed.joinToString()}"}. ${e.message}. Current aid state may be uncertain." }
            finally {
                durable = prefs.edit().putString("requested",encode(requested)).putBoolean("pending",false).commit() && durable
                busy.set(false)
            }
            if(!durable) result += " Storage failed; remembered state may not survive restart."
            runOnUiThread {
                message = result
                val watchReply = remoteReply; remoteReply = null
                watchReply?.invoke(remoteResult("FINAL",result))
                if(!isDestroyed) {
                    show()
                    if(pendingVoice && resumed) launchVoice()
                    else if(watchReply == null && resumed) tell(result,true)
                }
            }
        },"heysig-manual").start()
    }
    private fun gap() { repeat(100) { check(!cancelled.get()) { "Stopped" }; Thread.sleep(10) } }
}
