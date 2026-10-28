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
import com.heysig.voice.*
import com.heysig.sleep.*
import com.heysig.app.voice.HandsFreeService
import com.heysig.companion.PrivacyNotice
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** First native manual app slice. Does not start listeners, schedules or automatic playback. */
class HomeActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("heysig-core-v1", MODE_PRIVATE) }
    private val runtime by lazy { ManualRuntime.get(this) }
    private val player by lazy { SpeakerPlayback(this) }
    private var fitting: Fitting?
        get() = runtime.fitting
        set(value) { runtime.fitting=value }
    private var requested: RequestedState
        get() = runtime.requested
        set(value) { runtime.requested=value }
    private var saved: RequestedState?
        get() = runtime.saved
        set(value) { runtime.saved=value }
    private val busy get() = runtime.busy
    private val cancelled get() = runtime.cancelled
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
    private var foregroundSpeechDeadline=0L
    private var speechId=0L
    private var page = "Home"
    private var setupChanged: (() -> Boolean)? = null
    private var saveSetupAction: (() -> Unit)? = null
    private var message = "Ready. Nothing is sent until you ask."
    private var speechSummary: TextView? = null
    private var handsFreeStatus: TextView? = null
    private var voiceSessionStatus: TextView? = null
    private var voiceSessionPresent = false
    private var pendingHandsFreeStart=false
    private var pendingExactSleepRevision: Long?=null
    private var pendingNotificationSleepRevision: Long?=null
    private var seenRuntimeRevision=0L
    private var statusRow: LinearLayout? = null
    private fun speechStatusText() = "Prompts ${if(voiceEnabled("voicePrompts")) "on" else "off"} · Replies ${if(voiceEnabled("spokenResponses")) "on" else "off"}"
    private var backCallback: android.window.OnBackInvokedCallback? = null
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
            manualOverride=runtime.sleepRoutine.state.override,
            sleeping=runtime.sleepRoutine.state.blocksNoise || fitting?.programs?.find { it.slot == requested.program }?.name?.trim()?.equals("sleep",true) == true)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        load()
        // Android removes alarms after force-stop. Reopening restores future
        // occurrences only; never disturb an operation already owned by the service.
        if(AutomaticSleepService.running==null) runCatching { AutomaticSleepScheduler(this).refresh() }
        if(android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = android.window.OnBackInvokedCallback { leavePage() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,it)
            }
        }
        androidx.core.view.WindowCompat.enableEdgeToEdge(window)
        tts = TextToSpeech(this) { code ->
            speechReady = code == TextToSpeech.SUCCESS
            if(speechReady) tts?.language = Locale.US
        }
        tts?.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener() {
            private fun finished(id: String?) { runOnUiThread { if(id=="heysig-result-$speechId") { foregroundSpeechDeadline=0;HandsFreeService.running?.resumeWhenQuiet() } } }
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finished(id) }
            @Deprecated("Platform callback") override fun onError(id: String?) { finished(id) }
            override fun onStop(id: String?,interrupted: Boolean) { finished(id) }
        })
        show(if(intent?.action=="com.heysig.OPEN_HANDSFREE") "Voice" else "Home")
    }
    override fun onResume() {
        super.onResume(); resumed = true;
        runtime.stateChanged={
            if(resumed && page in listOf("Home","Volume","Balance","Tinnitus","Batteries","Sleep") && !(page=="Sleep" && setupChanged?.invoke()==true)) { seenRuntimeRevision=runtime.revision;message=runtime.lastResult;show() }
        }
        if(seenRuntimeRevision!=runtime.revision && page in listOf("Home","Volume","Balance","Tinnitus","Batteries","Sleep") && !(page=="Sleep" && setupChanged?.invoke()==true)) { seenRuntimeRevision=runtime.revision;message=runtime.lastResult;show() }
        HandsFreeService.stopNoise={ noise.stop() }
        HandsFreeService.externalSpeechBusy={ tts?.isSpeaking==true || SystemClock.elapsedRealtime()<foregroundSpeechDeadline }
        HandsFreeService.statusListener={ _ ->
            if(resumed && page=="Voice") {
                if(voiceSessionPresent != (HandsFreeService.running!=null) || speechSummary?.text?.toString()!=speechStatusText()) show()
                else voiceSessionStatus?.text=HandsFreeService.running?.status ?: "Hands-free is Off"
            }
            handsFreeStatus?.text=HandsFreeService.running?.status ?: "Hands-free Off · Tap to set up"
            handsFreeStatus?.contentDescription="${handsFreeStatus?.text}. Open Voice and speech"
            speechSummary?.text=speechStatusText()
        }
        HandsFreeService.statusListener?.invoke(HandsFreeService.running?.status ?: "Hands-free is Off")
         companion.resume()
        runCatching { watchHistory.prune() }.onFailure { tell("Watch history could not be expired; watch commands remain blocked until storage works") }
    }
    override fun onPostResume() {
        super.onPostResume()
        if(intent.getBooleanExtra("openSleep",false)) { intent.removeExtra("openSleep");navigate("Sleep") }
        pendingExactSleepRevision?.let { revision ->
            pendingExactSleepRevision=null
            if(AutomaticSleepScheduler(this).exactAllowed()) continueAutomaticSleep(revision)
            else { show("Sleep");tell("Automatic stays Off. Allow Alarms & reminders, then choose Start automatic routine.",true) }
        }
        pendingText?.let { pendingText = null; route(it) }
        if(pendingHandsFreeStart && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED &&
            (android.os.Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            pendingHandsFreeStart=false;startHandsFree()
        }
    }
    override fun onPause() { noise.stop(); if(companionDelegate.isInitialized()) companion.pause(); resumed = false; pendingVoice = false; if(!runtime.backgroundExecution) cancelled.set(true); tts?.stop(); runtime.stateChanged=null; HandsFreeService.stopNoise=null; HandsFreeService.externalSpeechBusy=null; HandsFreeService.statusListener=null; super.onPause() }
    override fun onDestroy() {
        if(android.os.Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        noise.stop(); if(companionDelegate.isInitialized()) companion.pause(); exportDocument = null; if(!runtime.backgroundExecution) cancelled.set(true); tts?.shutdown(); super.onDestroy()
    }
    @Deprecated("Fallback for Android 10–12; newer versions use OnBackInvokedDispatcher")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ dispatcher is registered in onCreate; retain the pre-33 framework fallback.
    override fun onBackPressed() { leavePage() }
    private fun leavePage() {
        when {
            page == "NoiseMeasure" -> navigate("NoiseSetup")
            page.startsWith("Noise") && page != "Noise" -> navigate("Noise")
            page == "VoiceTools" -> navigate("Voice")
            page in listOf("Setup","Voice","Watch","Privacy","Saved","Sleep","GettingStarted") -> navigate("Settings")
            page == "Home" -> finish()
            else -> navigate("Home")
        }
    }

    private fun load() { message=runtime.lastResult }
    private fun encode(state: RequestedState) = runtime.encode(state)
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
        if(target=="Setup") HandsFreeService.running?.control(HandsFreeControl.END)
        if(page in listOf("Setup","Sleep") && target != page && setupChanged?.invoke() == true) {
            android.app.AlertDialog.Builder(this).setTitle(if(page=="Sleep") "Sleep routine changes are not saved" else "Setup changes are not saved")
                .setMessage("Save the reviewed choices before leaving, or discard the changes.")
                .setPositiveButton(if(page=="Sleep") "Save routine" else "Save setup") { _,_ -> saveSetupAction?.invoke() }
                .setNegativeButton("Discard") { _,_ -> show(target) }
                .setNeutralButton("Keep editing",null).show()
        } else show(target)
    }
    private fun show(destination: String = page) {
        try { noise.detachEditor() } catch(e:Exception) { tell("Noise draft: ${e.message}. Correct the field or discard noise changes."); return }
        page = destination
        voiceSessionStatus = null
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
        if(page !in listOf("Home","Noise","Settings")) button("‹ Back") { leavePage() }
        val title = when(page) {
            "Home" -> "HeySig"
            "Noise" -> "Noise"
            "NoiseCalibration" -> "Calibration"
            "NoiseSetup" -> "Noise programs"
            "NoiseMeasure" -> "Measurement"
            "NoiseDetails" -> "Noise details"
            "Volume" -> "Volume"
            "Balance" -> "Balance"
            "Tinnitus" -> "Tinnitus"
            "Batteries" -> "Batteries"
            "Saved" -> "Saved settings"
            "Setup" -> "Aid setup"
            "GettingStarted" -> "Getting started"
            "Sleep" -> "Sleep routine"
            "Voice" -> "Voice & speech"
            "VoiceTools" -> "Voice tools"
            "Watch" -> "Watch connection"
            "Privacy" -> "Privacy & data"
            else -> "Settings"
        }
        line(title).apply { PhoneStyle.text(this,minOf(25f,40f/resources.configuration.fontScale),true); isAccessibilityHeading = true }
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PhoneStyle.dp(context,4),PhoneStyle.dp(context,4),PhoneStyle.dp(context,4),PhoneStyle.dp(context,12))
            content.addView(this)
        }
        val resultRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        statusRow=resultRow; statusCard.addView(resultRow)
        status = TextView(this).apply {
            text = message; PhoneStyle.text(this,14f,color=PhoneStyle.secondary)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            resultRow.addView(this,LinearLayout.LayoutParams(0,-2,1f))
        }
        resultRow.visibility=if(message == "Ready. Nothing is sent until you ask.") android.view.View.GONE else android.view.View.VISIBLE
        heardStatus = TextView(this).apply {
            text = if(lastHeard.isEmpty()) "" else "Heard: $lastHeard"
            PhoneStyle.text(this,14f,color=PhoneStyle.secondary)
            visibility = if(lastHeard.isEmpty() || page !in listOf("Voice","VoiceTools")) android.view.View.GONE else android.view.View.VISIBLE
            statusCard.addView(this)
        }
        when(page) {
            "Home" -> home()
            "GettingStarted" -> gettingStarted()
            "Setup" -> section("Your hearing-aid setup") { setup() }
            "Saved" -> section("Your saved snapshot") { snapshot() }
            "Sleep" -> section("Bedtime & waking") { sleepPage() }
            "Voice" -> section("Voice & speech") { voicePage() }
            "VoiceTools" -> section("Voice tools") { voiceToolsPage() }
            "Noise", "NoiseCalibration", "NoiseSetup", "NoiseMeasure", "NoiseDetails" -> noise.render(content,page) { navigate(it) }
            "Volume" -> levelPage(LevelControl.VOLUME)
            "Balance" -> levelPage(LevelControl.SHARPNESS)
            "Tinnitus" -> levelPage(LevelControl.TINNITUS)
            "Batteries" -> batteryPage()
            "Watch" -> section("Your watch") { line("To check the watch’s Wi-Fi, open HeySig on the watch → More → Check Wi-Fi. Check phone link there requests status without changing the aids."); if(resumed) companion.resume(); companion.render(content) }
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
        val speak = Button(this).apply { setText(R.string.speak); contentDescription = "Speak command"; setOnClickListener { beginVoice() }; PhoneStyle.action(this,"primary") }
        val stop = Button(this).apply {
            setText(R.string.stop); PhoneStyle.action(this,"danger")
            setOnClickListener { runCatching { AutomaticSleepScheduler(this@HomeActivity).disable() };HandsFreeService.running?.control(HandsFreeControl.OFF); noise.stop(); tts?.stop(); pendingProgram = null; pendingRestore = null; runtime.personalPhrases.cancel(); runtime.sleepRoutine.cancel(); runtime.fittingWorkflow.cancel(); pendingVoice = false; cancelled.set(true); tell("Stop requested. Automatic sleep is Off. Already emitted tones cannot be undone.") }
        }
        actions.addView(speak,LinearLayout.LayoutParams(0,-2,2f).apply { marginEnd = PhoneStyle.dp(this@HomeActivity,10) })
        actions.addView(stop,LinearLayout.LayoutParams(0,-2,1f))
        shell.addView(actions)
        val speechLabel = TextView(this).apply {
            text = speechStatusText()
            PhoneStyle.text(this,12f,color=PhoneStyle.secondary); gravity = android.view.Gravity.CENTER
            setPadding(PhoneStyle.dp(context,12),0,PhoneStyle.dp(context,12),PhoneStyle.dp(context,6))
        }; speechSummary = speechLabel; shell.addView(speechLabel)
        handsFreeStatus=TextView(this).apply {
            text=HandsFreeService.running?.status ?: "Hands-free Off · Tap to set up"; PhoneStyle.text(this,12f,color=PhoneStyle.secondary)
            gravity=android.view.Gravity.CENTER
            minHeight=PhoneStyle.dp(context,48)
            isClickable=true; isFocusable=true
            contentDescription="${text}. Open Voice and speech"
            setOnClickListener { navigate("Voice") }
            accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { shell.addView(it) }
        val tabs = listOf("Home","Noise","Settings")
        val selected = when { page.startsWith("Noise") -> "Noise"; page in listOf("Volume","Balance","Tinnitus","Batteries") -> "Home"; page in tabs -> page; else -> "Settings" }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.WHITE) }
        val count = 3
        tabs.chunked(count).forEach { rowTabs ->
            val row = LinearLayout(this)
            rowTabs.forEach { target ->
                val active = selected == target
                val tab = Button(this).apply {
                    text = target; isEnabled = !busy.get(); tag = "navigation"
                    PhoneStyle.text(this,12f,active,if(active) PhoneStyle.blue else PhoneStyle.secondary)
                    isAllCaps = false; minHeight = PhoneStyle.dp(context,52); minimumHeight = minHeight
                    setPadding(PhoneStyle.dp(context,4),PhoneStyle.dp(context,8),PhoneStyle.dp(context,4),PhoneStyle.dp(context,8))
                    background = PhoneStyle.shape(context,android.graphics.Color.WHITE,0)
                    if(resources.configuration.fontScale <= 1.3f) setCompoundDrawables(null,PhoneStyle.icon(context,target,if(active) PhoneStyle.blue else PhoneStyle.secondary),null,null)
                    compoundDrawablePadding = PhoneStyle.dp(context,5)
                    alpha = if(isEnabled) 1f else .45f
                    isSelected = active; contentDescription = "$target${if(active) ", selected" else ""}"
                    setOnClickListener { navigate(target) }
                }
                row.addView(tab,LinearLayout.LayoutParams(0,-2,1f))
            }; navigation.addView(row)
        }; shell.addView(navigation)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            val keyboardVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            navigation.visibility = if(keyboardVisible) android.view.View.GONE else android.view.View.VISIBLE
            speechLabel.visibility = if(keyboardVisible) android.view.View.GONE else android.view.View.VISIBLE
            insets
        }
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }
    private var pendingRefresh = false
    private fun settingsPage() {
        section("Your preferences") {
            button("Getting started") { navigate("GettingStarted") }
            button("Sleep routine") { navigate("Sleep") }
            button("Saved settings") { navigate("Saved") }
            button("Battery check") { navigate("Batteries") }
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
    }
    private fun programLabel(slot: Int?) = fitting?.programs?.find { it.slot == slot }?.let { "${it.name}, program ${it.slot}" } ?: "unknown"
    private fun stateText(state: RequestedState) = "Program: ${programLabel(state.program)}\n" +
        LevelControl.entries.joinToString("\n") { "${when(it) { LevelControl.VOLUME -> "Volume"; LevelControl.SHARPNESS -> "Balance"; LevelControl.TINNITUS -> "Tinnitus" }}: ${state.levels[it]?.toString() ?: "not set"}" }
    private fun gettingStarted() {
        section("1. Review your hearing aids") {
            line(if(fitting == null) "Needs setup. Use the addresses and programs verified for your aids. Do not guess fitting values." else "Setup saved. Your existing programs and settings are retained.")
            button(if(fitting == null) "Set up hearing aids" else "Review aid setup") { navigate("Setup") }
        }
        section("2. Use everyday controls") {
            line("Choose a program on Home, then adjust its levels. Keep HeySig open and unlocked, use the built-in speaker and check media volume and Do Not Disturb. Completed playback means sent, not confirmed by your aids.")
            button("Open Home") { navigate("Home") }
            button("Check device audio",!busy.get()) { try { player.inspect();tell("Speaker audio checks passed. No command sent.",true) } catch(e:Exception) { tell("Blocked: ${e.message}",true) } }
        }
        section("3. Choose optional help") {
            line("Hands-free: enable a phone listening session, then say Hey Sig, pause and give a command. Microphone use is explained before you enable it. The watch still needs Speak or a gesture.")
            button("Set up voice & hands-free") { navigate("Voice") }
            line("Sleep: save bedtime and morning programs, then enable reminders. A reminder asks you to open HeySig and confirm a switch; programs do not change automatically.")
            button("Set up bedtime reminders") { navigate("Sleep") }
            line("Noise: measure your surroundings and preview suggested programs without changing your aids. Keep the app open and unlocked during measurement. Automatic switching is unavailable in this build.")
            button("Explore noise preview") { navigate("Noise") }
        }
    }
    private fun home() {
        if(fitting == null) {
            PhoneStyle.heading(this,content,"Welcome to HeySig")
            line("Start with your verified hearing-aid setup. Then choose a program. Voice, bedtime reminders and noise previews are optional.")
            button("See setup checklist") { navigate("GettingStarted") }
            PhoneStyle.action(button("Set up hearing aids") { navigate("Setup") },"primary")
            return
        }
        val current = fitting?.programs?.find { it.slot == requested.program }?.name ?: "Choose program"
        val programButton = button("$current ▾",!busy.get()) {
            val available = fitting?.programs.orEmpty()
            android.app.AlertDialog.Builder(this).setTitle("Select program")
                .setItems(available.map { it.name }.toTypedArray()) { _,index -> dispatch(ManualRequest.SelectProgram(available[index].slot)) }
                .setNegativeButton("Cancel",null).show()
        }
        PhoneStyle.action(programButton,"primary")
        programButton.contentDescription = "Change program. Program: $current"
        val shortcuts = listOf("Volume" to "Volume","Tinnitus" to "Tinnitus","Balance" to "Balance","Refresh" to "Refresh")
        shortcuts.chunked(2).forEach { entries ->
            val row = LinearLayout(this)
            entries.forEach { (label,destination) ->
                val item = Button(this).apply {
                    text = label; PhoneStyle.action(this)
                    textSize=minOf(16f,24f/resources.configuration.fontScale)
                    setOnClickListener { if(destination == "Refresh") confirmRefresh() else navigate(destination) }
                }
                row.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=PhoneStyle.dp(this@HomeActivity,6) })
            }
            content.addView(row)
        }
        repeatRequest?.let { request -> button("Send last target again",!busy.get()) { dispatch(request,true) } }
        fitting?.let { fit -> content.addView(SigSceneCard(this,fit,requested) { navigate("VoiceTools") }) }
    }
    private fun levelPage(control: LevelControl) {
            val title = when(control) { LevelControl.VOLUME -> "Volume"; LevelControl.SHARPNESS -> "Balance"; LevelControl.TINNITUS -> "Tinnitus" }
            val supported = control != LevelControl.TINNITUS || fitting?.programs?.find { it.slot == requested.program }?.tinnitus == TinnitusCapability.SUPPORTED
            val enabled = !busy.get() && requested.program != null && supported
            section(title) {
                val last = requested.levels[control]
                line("Status: ${last?.toString() ?: "not set"}")
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
                            draft = control.range.first+progress; value.text = getString(R.string.integer_value,draft.toString())
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
    private fun batteryPage() {
        if(fitting != null) section("Battery indications","Ask your aids for their usual battery indication.") {
            button("Check both batteries",!busy.get()) { dispatch(ManualRequest.Battery(true,true)) }
            button("Check left battery",!busy.get()) { dispatch(ManualRequest.Battery(true,false)) }
            button("Check right battery",!busy.get()) { dispatch(ManualRequest.Battery(false,true)) }
        }
    }
    private fun chooseFittingImport() {
            android.app.AlertDialog.Builder(this).setTitle("Review an exported fitting?")
                .setMessage("Only fitting addresses, program names and tinnitus capability are read. Imported files may contain private settings. Review the draft before saving; no command is sent. Saving a changed fitting archives previous settings and clears active assumptions.")
                .setNegativeButton("Cancel",null).setPositiveButton("Choose export") { _,_ ->
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type="application/json";addCategory(Intent.CATEGORY_OPENABLE)
                    },404)
                }.show()
    }
    private fun choosePhraseExport() {
            try {
                val document=runtime.personalPhrases.exportReviewed(requireNotNull(fitting) { "Save aid setup first" })
                android.app.AlertDialog.Builder(this).setTitle("Export personal phrases?")
                    .setMessage("This exports active phrase text and meanings separately from app settings. Your chosen provider may upload the file; HeySig cannot delete exported copies.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Choose location") { _,_ ->
                        exportDocument=document
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"heysig-phrases.json")
                        },403)
                    }.show()
            } catch(e: Exception) { tell(e.message ?: "Cannot export phrases") }
    }
    private fun setup() {
        line("Voice setup: configure hearing aids • setup help • review aid setup • save aid setup • cancel aid setup. Changed setups archive previous settings locally (up to 20), also included in local export.")
        line("Review and save. For your tested fitting, addresses were 12. This is not a universal default. Blank program names disable those slots. Tinnitus support must be confirmed per program.")
        button("Import fitting for review",!busy.get()) { chooseFittingImport() }
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
                val changed = next != fitting
                check(runtime.saveFitting(next)) { "Setup could not be saved; previous setup retained. Archive limit is 20; export and review archives if full." }
                runtime.fittingWorkflow.cancel()
                runCatching { SleepReminders(this).refresh() }
                if(changed) noise.fittingChanged()
                message = "Setup saved. ${if(changed) "Previous settings archived; active assumptions cleared." else "Setup unchanged."} No playback."
                show("Home")
            } catch(e:Exception) { tell("Setup: ${e.message}",true) }
        }
        saveSetupAction = { saveButton.performClick(); Unit }
    }
    private fun snapshot() {
        line(saved?.let { "Saved requested settings\n${stateText(it)}\nUnknown fields will not be sent." } ?: runtime.savedIssue ?: "No saved settings")
        button("Save requested settings",!busy.get()) { saveRequested() }
        line("Only Save requested settings replaces this snapshot. Program changes, slider commands, battery checks and interrupted playback leave it intact. Status values are remembered separately. This app cannot read external changes made in Signia.")
    }
    private fun saveRequested() {
        val result=runtime.saveSnapshot()
        if(page == "Saved") show()
        tell(result,true)
    }
    private fun sleepPage() {
        val fit=fitting ?: run { line("Save aid setup before choosing a sleep routine."); return }
        val store=runtime.sleepRoutine
        val ready=runCatching { store.target(true,fit);store.target(false,fit) }.isSuccess
        val remindersOn=getSharedPreferences("heysig-sleep-v1",MODE_PRIVATE).getBoolean("reminders",false)
        val automatic=AutomaticSleepScheduler(this)
        section("Your bedtime routine") {
            line(if(automatic.enabled && (!automatic.exactAllowed() || !automatic.notificationsAllowed()))
                "Automatic is blocked by alarm or notification access. Review and start it again." else store.dashboard(fit))
            PhoneStyle.action(button(if(automatic.enabled) "Review automatic routine" else "Start automatic routine",ready && !busy.get()) { reviewAutomaticSleep() },"primary")
            if(automatic.enabled) button("Stop automatic routine") { tell(automatic.disable(),true);show("Sleep") }
            line("Automatic switches to your saved bedtime and morning programs without a tap, including while the phone is locked. The phone speaker must be near the aids, media volume at least halfway, Do Not Disturb off and external audio disconnected. A blocked or late action is not retried.")
            if(automatic.enabled && (!automatic.exactAllowed() || !automatic.notificationsAllowed())) line("Blocked: review Alarms & reminders and notification access before starting automatic again.")
            getSharedPreferences("heysig-sleep-v1",MODE_PRIVATE).getString("automaticResult",null)?.let { line("Last automatic result: $it") }
            if(ready) {
                val choices=store.choices(fit)
                line("Sleep now: ${fit.programs.first { it.slot==choices.sleepSlot }.name}. Wake now: ${fit.programs.first { it.slot==choices.wakeSlot }.name}.")
            } else line("Choose and save both programs below before starting.")
            button("Sleep now",ready && !busy.get()) { confirmSleep(true) }
            button("Wake now",ready && !busy.get()) { confirmSleep(false) }
            line("These buttons ask before switching immediately. They do not start a schedule, power off or mute the aids.")
            store.actionResult()?.let { line("Last program result: $it") }
            button("Check device audio",!busy.get()) {
                try { player.inspect();tell("Speaker audio checks passed. No command sent.",true) }
                catch(e:Exception) { tell("Blocked: ${e.message}",true) }
            }
        }
        val reminderOptions=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE }
        button("Reminder options") { reminderOptions.visibility=if(reminderOptions.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE }
        content.addView(reminderOptions)
        val mainContent=content;content=reminderOptions
        try {
            PhoneStyle.heading(this,content,"Ask me to tap instead")
            button(if(remindersOn && store.state.override) "Resume routine reminders" else "Start routine reminders",ready && !busy.get()) { requestSleepReminder() }
            if(remindersOn) button("Stop reminders") { val result=SleepReminders(this).setEnabled(false);show("Sleep");tell(result,true) }
            button("Test reminder") { requestSleepReminder(test=true) }
            line("Test reminder posts a notification now without changing a program or your saved schedule. Daily reminders open this screen so you can choose Sleep now or Wake now.")
            if(!SleepReminders(this).available()) {
                line("Blocked: Android notifications are off. Allow HeySig notifications, including the Sleep routine reminders channel, in Android settings.")
                button("Open notification settings") { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
            }
            line("Start also clears the manual pause for future reminders. Times are approximate; Android can delay delivery. A reminder more than 15 minutes late is skipped and recorded below.")
            getSharedPreferences("heysig-sleep-v1",MODE_PRIVATE).getString("reminderResult",null)?.let { line("Last reminder result: $it") }
        } finally { content=mainContent }
        val routineParent=content
        val routineEditor=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=if(ready) android.view.View.GONE else android.view.View.VISIBLE
        }
        val editRoutine=button(if(ready) "Edit bedtime & morning routine" else "Set up bedtime & morning routine") { }
        editRoutine.setOnClickListener {
            val opening=routineEditor.visibility != android.view.View.VISIBLE
            routineEditor.visibility=if(opening) android.view.View.VISIBLE else android.view.View.GONE
            editRoutine.text=if(opening) "Hide routine editor" else if(ready) "Edit bedtime & morning routine" else "Set up bedtime & morning routine"
        }
        routineParent.addView(routineEditor)
        content=routineEditor
        try {
        PhoneStyle.heading(this,content,"Choose bedtime and morning programs")
        line("Review your choices, then choose Save & start automatic or Save only. Saving alone leaves schedules Off.")
        val initial=store.choices(fit)
        val revision=store.editorRevision()
        var sleepMinute=initial.sleepMinute
        var wakeMinute=initial.wakeMinute
        lateinit var sleepTime: Button
        lateinit var wakeTime: Button
        sleepTime=button("Bedtime: ${SleepRoutine.clock(sleepMinute)}") {
            android.app.TimePickerDialog(this,{ _,hour,minute -> sleepMinute=hour*60+minute; sleepTime.text="Bedtime: ${SleepRoutine.clock(sleepMinute)}" },sleepMinute/60,sleepMinute%60,true).show()
        }
        wakeTime=button("Morning: ${SleepRoutine.clock(wakeMinute)}") {
            android.app.TimePickerDialog(this,{ _,hour,minute -> wakeMinute=hour*60+minute; wakeTime.text="Morning: ${SleepRoutine.clock(wakeMinute)}" },wakeMinute/60,wakeMinute%60,true).show()
        }
        val programs=fit.programs
        fun picker(label: String,slot: Int?) = Spinner(this).apply {
            line(label);contentDescription=label
            adapter=ArrayAdapter(this@HomeActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Choose a program")+programs.map { "${it.slot}: ${it.name}" })
            setSelection(programs.indexOfFirst { it.slot==slot }.let { if(it<0) 0 else it+1 });content.addView(this)
        }
        val sleep=picker("Bedtime program",initial.sleepSlot)
        val wake=picker("Morning return program",initial.wakeSlot)
        fun choices()=SleepRoutine(sleepMinute,wakeMinute,programs.getOrNull(sleep.selectedItemPosition-1)?.slot,programs.getOrNull(wake.selectedItemPosition-1)?.slot)
        setupChanged={ choices()!=initial }
        val commitRoutine: (Boolean) -> Unit = { start ->
            if(fitting != fit) tell("Aid setup changed. Reopen and review the sleep routine.",true)
            else {
                val result=store.save(choices(),fit,revision)
                runCatching { SleepReminders(this).refresh() }
                runCatching { AutomaticSleepScheduler(this).refresh() }
                if(result.startsWith("Routine saved.")) {
                    if(start) continueAutomaticSleep(store.editorRevision()) else { show("Sleep");tell(result,true) }
                } else tell(result,true)
            }
        }
        val save: () -> Unit = {
            val selected=choices()
            runCatching { selected.validate(fit) }.fold(onSuccess={
                val bedtime=fit.programs.first { it.slot==selected.sleepSlot }.name
                val morning=fit.programs.first { it.slot==selected.wakeSlot }.name
                android.app.AlertDialog.Builder(this).setTitle("Review bedtime routine")
                    .setMessage("Bedtime ${SleepRoutine.clock(selected.sleepMinute)}: switch to $bedtime.\nMorning ${SleepRoutine.clock(selected.wakeMinute)}: switch to $morning.\n\nSave & start automatic plays the saved programs and retained known targets at future times, even while locked, without another tap. Phone speaker must be near the aids; media volume at least halfway, DND off, external audio disconnected. Other people may hear the tones. Blocked, interrupted or more than two minutes late actions are not retried. Manual changes pause automatic. Save only leaves schedules Off.")
                    .setNegativeButton("Keep editing",null).setNeutralButton("Save only") { _,_ -> commitRoutine(false) }
                    .setPositiveButton("Save & start automatic") { _,_ -> commitRoutine(true) }.show()
            },onFailure={ tell(it.message ?: "Choose both programs and different times",true) })
        }
        saveSetupAction=save
        PhoneStyle.action(button("Review & save routine") { save() },"primary")
        button("Discard routine changes") { store.cancel();show("Sleep") }
        } finally { content=routineParent }
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=android.view.View.GONE }
        val toggle=button("Advanced & troubleshooting") { }
        toggle.setOnClickListener {
            val opening=details.visibility != android.view.View.VISIBLE
            details.visibility=if(opening) android.view.View.VISIBLE else android.view.View.GONE
            toggle.text=if(opening) "Hide advanced & troubleshooting" else "Advanced & troubleshooting"
        }
        content.addView(details)
        val parent=content;content=details
        try {
            line(store.status(fit))
            line(store.preview(fit))
            line("Manual program or level changes pause future reminders. Resume allows future reminders; it does not send a program command. Android may delay reminders, and reminders over 15 minutes late are skipped.")
            button(if(store.state.override) "Resume sleep reminders" else "Pause sleep reminders",!busy.get()) {
                tell(store.handle(if(store.state.override) SleepCommand.Resume else SleepCommand.Override,fit,"phone"),true);show("Sleep")
            }
            line("No reminder? Check that reminders are On, the routine is not paused, and Android allows HeySig notifications. No switch? Read the result at the top of this screen after confirming Switch.")
        } finally { content=parent }

    }
    private fun reviewAutomaticSleep() {
        val fit=fitting ?: return
        val store=runtime.sleepRoutine
        val targets=runCatching { store.target(true,fit) to store.target(false,fit) }.getOrElse { tell(it.message ?: "Save both programs first",true);return }
        val choices=store.choices(fit)
        val revision=store.editorRevision()
        android.app.AlertDialog.Builder(this).setTitle("Start automatic program switching?")
            .setMessage("Bedtime ${SleepRoutine.clock(choices.sleepMinute)}: ${fit.programs.first { it.slot==targets.first }.name}.\nMorning ${SleepRoutine.clock(choices.wakeMinute)}: ${fit.programs.first { it.slot==targets.second }.name}.\n\nAt future scheduled times HeySig plays these programs and their retained known targets without a tap, including while locked. Keep the phone speaker near the aids, media volume at least halfway, Do Not Disturb off and external audio disconnected. Other people may hear the tones.\n\nManual changes pause automatic. Blocked, interrupted or more than two minutes late actions are not retried. Use Stop automatic in the notification to cancel future actions and request cancellation of playback. Emitted tones cannot be undone. No microphone or audio recording is started.")
            .setNegativeButton("Cancel",null).setPositiveButton("Enable automatic") { _,_ -> continueAutomaticSleep(revision) }.show()
    }
    private fun continueAutomaticSleep(revision: Long) {
        if(runtime.sleepRoutine.editorRevision()!=revision) { tell("Routine changed. Review automatic switching again.",true);return }
        val scheduler=AutomaticSleepScheduler(this)
        if(!scheduler.exactAllowed()) {
            pendingExactSleepRevision=revision
            try { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:$packageName"))) }
            catch(e: Exception) { pendingExactSleepRevision=null;tell("Open Android app settings and allow Alarms & reminders, then start automatic again.",true) }
            return
        }
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingNotificationSleepRevision=revision
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),767);return
        }
        val result=scheduler.enable(revision)
        show("Sleep");tell(result,true)
    }
    private fun requestSleepReminder(test: Boolean=false) {
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.app.AlertDialog.Builder(this).setTitle(if(test) "Test sleep notification" else "Sleep routine reminders")
                .setMessage(if(test) "Allow notifications to post one test reminder now. This will not start daily reminders or change your hearing-aid program." else "Allow bedtime and morning reminders. Each opens HeySig so you can choose a program switch. No microphone or automatic playback starts. Android may delay reminders.")
                .setNegativeButton("Not now",null).setPositiveButton("Allow") { _,_ -> requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),if(test) 766 else 765) }.show()
        } else {
            if(!test) AutomaticSleepScheduler(this).disable()
            val result=if(test) SleepReminders(this).testReminder() else SleepReminders(this).setEnabled(true)
            show("Sleep");tell(result,true)
        }
    }

    private fun voicePage() {
        PhoneStyle.heading(this,content,"Speech on phone and watch")
        line("These switches control spoken prompts and replies on both your phone and watch. Both can be Off; text status stays visible. Speech engines may use their network services.")
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
                    if(!checked) { tts?.stop(); HandsFreeService.running?.stopSpokenReply() }
                }
            })
        }
        line("Prompts give a listening cue and guide confirmations. Responses read results and errors. The watch follows these choices for each command.")
        voiceSessionPresent=HandsFreeService.running!=null
        voiceSessionStatus=line(HandsFreeService.running?.status ?: "Hands-free is Off").apply {
            accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        button(if(voiceSessionPresent) "Resume listening" else "Start hands-free session",!busy.get()) { startHandsFree() }
        line("Say Hey Sig, wait for Ready or a vibration, then say your command. Try: current program. Each new command needs Hey Sig again.")
        if(voiceSessionPresent) {
            button("Pause for 30 minutes") { HandsFreeService.running?.control(HandsFreeControl.PAUSE) }
            button("Turn listening Off") { HandsFreeService.running?.control(HandsFreeControl.OFF) }
            button("End hands-free session") { HandsFreeService.running?.control(HandsFreeControl.END); show() }
        }
        line("Off stops the microphone and shake. Resume turns them back on. End closes the session; start again here after restarting the app. Sessions last up to eight hours.")
        val needsMic=checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED
        val needsNotifications=android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED
        if(needsMic || needsNotifications) line("Hey Sig needs: "+listOfNotNull(if(needsMic) "microphone permission" else null,if(needsNotifications) "notification permission" else null).joinToString(" and ")+". Choose Start hands-free session to review microphone use and allow access. No listening starts just by opening this screen.")
        content.addView(CheckBox(this).apply {
            text="Shake to speak"
            val choices=getSharedPreferences("heysig-handsfree",MODE_PRIVATE)
            isChecked=choices.getBoolean("shake",false)
            var reverting=false
            setOnCheckedChangeListener { box,checked ->
                if(!reverting) {
                    if(choices.edit().putBoolean("shake",checked).commit()) HandsFreeService.running?.refreshShake()
                    else { reverting=true;box.isChecked=!checked;reverting=false;tell("Could not save shake choice") }
                }
            }
        })
        line("While listening is On, shake three times, wait for Ready or a vibration, then speak. Movement can trigger it accidentally.")
        button("Voice tools & personal phrases") { navigate("VoiceTools") }
    }
    private fun voiceToolsPage() {
        button("Test voice recognition",!busy.get()) {
            noise.stop();tts?.stop();launchVoice(testOnly=true)
        }
        line("Say testing one two three. This test displays the words without running a command or changing aid settings.")
        button("Open microphone & app settings") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
        }
        line(PrivacyNotice.SYNTHESIS)
        line("Use your installed speech service, or type a command below. Recognition may use the service's network connection. Cancel sends nothing.")
        PhoneStyle.heading(this,content,"Personal phrases")
        line("Teach an exact phrase with: learn phrase TV time means program TV. Review the proposed mapping, then say remember that correction. Only the confirmed phrase and meaning are stored locally; audio is not saved. Learning sends no command. Use the phrase separately afterward. Setup changes deactivate old phrases.")
        content.addView(CheckBox(this).apply {
            text = "Use learned phrases"; isChecked = runtime.personalPhrases.enabled()
            setOnCheckedChangeListener { _,checked ->
                if(!runtime.personalPhrases.enable(checked)) { tell("Could not save phrase choice"); show() }
            }
        })
        line("Program aliases use the same reviewed phrase workflow: when I say movie time I mean program TV. Then say remember that correction. A correction never replays a command.")
        button("Export active personal phrases",!busy.get()) { choosePhraseExport() }
        button("Import personal phrases for review",!busy.get()) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE) },405)
        }
        button("List learned phrases") { route("list my phrases") }
        button("Add personal phrase",!busy.get()) {
            runtime.personalPhrases.cancel()
            val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val phrase = EditText(this).apply { hint = "Personal phrase, e.g. TV time"; setSingleLine(); fields.addView(this) }
            val target = EditText(this).apply { hint = "Meaning, e.g. program TV"; setSingleLine(); fields.addView(this) }
            android.app.AlertDialog.Builder(this).setTitle("Teach a personal phrase").setView(fields)
                .setNegativeButton("Cancel",null).setPositiveButton("Review") { _,_ ->
                    route("learn phrase ${phrase.text} means ${target.text}")
                }.show()
        }
        button("Remember reviewed correction") { route("remember that correction") }
        val forget = EditText(this).apply { hint = "Exact phrase to forget"; setSingleLine(); content.addView(this) }
        button("Forget phrase") { route("forget phrase ${forget.text}") }
        button("Clear all learned phrases") {
            route("reset learned phrases")
            android.app.AlertDialog.Builder(this).setTitle("Delete all learned phrases?")
                .setMessage("This deletes only personal phrases. No hearing-aid command is sent.")
                .setNegativeButton("Cancel") { _,_ -> runtime.personalPhrases.cancel() }
                .setOnCancelListener { runtime.personalPhrases.cancel() }
                .setPositiveButton("Delete phrases") { _,_ -> route("confirm reset learned phrases") }.show()
        }
        PhoneStyle.heading(this,content,"Type a command")
        val input = EditText(this).apply { hint = "Try: program two"; setSingleLine(); content.addView(this) }
        button("Run typed command",!busy.get()) { route(input.text.toString()) }
        line("Programs by name or number • volume 0–15 • balance −8 to +7 • tin 0–15 • current program/volume/balance/tin • battery check • refresh (resend current configuration) • save settings • restore settings • cancel.\nNoise: configure noise, read noise settings, assign program three to noise level five, set noise threshold one to minus sixty, save noise settings, noise sample, observe noise, noise status, stop noise. Guided calibration and automatic preview are available on Noise. Automatic switching remains locked pending physical acceptance. Sleep routine choices and preview are available in Settings; optional sleep reminders require your tap to request a program change. Use Watch connection for companion setup.")
    }
    private fun privacyPage() {
        PrivacyNotice.sections.forEach { (title,text) -> section(title) { line(text) } }
        button("Offline wake engine licenses") {
            val notices=listOf("LICENSE","ONNXRUNTIME-LICENSE").joinToString("\n\n") { name -> assets.open("handsfree/$name").bufferedReader().use { it.readText() } }
            android.app.AlertDialog.Builder(this).setTitle("Offline wake detector notices").setMessage(notices).setPositiveButton("Close",null).show()
        }
        button("Open public privacy policy") {
            if(BuildConfig.PRIVACY_POLICY_URL.isEmpty()) tell("Public policy is not configured yet. Publisher contact and hosting must be supplied before release.")
            else try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
            catch(e:Exception) { tell("No browser could open the policy") }
        }
        button("Clear heard text and watch history",!busy.get()) {
            if(busy.get() || remoteReply != null) { tell("Wait for the current result or Stop before clearing history"); return@button }
            HandsFreeService.running?.control(HandsFreeControl.END); disconnectCompanion(); noise.stop(); tts?.stop()
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
                            .put("sleepRoutine",JSONObject(getSharedPreferences("heysig-sleep-v1",MODE_PRIVATE).all))
                            .put("handsFreeChoices",JSONObject(getSharedPreferences("heysig-handsfree",MODE_PRIVATE).all))
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
                .setMessage("Android will close HeySig and erase this app's private data, including fitting, requested settings, saved restoration record, noise configuration, watch selection/history and speech choices, and reset its permissions. You will need Setup again. No command is sent to the aids. Watch, provider and exported copies must be deleted separately.")
                .setNegativeButton("Keep data",null).setPositiveButton("Delete local data") { _,_ ->
                    if(busy.get() || remoteReply != null) { tell("Wait for the current result or Stop before deleting data"); return@setPositiveButton }
                    HandsFreeService.running?.control(HandsFreeControl.END); noise.stop(); disconnectCompanion(); tts?.stop()
                    pendingVoice = false; pendingText = null; lastHeard = ""; exportDocument = null
                    val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                    if(!manager.clearApplicationUserData()) tell("Android could not clear local data. Use the app's Android storage settings.")
                }.show()
        }
        button("Open microphone permission settings") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
        }
    }
    private fun startHandsFree() {
        val choice=getSharedPreferences("heysig-handsfree",MODE_PRIVATE)
        if(!choice.getBoolean("disclosureV1",false)) {
            android.app.AlertDialog.Builder(this).setTitle("Enable hands-free, including while locked?")
                .setMessage("HeySig will use the phone microphone continuously during your session to detect Hey Sig on this device. Wake audio is processed in memory and not saved or uploaded. After the phrase, command recognition lasts up to ten seconds. On-device recognition is preferred; an installed service may send command audio to its provider when on-device recognition is unavailable. Spoken replies use your independently optional TTS settings.\n\nCommands can change your hearing-aid program or levels while this phone is locked, through the same validated speaker transport. Background sounds or other people may trigger recognition: this is not speaker authentication. Keep media volume, speaker route and Do Not Disturb configured normally.\n\nYour selected watch may control listening and send commands throughout this session, including while locked. Off releases the microphone but keeps the visible session notification and watch control. End session stops both. Pause resumes after about 30 minutes; Android may delay that timer. Sessions end after eight hours, a crash or reboot, with no automatic replay. No audio or transcript history is added.")
                .setNegativeButton("Not now",null).setPositiveButton("Enable session") { _,_ ->
                    if(choice.edit().putBoolean("disclosureV1",true).putBoolean("watchControlV1",true).commit()) startHandsFree()
                    else tell("Could not save hands-free choice")
                }.show();return
        }
        val permissions=mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        val missing=permissions.filter { checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) { pendingHandsFreeStart=true; requestPermissions(missing.toTypedArray(),764);return }
        if(!resumed) { pendingHandsFreeStart=true;return }
        val notifications=getSystemService(android.app.NotificationManager::class.java)
        if(!notifications.areNotificationsEnabled() || notifications.getNotificationChannel("handsfree-v1")?.importance==android.app.NotificationManager.IMPORTANCE_NONE) {
            tell("Enable HeySig hands-free notifications in Android app settings first. Listening stays Off.");return
        }
        noise.stop();tts?.stop();
        try { HandsFreeService.startFromVisibleActivity(this);tell("Starting hands-free session");show() }
        catch(e: Exception) { tell("Hands-free could not start; use Speak or watch controls") }
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,grants:IntArray) {
        super.onRequestPermissionsResult(code,permissions,grants)
        if(code==767) {
            val revision=pendingNotificationSleepRevision;pendingNotificationSleepRevision=null
            if(revision!=null && grants.isNotEmpty() && grants.all { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) continueAutomaticSleep(revision)
            else { show("Sleep");tell("Automatic stays Off. Visible notifications are required for Stop and results.",true) }
        }
        if(code==765 || code==766) {
            val result=if(grants.isNotEmpty() && grants.all { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                if(code==766) SleepReminders(this).testReminder() else { AutomaticSleepScheduler(this).disable();SleepReminders(this).setEnabled(true) }
            } else "Notification permission was not granted. You can still use Sleep now and Wake now."
            show("Sleep");tell(result,true)
        }
        if(code==764 && pendingHandsFreeStart) {
            pendingHandsFreeStart=false
            if(grants.isNotEmpty() && grants.all { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) startHandsFree()
            else tell("Hands-free stays Off. Microphone and visible-notification permission are needed for this feature.")
        }
    }
    private fun voiceEnabled(key:String) = prefs.getBoolean(key,if(key == "spokenResponses") prefs.getBoolean("spokenResults",BuildConfig.DEBUG) else BuildConfig.DEBUG)
    private fun tell(text: String, speak: Boolean = false) {
        message = text
        val fromWatch = remoteReply != null
        val prompt = runtime.sleepRoutine.isDraftFor(commandOrigin ?: "phone") || pendingProgram != null || pendingRestore != null || (runtime.personalPhrases.awaitingConfirmation || runtime.fittingWorkflow.pending) || text.startsWith("Noise settings.") || text.startsWith("Draft noise")
        if(!busy.get() && remoteReply != null) {
            val reply = remoteReply; remoteReply = null
            activeRemoteNode = null
            reply?.invoke(remoteResult(if(pendingProgram != null || pendingRestore != null || (runtime.personalPhrases.awaitingConfirmation || runtime.fittingWorkflow.pending) || runtime.sleepRoutine.isDraftFor(commandOrigin ?: "phone")) "CLARIFY" else "FINAL",text).put("prompt",prompt))
        }
        if(::status.isInitialized) { statusRow?.visibility = android.view.View.VISIBLE; status.text = text }
        if(speak && !fromWatch && speechReady && resumed && !busy.get() && voiceEnabled(if(prompt) "voicePrompts" else "spokenResponses")) {
            HandsFreeService.running?.suspendMicrophone()
            foregroundSpeechDeadline=SystemClock.elapsedRealtime()+15_000
            val id=++speechId
            if(tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-result-$id")!=TextToSpeech.SUCCESS) foregroundSpeechDeadline=0
            if(HandsFreeService.running!=null) android.os.Handler(mainLooper).postDelayed({
                if(id==speechId && SystemClock.elapsedRealtime()>=foregroundSpeechDeadline) { tts?.stop();foregroundSpeechDeadline=0;HandsFreeService.running?.resumeWhenQuiet() }
            },15_000)
            HandsFreeService.running?.resumeWhenQuiet()
        }
    }
    private fun beginVoice() {
        noise.stop()
        tts?.stop()
        if(busy.get()) { pendingVoice = true; cancelled.set(true); tell("Stopping playback before voice input"); return }
        launchVoice()
    }
    private fun launchVoice(testOnly: Boolean = false) {
        pendingVoice = false
        if(!prefs.getBoolean("speechDisclosureV1",false)) {
            android.app.AlertDialog.Builder(this).setTitle("Optional speech recognition")
                .setMessage(PrivacyNotice.SPEECH).setNegativeButton("Not now",null)
                .setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("speechDisclosureV1",true).commit()) launchVoice(testOnly)
                    else tell("Could not save speech choice")
                }.show()
            return
        }
        try {
            HandsFreeService.manualSpeechBusy=true
            HandsFreeService.running?.suspendMicrophone()
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                if(voiceEnabled("voicePrompts")) putExtra(RecognizerIntent.EXTRA_PROMPT,"HeySig: say a command")
            },if(testOnly) 406 else 401)
        } catch(e:Exception) { HandsFreeService.manualSpeechBusy=false; HandsFreeService.running?.resumeWhenQuiet(); tell("Speech service unavailable. Use typed commands on Voice.",true) }
    }
    @Deprecated("Platform recognition result adapter")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode in listOf(404,405)) {
            if(resultCode == RESULT_OK) try {
                check(!busy.get()) { "Wait for playback to finish" }
                check(pendingProgram == null && pendingRestore == null && !runtime.personalPhrases.awaitingConfirmation && !runtime.sleepRoutine.hasDraft) { "Finish or cancel the current confirmation first" }
                val uri=requireNotNull(data?.data)
                val bytes=contentResolver.openInputStream(uri)?.use { stream ->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(4096)
                    while(output.size() <= 65_536) {
                        val count=stream.read(buffer,0,minOf(buffer.size,65_537-output.size()))
                        if(count < 0) break
                        output.write(buffer,0,count)
                    }
                    output.toByteArray()
                } ?: error("Cannot open export")
                require(bytes.size <= 65_536) { "Setup import is limited to 64 KiB" }
                if(requestCode == 404) {
                    val next=FittingImport.decode(bytes.toString(Charsets.UTF_8))
                    val review=runtime.fittingWorkflow.stage(next,fitting,"phone",SystemClock.elapsedRealtime())
                    val scroll=ScrollView(this).apply {
                        addView(TextView(this@HomeActivity).apply { text=review.replace("Say save aid setup or cancel aid setup.","Choose Save reviewed fitting or Cancel.");setPadding(24,24,24,24);textSize=18f })
                    }
                    android.app.AlertDialog.Builder(this).setTitle("Review imported fitting").setView(scroll)
                        .setNegativeButton("Cancel") { _,_ -> route("cancel aid setup") }
                        .setOnCancelListener { route("cancel aid setup") }
                        .setPositiveButton("Save reviewed fitting") { _,_ ->
                            route("save aid setup")
                            // Rebuild the form from the saved fitting, never the pre-import defaults.
                            show()
                        }.show()
                    tell("Imported fitting ready for review. Nothing saved or sent.",true)
                } else {
                    check(!runtime.fittingWorkflow.pending) { "Finish setup review first" }
                    val review=runtime.personalPhrases.importForReview(bytes.toString(Charsets.UTF_8),requireNotNull(fitting),"phone",System.currentTimeMillis())
                    val scroll=ScrollView(this).apply {
                        addView(TextView(this@HomeActivity).apply { text=review;setPadding(24,24,24,24);textSize=18f })
                    }
                    android.app.AlertDialog.Builder(this).setTitle("Review imported phrases").setView(scroll)
                        .setNegativeButton("Cancel") { _,_ -> runtime.personalPhrases.cancel() }
                        .setOnCancelListener { runtime.personalPhrases.cancel() }
                        .setPositiveButton("Import reviewed phrases") { _,_ -> route("confirm phrase import") }.show()
                    tell("Phrase import ready for review. Nothing saved or sent.")
                }
            } catch(e: Exception) { tell("Import: ${e.message}. Nothing saved or sent.",true) }
            return
        }
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
        if(requestCode in listOf(401,406)) {
            HandsFreeService.manualSpeechBusy=false; HandsFreeService.running?.resumeWhenQuiet()
            val words=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if(requestCode==406) {
                if(resultCode==RESULT_OK && words.isNotEmpty()) {
                    lastHeard=words.first()
                    heardStatus.text=getString(R.string.heard,lastHeard);heardStatus.visibility=android.view.View.VISIBLE
                    tell("Voice test heard: $lastHeard. No command sent.")
                } else tell("Voice test returned no words. Check microphone access on this phone and your speech provider, then try again. No command sent.")
                return
            }
            if(resultCode!=RESULT_OK || words.isEmpty()) {
                tell("No voice command returned. Try Test voice recognition in Voice & speech to check microphone and speech input. Nothing sent.")
                return
            }
        }
        if(requestCode == 401 && resultCode == RESULT_OK) {
            val alternatives = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if(alternatives.isNotEmpty()) {
                lastHeard = alternatives[0]
                heardStatus.text = getString(R.string.heard,lastHeard); heardStatus.visibility = if(page in listOf("Voice","VoiceTools")) android.view.View.VISIBLE else android.view.View.GONE
            }
            // Do not guess between conflicting recognized targets.
            val fit = fitting
            val parsed = alternatives.map { voiceIdentity(it,fit) }.filter { it !is AppIntent.Unsupported }.distinct()
            if(parsed.size > 1) tell("Speech alternatives disagree. Please say the exact program or level again.",true)
            else if(alternatives.isNotEmpty()) pendingText = alternatives.firstOrNull { (voiceIdentity(it,fit)) == parsed.firstOrNull() } ?: alternatives[0]
        }
    }
    // Must observe durable journal success before acknowledging or playing; apply() cannot do this.
    private fun commitRequired(editor: android.content.SharedPreferences.Editor): Boolean = editor.commit()
    private fun receiveWatch(request: JSONObject, callback: (JSONObject) -> Unit, node: String) {
        if(!resumed || !companion.accepts(node)) { callback(remoteResult("BLOCKED","Open HeySig and enable its watch connection")); return }
        val id = request.getString("id")
        watchHistory.get(node,id)?.let { callback(it); return }
        val record: (JSONObject) -> Unit = { result ->
            if(!watchHistory.put(node,id,result)) callback(remoteResult("UNKNOWN","App result could not be stored; do not automatically retry")) else callback(result)
        }
        val text = request.getString("text")
        if(text.lowercase() == "status") { record(remoteResult("FINAL", stateText(requested))); return }
        if(text.trim().lowercase() in listOf("stop","cancel")) {
            noise.stop(); tts?.stop()
            cancelled.set(true); pendingProgram = null; pendingRestore = null; runtime.personalPhrases.cancel(); runtime.sleepRoutine.cancel(); runtime.fittingWorkflow.cancel()
            record(remoteResult("FINAL","Stop requested; already emitted tones cannot be undone")); return
        }
        // Authenticated manual input ends automatic sessions even when playback is busy.
        // An already emitted command cannot be undone; never queue or replay the new request.
        if(NoiseVoice.parse(text) == null) noise.stop()
        if(busy.get() || remoteReply != null) { record(remoteResult("BUSY","HeySig is busy. Wait for its result or say Stop")); return }
        if(page == "Setup") { record(remoteResult("BLOCKED","Return to Home after reviewing and saving Setup")); return }
        if(NoiseVoice.parse(text) in listOf(NoiseCommand.Sample,NoiseCommand.Observe) &&
            (!getSharedPreferences("heysig-noise-v1",MODE_PRIVATE).getBoolean("microphoneDisclosureV2",false) ||
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            record(remoteResult("BLOCKED","On the phone, open Noise and review microphone use and permission before requesting watch noise capture.")); return
        }
        val started = remoteResult("UNKNOWN","An earlier request started; do not replay automatically")
        if(!watchHistory.put(node,id,started)) {
            callback(remoteResult("BLOCKED","Cannot journal watch command")); return
        }
        val origin = "paired:$node:" + request.getString("conversation")
        val alternatives = request.optJSONArray("alternatives")
        if(pendingProgram == null && pendingRestore == null && alternatives != null) {
            val intents = (0 until minOf(5,alternatives.length())).map { voiceIdentity(alternatives.getString(it),fitting) }.filter { it !is AppIntent.Unsupported }.distinct()
            if(intents.size > 1) { record(remoteResult("CLARIFY","Speech alternatives disagree. Say the exact program or number again")); return }
        }
        remoteReply = record; commandOrigin = origin; activeRemoteNode = node
        try { lastHeard = "Watch: $text"; route(text,origin) }
        catch(e:Exception) { remoteReply = null; activeRemoteNode = null; record(remoteResult("FAILED","Watch command could not be processed")) }
        finally { commandOrigin = null }
    }
    private fun voiceIdentity(text:String,fit:Fitting?): Any = HandsFreeControl.parse(text) ?: VoicePreference.parse(text) ?: NoiseVoice.parse(text) ?: DataVoice.parse(text) ?: if(FittingWorkflow.accepts(text)) "setup:${FittingWorkflow.normalize(text)}" else runtime.personalPhrases.identity(text,fit)
    private fun route(text: String, origin: String? = null) {
        DataVoice.parse(text)?.let { action ->
            noise.stop()
            if(busy.get() || pendingProgram != null || pendingRestore != null || runtime.fittingWorkflow.pending || runtime.personalPhrases.awaitingConfirmation || runtime.sleepRoutine.hasDraft) {
                tell("Finish or cancel the current operation before opening import or export.",true);return
            }
            when(action) {
                DataVoice.IMPORT_FITTING -> chooseFittingImport()
                DataVoice.EXPORT_PHRASES -> choosePhraseExport()
                DataVoice.IMPORT_PHRASES -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE) },405)
            }
            if(origin != null) tell("Continue the document review on the phone. Nothing sent.",true)
            return
        }

        HandsFreeControl.parse(text)?.let { control ->
            if(control==HandsFreeControl.ON && HandsFreeService.running==null) {
                if(origin==null) startHandsFree() else tell("Start a hands-free session on the phone first")
            } else {
                noise.stop();HandsFreeService.running?.control(control)
                tell(HandsFreeService.running?.status ?: "Hands-free session ended")
            }
            return
        }
        if(NoiseVoice.parse(text) == null) noise.stop()
        VoicePreference.parse(text)?.let { preference ->
            if(!prefs.edit().putBoolean(preference.key,preference.enabled).commit()) { tell("Could not save voice setting"); return }
            speechSummary?.text = speechStatusText()
            tts?.stop(); HandsFreeService.running?.stopSpokenReply(); tell(preference.description,true)
            if(page == "Voice") show()
            return
        }
        if(FittingWorkflow.accepts(text) && !runtime.fittingWorkflow.pending &&
            (runtime.personalPhrases.awaitingConfirmation || runtime.sleepRoutine.hasDraft)) {
            tell("Finish or cancel the current phrase or sleep review before starting aid setup.",true);return
        }
        if(!busy.get() && pendingProgram == null && pendingRestore == null &&
            (runtime.fittingWorkflow.pending || FittingWorkflow.accepts(text))) {
            runtime.fittingWorkflow.handle(text,fitting,origin ?: "phone",SystemClock.elapsedRealtime())?.let {
                if(it.startsWith("Aid setup saved")) { noise.fittingChanged();runCatching { SleepReminders(this).refresh() } }
                tell(it,true);return
            }
        }
        val fit = fitting ?: run { tell("Save aid setup first",true); return }
        if(text.trim().lowercase() in listOf("stop","cancel")) { if(text.trim().lowercase()=="stop") runCatching { AutomaticSleepScheduler(this).disable() };noise.stop(); tts?.stop(); cancelled.set(true); pendingProgram = null; pendingRestore = null; runtime.personalPhrases.cancel(); runtime.sleepRoutine.cancel(); runtime.fittingWorkflow.cancel(); tell("Cancelled"); return }
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
            val confirmed = text.trim().lowercase().trimEnd('.', '!', '?') in listOf("yes","confirm","confirm restore","restore known settings","confirm refresh","refresh current settings")
            val plan = pendingRestore!!; pendingRestore = null
            val refreshing = pendingRefresh; pendingRefresh = false
            if(confirmed) execute(plan,true,refreshing=refreshing) else tell(if(refreshing) "Refresh cancelled. Nothing sent." else "Restore cancelled. Say restore settings to review again.",true)
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
        SleepVoice.parse(text,fit)?.let { command ->
            if(command is SleepCommand.Now) { runSleep(command.sleep);return }
            if(command==SleepCommand.Enable) { tell(AutomaticSleepScheduler(this).enable(),true);return }
            if(command==SleepCommand.Disable) { val result=AutomaticSleepScheduler(this).disable();SleepReminders(this).setEnabled(false);tell(result,true);return }
            val reply=runtime.sleepRoutine.handle(command,fit,origin ?: "phone")
            runCatching { SleepReminders(this).refresh() }
            if(command in listOf(SleepCommand.Resume,SleepCommand.Override) || reply.startsWith("Routine saved.")) runCatching { AutomaticSleepScheduler(this).refresh() }
            if(origin == null && command == SleepCommand.Configure) navigate("Sleep")
            tell(reply,true); return
        }
        runtime.personalPhrases.manage(text,fit,origin ?: "phone")?.let { tell(it,true); return }
        when(val intent = runtime.personalPhrases.resolve(text,fit)) {
            is AppIntent.RelativeLevel -> {
                val request=try { LevelSteps.request(intent.control,intent.delta,requested,fit) }
                    catch(e: Exception) { tell(e.message ?: "Choose an exact value first",true);return }
                dispatch(request)
            }
            is AppIntent.RelativeVolume -> {
                val request=try { VolumeSteps.request(intent.delta,requested,fit) }
                    catch(e: Exception) { tell(e.message ?: "Choose an exact volume first",true);return }
                dispatch(request)
            }
            is AppIntent.Control -> dispatch(intent.request)
            is AppIntent.ClarifyProgram -> clarifyProgram(intent.slot)
            is AppIntent.ReadLevel -> tell("${intent.control.name.lowercase().replaceFirstChar { it.uppercase() }}: ${requested.levels[intent.control]?.toString() ?: "unknown"}.",true)
            AppIntent.ReadProgram -> tell("Program: ${programLabel(requested.program)}.",true)
            AppIntent.ListPrograms -> tell(fit.programs.joinToString(". ") { "${it.slot}: ${it.name}" },true)
            AppIntent.SaveSettings -> saveRequested()
            AppIntent.ReadSavedSettings -> tell(saved?.let { "Saved snapshot. ${stateText(it)} Unknown fields will not be sent." } ?: runtime.savedIssue ?: "No saved settings",true)
            AppIntent.RestoreSettings -> confirmRestore()
            AppIntent.Refresh -> confirmRefresh()
            AppIntent.Cancel -> { runtime.sleepRoutine.cancel();cancelled.set(true); tell("Cancelled") }
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
    private fun confirmRefresh() {
        if(busy.get()) { tell("Busy; wait or Stop",true); return }
        val source=requested.copy(levels=requested.levels.toMap())
        val plan=try { runtime.restorePlan(source) }
            catch(e: Exception) { tell(e.message ?: "Select a program before Refresh",true); return }
        pendingRestore=plan; pendingRefresh=true; pendingProgram=null; confirmationOrigin=commandOrigin
        confirmationDeadline=SystemClock.elapsedRealtime()+60000
        tell("Refresh ${stateText(source)}. Unknown levels will be skipped. Say confirm refresh or cancel.",true)
        if(commandOrigin != null) return
        android.app.AlertDialog.Builder(this).setTitle("Refresh current settings?")
            .setMessage("${stateText(source)}\nResends the current program and known levels. Unknown levels are skipped.")
            .setNegativeButton("Cancel") { _,_ -> pendingRestore=null; pendingRefresh=false }
            .setNeutralButton("Speak answer") { _,_ -> beginVoice() }
            .setPositiveButton("Refresh") { _,_ ->
                pendingRestore=null; pendingRefresh=false
                if(SystemClock.elapsedRealtime()<=confirmationDeadline) execute(plan,true,refreshing=true)
                else tell("Confirmation expired; nothing sent",true)
            }.setOnCancelListener { pendingRestore=null; pendingRefresh=false }.show()
    }
    private fun confirmRestore() {
        pendingRefresh=false
        val source = saved ?: run { tell(runtime.savedIssue ?: "No saved settings",true); return }
        val slot = source.program ?: run { tell("Saved program unknown",true); return }
        val plan = try { runtime.restorePlan(source) } catch(e: Exception) { tell(e.message ?: "Saved settings cannot be restored",true);return }
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
        if(request !is ManualRequest.Battery) {
            try { runtime.sleepRoutine.transition(runtime.sleepRoutine.state.manual()) }
            catch(e: Exception) { tell(e.message ?: "Could not pause routine",true);return }
        }
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
    private fun confirmSleep(sleep: Boolean) {
        val fit=fitting ?: return
        val slot=runCatching { runtime.sleepRoutine.target(sleep,fit) }.getOrElse { tell(it.message ?: "Review routine first",true);return }
        val revision=runtime.sleepRoutine.editorRevision()
        val deadline=SystemClock.elapsedRealtime()+60_000
        android.app.AlertDialog.Builder(this).setTitle(if(sleep) "Sleep now?" else "Wake now?")
            .setMessage("Switch to ${fit.programs.first { it.slot==slot }.name}?")
            .setNegativeButton("Cancel",null).setPositiveButton("Switch") { _,_ ->
                if(fitting!=fit || runtime.sleepRoutine.editorRevision()!=revision || SystemClock.elapsedRealtime()>deadline)
                    tell("Routine changed or confirmation expired. Review the program again; nothing sent.",true)
                else runSleep(sleep)
            }.show()
    }
    private fun runSleep(sleep: Boolean) {
        val fit=fitting ?: run { tell("Save aid setup first");return }
        val slot=runCatching { runtime.sleepRoutine.target(sleep,fit) }.getOrElse { tell(it.message ?: "Review routine first",true);return }
        execute(listOf(ManualRequest.SelectProgram(slot)),false,sleepTarget=sleep)
    }
    private fun execute(plan: List<ManualRequest>,restoring: Boolean, automatic: Boolean = false, sleepTarget: Boolean? = null, refreshing: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        if(!automatic) noise.stop()
        fun reject(text:String) { tell(text,true); onComplete?.invoke(false) }
        if(automatic && (!NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED || noiseSafety().let { !it.foreground || !it.unlocked || !it.audioFree || it.manualOverride || it.sleeping })) {
            reject("Automatic blocked by release or foreground/audio priority guard"); return
        }
        if(!resumed) { reject("Open HeySig before sending a command"); return }
        com.heysig.app.voice.HandsFreeService.running?.suspendMicrophone(invalidateConfirmation=true)
        runtime.execute(plan,restoring,automatic,{ resumed },{ false },{ text ->
            if(!isDestroyed) { message=text; show(); tell(text) }
        },sleepTarget=sleepTarget) { playbackResult,success ->
            val result=if(refreshing) "${if(success) "Refresh sent." else "Refresh incomplete."} $playbackResult" else playbackResult
            onComplete?.invoke(success)
            message=result
            val watchReply=remoteReply; remoteReply=null; activeRemoteNode=null
            watchReply?.invoke(remoteResult("FINAL",result))
            if(!isDestroyed) {
                show()
                if(pendingVoice && resumed) launchVoice()
                else if(watchReply == null && resumed) tell(result,true)
            }
            com.heysig.app.voice.HandsFreeService.running?.resumeWhenQuiet()
        }
    }
}
