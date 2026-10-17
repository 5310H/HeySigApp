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
import com.heysig.companion.PrivacyNotice
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
    @Volatile private var resumed = false
    @Volatile private var pendingVoice = false
    private var pendingText: String? = null
    @Volatile private var pendingRestore: List<ManualRequest>? = null
    @Volatile private var pendingProgram: Int? = null
    private var confirmationDeadline = 0L
    private var lastHeard = ""
    private lateinit var heardStatus: TextView
    private var repeatRequest: ManualRequest? = null
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    @Volatile private var tts: TextToSpeech? = null
    private var speechReady = false
    private var page = "Home"
    private var setupChanged: (() -> Boolean)? = null
    private var saveSetupAction: (() -> Unit)? = null
    private var message = "Ready. Nothing is sent until you ask."
    private var speechSummary: TextView? = null
    private fun speechStatusText() = "Prompts ${if(voiceEnabled("voicePrompts")) "on" else "off"} · Spoken replies ${if(voiceEnabled("spokenResponses")) "on" else "off"}"
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var watchBridge: WatchBridge? = null
    private var watchToggle: CheckBox? = null
    @Volatile private var remoteReply: ((JSONObject) -> Unit)? = null
    private var commandOrigin: String? = null
    private var confirmationOrigin: String? = null
    private var activeRemoteNode: String? = null
    private var exportDocument: String? = null
    private val watchHistory by lazy { WatchHistory(this) }
    private val companionDelegate = lazy { CompanionConnection(this,
        { node,request,reply -> receiveWatch(request,reply,node) },
        { node,id -> watchHistory.get(node,id) },
        {
            if(activeRemoteNode != null) cancelled.set(true)
            if(confirmationOrigin?.startsWith("paired:") == true) { pendingProgram = null; pendingRestore = null }
        },{ text -> tell(text) }) }
    private val companion by companionDelegate
    private fun disconnectCompanion() { if(companionDelegate.isInitialized()) companion.close() }
    private val remotePrefs by lazy { getSharedPreferences("heysig-watch-results",MODE_PRIVATE) }
    private fun remoteResult(phase: String, text: String) = JSONObject().put("phase",phase).put("text",text)
    private val profile = AcousticProfile(44100,15360,512,0.0625,1.2,true)
    private val noise by lazy { NoiseFeature(this,{ fitting?.programs.orEmpty() },
        { noiseSafety().let { it.foreground && it.unlocked && it.audioFree } },{ text,speak -> tell(text,speak) },
        { noiseSafety() },{ requested.program },{ slot,done ->
            execute(listOf(ManualRequest.SelectProgram(slot)),false,automatic=true,onComplete=done)
        }) }
    private fun noiseSafety(): NoiseSafety {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        return NoiseSafety(foreground=resumed,unlocked=!keyguard.isKeyguardLocked,
            audioFree=!busy.get() && !pendingVoice && pendingProgram == null && pendingRestore == null && remoteReply == null && tts?.isSpeaking != true,
            sleeping=fitting?.programs?.find { it.slot == requested.program }?.name?.trim()?.equals("sleep",true) == true)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        load()
        if(android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = android.window.OnBackInvokedCallback { leavePage() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,it)
            }
        }
        if(android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        tts = TextToSpeech(this) { code ->
            speechReady = code == TextToSpeech.SUCCESS
            if(speechReady) tts?.language = Locale.US
        }
        show("Home")
    }
    override fun onResume() {
        super.onResume(); resumed = true; if(companionDelegate.isInitialized()) companion.resume(); watchToggle?.isChecked = watchBridge != null
        runCatching { watchHistory.prune() }.onFailure { tell("Watch history could not be expired; watch commands remain blocked until storage works") }
    }
    override fun onPostResume() {
        super.onPostResume()
        pendingText?.let { pendingText = null; route(it) }
    }
    override fun onPause() { noise.stop(); if(companionDelegate.isInitialized()) companion.pause(); watchBridge?.close(); watchBridge = null; resumed = false; pendingVoice = false; cancelled.set(true); tts?.stop(); super.onPause() }
    override fun onDestroy() {
        if(android.os.Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        noise.stop(); disconnectCompanion(); exportDocument = null; cancelled.set(true); tts?.shutdown(); super.onDestroy()
    }
    @Deprecated("Fallback for Android 10–12; newer versions use OnBackInvokedDispatcher")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ dispatcher is registered in onCreate; retain the pre-33 framework fallback.
    override fun onBackPressed() { leavePage() }
    private fun leavePage() {
        when(page) { "Setup", "Voice", "Watch", "Privacy" -> navigate("Settings"); "Home" -> finish(); else -> navigate("Home") }
    }

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
    private fun line(text: String) = TextView(this).also {
        PhoneStyle.text(it,16f,color=PhoneStyle.secondary); it.text = text
        it.setPadding(0,PhoneStyle.dp(this,4),0,PhoneStyle.dp(this,10)); content.addView(it)
    }
    private fun button(text: String, enabled: Boolean = true, action: () -> Unit) = Button(this).also {
        it.text = text; it.isEnabled = enabled
        PhoneStyle.action(it,if(text.startsWith("Delete")) "danger" else "secondary")
        it.setOnClickListener { action() }; content.addView(it)
    }
    private fun section(title: String, detail: String? = null, compact: Boolean = false, build: () -> Unit) {
        val parent = content
        content = PhoneStyle.card(this,parent)
        try {
            val heading = PhoneStyle.heading(this,content,title)
            if(compact) PhoneStyle.text(heading,13f,true,PhoneStyle.secondary)
            detail?.let { line(it) }
            build()
        } finally { content = parent }
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
        val root = FrameLayout(this).apply { setBackgroundColor(PhoneStyle.background) }
        val shell = object : LinearLayout(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val width = minOf(android.view.View.MeasureSpec.getSize(widthSpec),PhoneStyle.dp(context,760))
                super.onMeasure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),heightSpec)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        root.addView(shell,FrameLayout.LayoutParams(-1,-1,android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PhoneStyle.dp(this@HomeActivity,22),PhoneStyle.dp(this@HomeActivity,18),PhoneStyle.dp(this@HomeActivity,22),PhoneStyle.dp(this@HomeActivity,12))
        }
        line("HEYSIG").apply { PhoneStyle.text(this,12f,true,PhoneStyle.blue); letterSpacing = .15f }
        val title = when(page) {
            "Home" -> "Your hearing"
            "Noise" -> "Sound & noise"
            "Saved" -> "Saved settings"
            "Setup" -> "Aid setup"
            "Voice" -> "Voice & speech"
            "Watch" -> "Watch connection"
            "Privacy" -> "Privacy & data"
            else -> "Settings"
        }
        line(title).apply { PhoneStyle.text(this,minOf(34f,56f/resources.configuration.fontScale),true); isAccessibilityHeading = true }
        line(when(page) {
            "Home" -> "Your listening controls, all in one place."
            "Noise" -> "Understand your surroundings. Stay in control."
            "Saved" -> "Keep a familiar starting point."
            "Settings" -> "Make HeySig work your way."
            else -> "Your preferences, on your terms."
        }).apply { if(resources.configuration.fontScale > 1.3f) visibility = android.view.View.GONE }
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PhoneStyle.dp(context,4),PhoneStyle.dp(context,4),PhoneStyle.dp(context,4),PhoneStyle.dp(context,12))
            content.addView(this)
        }
        status = TextView(this).apply {
            text = message; PhoneStyle.text(this,14f,color=PhoneStyle.secondary)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            statusCard.addView(this)
        }
        heardStatus = TextView(this).apply {
            text = if(lastHeard.isEmpty()) "" else "Heard: $lastHeard"
            PhoneStyle.text(this,14f,color=PhoneStyle.secondary)
            visibility = if(lastHeard.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            statusCard.addView(this)
        }
        when(page) {
            "Home" -> home()
            "Setup" -> section("Your hearing-aid setup") { setup() }
            "Saved" -> section("Your saved snapshot") { snapshot() }
            "Voice" -> section("Voice & speech") { voicePage() }
            "Noise" -> noise.render(content)
            "Watch" -> section("Your watch") { if(resumed) companion.resume(); companion.render(content) }
            "Privacy" -> privacyPage()
            "Settings" -> settingsPage()
        }
        PhoneStyle.polish(content)
        val scroll = ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            addView(content,ViewGroup.LayoutParams(-1,-2))
        }
        shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val actions = LinearLayout(this).apply {
            setPadding(PhoneStyle.dp(context,18),PhoneStyle.dp(context,8),PhoneStyle.dp(context,18),PhoneStyle.dp(context,4))
        }
        val speak = Button(this).apply { text = "Speak command"; setOnClickListener { beginVoice() }; PhoneStyle.action(this,"primary") }
        val stop = Button(this).apply {
            text = "Stop"; PhoneStyle.action(this,"danger")
            setOnClickListener { noise.stop(); tts?.stop(); pendingProgram = null; pendingRestore = null; pendingVoice = false; cancelled.set(true); tell("Stop requested. Already emitted tones cannot be undone.") }
        }
        actions.addView(speak,LinearLayout.LayoutParams(0,-2,2f).apply { marginEnd = PhoneStyle.dp(this@HomeActivity,10) })
        actions.addView(stop,LinearLayout.LayoutParams(0,-2,1f))
        shell.addView(actions)
        val speechLabel = TextView(this).apply {
            text = speechStatusText()
            PhoneStyle.text(this,12f,color=PhoneStyle.secondary); gravity = android.view.Gravity.CENTER
            setPadding(PhoneStyle.dp(context,12),0,PhoneStyle.dp(context,12),PhoneStyle.dp(context,6))
        }; speechSummary = speechLabel; shell.addView(speechLabel)
        val tabs = listOf("Home","Noise","Saved","Settings")
        val selected = if(page in tabs) page else "Settings"
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.WHITE) }
        val count = if(resources.configuration.fontScale > 1.3f && resources.configuration.screenWidthDp < 600 && resources.configuration.screenHeightDp > 480) 2 else 4
        tabs.chunked(count).forEach { rowTabs ->
            val row = LinearLayout(this)
            rowTabs.forEach { target ->
                val active = selected == target
                val tab = Button(this).apply {
                    text = target; isEnabled = !busy.get(); tag = "navigation"
                    PhoneStyle.text(this,12f,active,if(active) PhoneStyle.blue else PhoneStyle.secondary)
                    isAllCaps = false; minHeight = PhoneStyle.dp(context,68); minimumHeight = minHeight
                    setPadding(PhoneStyle.dp(context,4),PhoneStyle.dp(context,8),PhoneStyle.dp(context,4),PhoneStyle.dp(context,8))
                    background = PhoneStyle.shape(context,android.graphics.Color.WHITE,0)
                    setCompoundDrawables(null,PhoneStyle.icon(context,target,if(active) PhoneStyle.blue else PhoneStyle.secondary),null,null)
                    compoundDrawablePadding = PhoneStyle.dp(context,5)
                    alpha = if(isEnabled) 1f else .45f
                    isSelected = active; contentDescription = "$target${if(active) ", selected" else ""}"
                    setOnClickListener { navigate(target) }
                }
                row.addView(tab,LinearLayout.LayoutParams(0,-2,1f))
            }; navigation.addView(row)
        }; shell.addView(navigation)
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
                val keyboardVisible = insets.isVisible(android.view.WindowInsets.Type.ime())
                navigation.visibility = if(keyboardVisible) android.view.View.GONE else android.view.View.VISIBLE
                speechLabel.visibility = if(keyboardVisible) android.view.View.GONE else android.view.View.VISIBLE
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }; insets
        }
        setContentView(root)
        root.requestApplyInsets()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun settingsPage() {
        section("Your preferences") {
            button("Aid setup") { navigate("Setup") }
            button("Voice & speech") { navigate("Voice") }
            button("Watch connection") { navigate("Watch") }
            button("Privacy and data") { navigate("Privacy") }
        }
        section("Device & assistance") {
            line("Only completed requests are remembered. HeySig cannot read changes made directly on your aids or in another app.")
            button("Check device audio",!busy.get()) { try { player.inspect(); tell("Speaker audio checks passed. No command sent.",true) } catch(e:Exception) { tell("Blocked: ${e.message}",true) } }
            button("Mark aid settings unknown",!busy.get()) {
                requested = RequestedState()
                message = if(prefs.edit().putString("requested",encode(requested)).commit()) "Requested state cleared; saved restoration record retained." else "Could not save unknown state"
                show()
            }
        }
        if(BuildConfig.DEBUG) section("Development connection") {
            line("Optional Chromebook relay. Normal phone/watch pairing is available in the release candidate.")
            val remote = CheckBox(this).apply {
                text = "Watch Wi-Fi (development)"; isChecked = watchBridge != null
                setOnCheckedChangeListener { box,enabled ->
                    if(enabled) {
                        if(getSharedPreferences("heysig-watch-dev",MODE_PRIVATE).getString("token",null) == null) {
                            box.isChecked = false; tell("Set up the Chromebook watch relay first")
                        } else try {
                            watchBridge = WatchBridge(this@HomeActivity) { request,reply -> runOnUiThread { try { receiveWatch(request,reply) } catch(e:Exception) { reply(remoteResult("FAILED","Watch request could not be processed")) } } }
                            tell("Watch Wi-Fi ready while HeySig stays open")
                        } catch(e:Exception) { box.isChecked = false; tell("Watch bridge could not start: ${e.message}") }
                    } else { watchBridge?.close(); watchBridge = null }
                }
            }; watchToggle = remote; content.addView(remote)
        }
    }
    private fun programLabel(slot: Int?) = fitting?.programs?.find { it.slot == slot }?.let { "${it.name}, program ${it.slot}" } ?: "unknown"
    private fun stateText(state: RequestedState) = "Program: ${programLabel(state.program)}\n" +
        LevelControl.entries.joinToString("\n") { "${when(it) { LevelControl.VOLUME -> "Volume"; LevelControl.SHARPNESS -> "Sound sharpness"; LevelControl.TINNITUS -> "Tinnitus" }}: ${state.levels[it]?.toString() ?: "not set"}" }
    private fun home() {
        section("Last requested",compact=true) {
            line(fitting?.programs?.find { it.slot == requested.program }?.name ?: "No program selected").apply { PhoneStyle.text(this,27f,true) }
            line("Not a live reading from the aids.")
            if(fitting == null) {
                line("Set up your hearing aids once to unlock your controls.")
                PhoneStyle.action(button("Set up hearing aids") { navigate("Setup") },"primary")
            }
        }
        if(fitting != null) section("Programs","Choose a listening program.") {
            val programs = fitting!!.programs
            val columns = if(resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.3f) 1 else 2
            programs.chunked(columns).forEach { group ->
                val row = LinearLayout(this)
                group.forEach { program ->
                    val selected = requested.program == program.slot
                    val choice = Button(this).apply {
                        text = "${program.name}${if(selected) " ✓" else ""}\nProgram ${program.slot}"
                        contentDescription = "${program.name}, program ${program.slot}${if(selected) ", last requested" else ""}"
                        isEnabled = !busy.get(); isSelected = selected
                        PhoneStyle.action(this,if(selected) "selected" else "program")
                        gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                        minHeight = PhoneStyle.dp(context,88)
                        setOnClickListener { dispatch(ManualRequest.SelectProgram(program.slot)) }
                    }
                    row.addView(choice,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd = PhoneStyle.dp(this@HomeActivity,5); bottomMargin = PhoneStyle.dp(this@HomeActivity,8) })
                }
                if(group.size < columns) row.addView(android.view.View(this),LinearLayout.LayoutParams(0,1,1f))
                content.addView(row)
            }
        }
        if(fitting != null) for(control in LevelControl.entries) {
            val title = when(control) { LevelControl.VOLUME -> "Volume"; LevelControl.SHARPNESS -> "Sound sharpness"; LevelControl.TINNITUS -> "Tinnitus" }
            val supported = control != LevelControl.TINNITUS || fitting?.programs?.find { it.slot == requested.program }?.tinnitus == TinnitusCapability.SUPPORTED
            val enabled = !busy.get() && requested.program != null && supported
            section(title) {
                val last = requested.levels[control]
                line("Last requested: ${last?.toString() ?: "not set"}")
                var draft = last ?: if(control == LevelControl.VOLUME) 8 else 0
                val value = line(draft.toString()).apply { PhoneStyle.text(this,30f,true) }
                val slider = SeekBar(this).apply {
                    max = control.range.last-control.range.first
                    progress = draft-control.range.first; isEnabled = enabled
                    minHeight = PhoneStyle.dp(context,48)
                    progressTintList = android.content.res.ColorStateList.valueOf(PhoneStyle.blue)
                    thumbTintList = android.content.res.ColorStateList.valueOf(PhoneStyle.blue)
                    contentDescription = "$title, chosen level $draft"
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                            draft = control.range.first+progress; value.text = draft.toString()
                            bar.contentDescription = "$title, chosen level $draft"
                        }
                        override fun onStartTrackingTouch(bar: SeekBar) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar) = Unit
                    })
                }; content.addView(slider,LinearLayout.LayoutParams(-1,-2))
                line("${control.range.first} – ${control.range.last} · Changes are sent only when you tap Send.")
                PhoneStyle.action(button("Send ${title.lowercase()}",enabled) { dispatch(ManualRequest.SetLevel(control,draft)) },"primary")
                if(!enabled) line(if(requested.program == null) "Choose a program first." else if(!supported) "Tinnitus support needs confirmation for this program in Aid setup." else "Wait for the current request or tap Stop.")
            }
        }
        repeatRequest?.let { request -> section("Repeat a request") { button("Send last target again",!busy.get()) { dispatch(request,true) } } }
        if(fitting != null) section("Battery indications","Ask your aids for their usual battery indication.") {
            button("Check both batteries",!busy.get()) { dispatch(ManualRequest.Battery(true,true)) }
            button("Check left battery",!busy.get()) { dispatch(ManualRequest.Battery(true,false)) }
            button("Check right battery",!busy.get()) { dispatch(ManualRequest.Battery(false,true)) }
        }
    }
    private fun setup() {
        line("Review and save. For your tested fitting, addresses were 12. This is not a universal default. Blank program names disable those slots. Tinnitus support must be confirmed per program.")
        fun field(label: String, value: String): EditText {
            val caption = line(label)
            return EditText(this).apply {
                id = android.view.View.generateViewId(); caption.labelFor = id
                setSingleLine(); setText(value); content.addView(this)
            }
        }
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
                contentDescription = "Tinnitus availability for program $slot"
                adapter = ArrayAdapter(this@HomeActivity,android.R.layout.simple_spinner_dropdown_item,TinnitusCapability.entries.map { it.name.lowercase() })
                setSelection((existing?.tinnitus ?: TinnitusCapability.UNKNOWN).ordinal); content.addView(this)
            })
        }
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
        PhoneStyle.heading(this,content,"Spoken feedback")
        line(PrivacyNotice.SYNTHESIS)
        for((label,key) in listOf("Voice prompts (confirmation and guidance)" to "voicePrompts","Spoken responses (status, results and errors)" to "spokenResponses")) {
            content.addView(CheckBox(this).apply {
                text = label; isChecked = voiceEnabled(key)
                var reverting = false
                setOnCheckedChangeListener { box,checked ->
                    if(reverting) return@setOnCheckedChangeListener
                    if(!prefs.edit().putBoolean(key,checked).commit()) {
                        reverting = true; box.isChecked = !checked; reverting = false
                        tell("Could not save voice setting")
                    }
                    speechSummary?.text = speechStatusText()
                    if(!checked) tts?.stop()
                }
            })
        }
        line("Saved separately on this device and the watch. Text replies remain visible. Say voice prompts off/on or voice responses off/on. The installed speech-recognition service may have its own sounds.")
        line("Use your installed speech service, or type a command below. Recognition may use the service's network connection. Cancel sends nothing.")
        PhoneStyle.heading(this,content,"Type a command")
        val input = EditText(this).apply { hint = "Try: program two"; setSingleLine(); content.addView(this) }
        button("Run typed command",!busy.get()) { route(input.text.toString()) }
        line("Programs by name or number • volume 0–15 • balance −8 to +7 • tin 0–15 • current program/volume/balance/tin • battery check • save settings • restore settings • cancel.\nNoise: configure noise, read noise settings, assign program three to noise level five, set noise threshold one to minus sixty, save noise settings, noise sample, observe noise, noise status, stop noise. Guided calibration and automatic preview are available on Noise. Automatic switching remains locked pending physical acceptance. Schedules remain pending. Use Watch connection for companion setup.")
    }
    private fun privacyPage() {
        PrivacyNotice.sections.forEach { (title,text) -> section(title) { line(text) } }
        if(BuildConfig.DEBUG) line("Development build: the optional Linux/ADB relay has separate command/reply storage on that computer. This phone can also retain up to 128 legacy development replies until cleared; the 24-hour production rule does not apply to those old records. Clear heard text and watch history removes phone-side replies. Phone deletion does not delete computer relay records. This transport is excluded from production.")
        button("Open public privacy policy") {
            if(BuildConfig.PRIVACY_POLICY_URL.isEmpty()) tell("Public policy is not configured yet. Publisher contact and hosting must be supplied before release.")
            else try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
            catch(e:Exception) { tell("No browser could open the policy") }
        }
        button("Clear heard text and watch history",!busy.get()) {
            if(busy.get() || remoteReply != null) { tell("Wait for the current result or Stop before clearing history"); return@button }
            disconnectCompanion(); noise.stop(); tts?.stop()
            pendingText = null; lastHeard = ""; pendingProgram = null; pendingRestore = null
            val cleared = watchHistory.clear() && remotePrefs.edit().clear().commit()
            message = if(cleared) "Heard text and phone watch history cleared. Saved fitting and restoration settings retained. Clear watch data separately on the watch." else "Some history could not be cleared; try again"
            show()
        }
        button("Export local app data",!busy.get()) {
            android.app.AlertDialog.Builder(this).setTitle("Export private settings?")
                .setMessage("The JSON file contains hearing-aid setup, requested settings, noise configuration and retained watch replies. Save it only to a location you trust. A chosen cloud provider may upload it. Pairing secrets are excluded. HeySig cannot delete exported copies.")
                .setNegativeButton("Cancel",null).setPositiveButton("Choose location") { _,_ ->
                    if(busy.get() || remoteReply != null) { tell("Wait for the current result before exporting"); return@setPositiveButton }
                    try {
                        val document = JSONObject().put("format","heysig-local-export-v1")
                            .put("createdAt",System.currentTimeMillis())
                            .put("settings",JSONObject(prefs.all))
                            .put("noise",JSONObject(getSharedPreferences("heysig-noise-v1",MODE_PRIVATE).all))
                            .put("companionSelection",JSONObject(getSharedPreferences("heysig-companion",MODE_PRIVATE).all))
                            .put("watchResults",watchHistory.export())
                        exportDocument = document.toString(2)
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE,"heysig-data.json")
                        },403)
                    } catch(e:Exception) { exportDocument = null; tell("Export could not start") }
                }.show()
        }
        button("Delete local app data",!busy.get()) {
            android.app.AlertDialog.Builder(this).setTitle("Delete this device's HeySig data?")
                .setMessage("Android will close HeySig and erase this app's private data, including fitting, requested settings, saved restoration record, noise configuration, watch selection/history and speech choices, and reset its permissions. You will need Setup again. No command is sent to the aids. Watch, relay, provider and exported copies must be deleted separately.")
                .setNegativeButton("Keep data",null).setPositiveButton("Delete local data") { _,_ ->
                    if(busy.get() || remoteReply != null) { tell("Wait for the current result or Stop before deleting data"); return@setPositiveButton }
                    noise.stop(); disconnectCompanion(); watchBridge?.close(); watchBridge = null; tts?.stop()
                    pendingVoice = false; pendingText = null; lastHeard = ""; exportDocument = null
                    val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                    if(!manager.clearApplicationUserData()) tell("Android could not clear local data. Use the app's Android storage settings.")
                }.show()
        }
        button("Open microphone permission settings") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
        }
    }
    private fun voiceEnabled(key:String) = prefs.getBoolean(key,if(key == "spokenResponses") prefs.getBoolean("spokenResults",BuildConfig.DEBUG) else BuildConfig.DEBUG)
    private fun tell(text: String, speak: Boolean = false) {
        message = text
        val fromWatch = remoteReply != null
        val prompt = pendingProgram != null || pendingRestore != null || text.startsWith("Noise settings.") || text.startsWith("Draft noise")
        if(!busy.get() && remoteReply != null) {
            val reply = remoteReply; remoteReply = null
            activeRemoteNode = null
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
        if(!prefs.getBoolean("speechDisclosureV1",false)) {
            android.app.AlertDialog.Builder(this).setTitle("Optional speech recognition")
                .setMessage(PrivacyNotice.SPEECH).setNegativeButton("Not now",null)
                .setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("speechDisclosureV1",true).commit()) launchVoice()
                    else tell("Could not save speech choice")
                }.show()
            return
        }
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
        if(requestCode == 403) {
            val document = exportDocument; exportDocument = null
            if(resultCode == RESULT_OK && document != null && data?.data != null) {
                try {
                    checkNotNull(contentResolver.openOutputStream(data.data!!,"wt")).use { it.write(document.toByteArray(Charsets.UTF_8)) }
                    tell("Export saved to your chosen location. Delete that copy separately when no longer needed.")
                } catch(e:Exception) { tell("Export could not be saved") }
            }
            return
        }
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
    private fun receiveWatch(request: JSONObject, callback: (JSONObject) -> Unit, node: String? = null) {
        if(!resumed || (if(node == null) watchBridge == null else !companion.accepts(node))) { callback(remoteResult("BLOCKED","Open HeySig and enable its watch connection")); return }
        val id = request.getString("id")
        if(node == null) remotePrefs.getString(id,null)?.let { callback(JSONObject(it)); return }
        else watchHistory.get(node,id)?.let { callback(it); return }
        val record: (JSONObject) -> Unit = { result ->
            val stored = if(node != null) watchHistory.put(node,id,result) else {
                val editor = remotePrefs.edit().putString(id,result.toString())
                val old = remotePrefs.all.keys.filter { it != id }.sorted()
                if(old.size >= 128) old.take(old.size-127).forEach { editor.remove(it) }
                editor.commit()
            }
            if(!stored) callback(remoteResult("UNKNOWN","App result could not be stored; do not automatically retry")) else callback(result)
        }
        val text = request.getString("text")
        if(text.lowercase() == "status") { record(remoteResult("FINAL", "Last sent. ${stateText(requested)}")); return }
        if(text.trim().lowercase() in listOf("stop","cancel")) {
            noise.stop(); tts?.stop()
            cancelled.set(true); pendingProgram = null; pendingRestore = null
            record(remoteResult("FINAL","Stop requested; already emitted tones cannot be undone")); return
        }
        // Authenticated manual input ends automatic sessions even when playback is busy.
        // An already emitted command cannot be undone; never queue or replay the new request.
        if(NoiseVoice.parse(text) == null) noise.stop()
        if(busy.get() || remoteReply != null) { record(remoteResult("BUSY","HeySig is busy. Wait for its result or say Stop")); return }
        if(page == "Setup") { record(remoteResult("BLOCKED","Return to Home after reviewing and saving Setup")); return }
        if(node != null && NoiseVoice.parse(text) in listOf(NoiseCommand.Sample,NoiseCommand.Observe) &&
            (!getSharedPreferences("heysig-noise-v1",MODE_PRIVATE).getBoolean("microphoneDisclosureV2",false) ||
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            record(remoteResult("BLOCKED","On the phone, open Noise and review microphone use and permission before requesting watch noise capture.")); return
        }
        val started = remoteResult("UNKNOWN","An earlier request started; do not replay automatically")
        if(!(if(node == null) remotePrefs.edit().putString(id,started.toString()).commit() else watchHistory.put(node,id,started))) {
            callback(remoteResult("BLOCKED","Cannot journal watch command")); return
        }
        val origin = (if(node != null) "paired:$node:" else "") + request.getString("conversation")
        val alternatives = request.optJSONArray("alternatives")
        if(pendingProgram == null && pendingRestore == null && alternatives != null && fitting != null) {
            val intents = (0 until minOf(5,alternatives.length())).map { voiceIdentity(alternatives.getString(it),fitting!!) }.filter { it !is AppIntent.Unsupported }.distinct()
            if(intents.size > 1) { record(remoteResult("CLARIFY","Speech alternatives disagree. Say the exact program or number again")); return }
        }
        remoteReply = record; commandOrigin = origin; activeRemoteNode = node
        try { lastHeard = "Watch: $text"; route(text,origin) }
        catch(e:Exception) { remoteReply = null; activeRemoteNode = null; record(remoteResult("FAILED","Watch command could not be processed")) }
        finally { commandOrigin = null }
    }
    private fun voiceIdentity(text:String,fit:Fitting): Any = VoicePreference.parse(text) ?: NoiseVoice.parse(text) ?: VoiceCommands.parse(text,fit)
    private fun route(text: String, origin: String? = null) {
        if(NoiseVoice.parse(text) == null) noise.stop()
        VoicePreference.parse(text)?.let { preference ->
            if(!prefs.edit().putBoolean(preference.key,preference.enabled).commit()) { tell("Could not save voice setting"); return }
            speechSummary?.text = speechStatusText()
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
        noise.stop() // Any manual request, including a repeat/no-op, takes priority.
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
    private fun execute(plan: List<ManualRequest>,restoring: Boolean, automatic: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        if(!automatic) noise.stop()
        fun reject(text:String) { tell(text,true); onComplete?.invoke(false) }
        if(automatic && (!NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED || noiseSafety().let { !it.foreground || !it.unlocked || !it.audioFree || it.manualOverride || it.sleeping })) {
            reject("Automatic blocked by release or foreground/audio priority guard"); return
        }
        val fit = fitting ?: run { reject("Save setup first"); return }
        if(!resumed) { reject("Open HeySig before sending a command"); return }
        if(busy.get()) { reject("Busy; wait or Stop"); return }
        try {
            var simulated = requested
            plan.forEach { ManualValidation.validate(it,fit,simulated); simulated = simulated.completed(it) }
            player.inspect()
            check(prefs.edit().putBoolean("pending",true).commit()) { "Cannot journal command; no playback" }
        } catch(e:Exception) { reject("Blocked: ${e.message}"); return }
        if(!busy.compareAndSet(false,true)) { onComplete?.invoke(false); return }
        cancelled.set(false); tts?.stop(); message = "Transmitting…"; show()
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
                        runOnUiThread { tell("Transmitting ${stage.first}…") }
                        player.play(stage.second,profile.sampleRate,cancelled)
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
                busy.set(false)
            }
            if(!durable) result += " Storage failed; remembered state may not survive restart."
            runOnUiThread {
                onComplete?.invoke(playbackSucceeded && durable && !cancelled.get())
                message = result
                val watchReply = remoteReply; remoteReply = null; activeRemoteNode = null
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
