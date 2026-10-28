package com.heysig.watch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.*
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.heysig.companion.CompanionSafety
import com.heysig.companion.PrivacyNotice
import com.heysig.voice.WatchSpeechChoices
import com.heysig.voice.HandsFreeCompanion
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Foreground MessageClient remote. Never records audio, generates tones, queues or retries. */
class WatchActivity : Activity(), MessageClient.OnMessageReceivedListener {
    private val prefs by lazy { getSharedPreferences("heysig-watch-v1",MODE_PRIVATE) }
    private val messages by lazy { Wearable.getMessageClient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private data class Awaiting(val node: String, val timeout: Runnable, val reply: (JSONObject) -> Unit)
    private val pending = mutableMapOf<String,Awaiting>()
    private var phone: String? = null
    private var rpcPath=CompanionSafety.PATH
    private var active = false
    private var listening = false
    private var listener: MessageClient.OnMessageReceivedListener? = null
    private var reconnectProbe: WatchVoiceClient?=null
    private var reconnecting = false
    private var readStatusAfterConnect = false
    private var ready = false
    private var working = false
    private var generation = 0
    private var listenerGeneration = 0
    private var conversation = UUID.randomUUID().toString()
    private var phoneSpeech = WatchSpeechChoices()
    private var pendingSessionCapture: Boolean? = null
    private var exportDocument: String? = null
    private lateinit var status: TextView
    private lateinit var primary: Button
    private lateinit var layout: LinearLayout
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var speechIssue = "Speech engine is starting. Try again shortly."

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        phone = prefs.getString("phone",null)
        rpcPath=prefs.getString("connectionPath",CompanionSafety.PATH)!!
        speech = TextToSpeech(this) { result ->
            runOnUiThread {
                val language = if(result == TextToSpeech.SUCCESS) speech?.setLanguage(Locale.US) else null
                speechReady = language != null && language >= TextToSpeech.LANG_AVAILABLE
                speechIssue = if(result != TextToSpeech.SUCCESS) "Speech engine unavailable. Check watch text-to-speech settings."
                    else "English speech data unavailable. Check watch text-to-speech language settings."
                if(speechReady) speech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {}
                    @Deprecated("Platform speech callback")
                    override fun onError(id: String?) { runOnUiThread { if(active) showSpeechIssue("Speech output failed. Check watch speech data and media volume.") } }
                })
            }
        }
        render()
    }
    private fun label(text: String, size: Float = 16f) {
        layout.addView(TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(0,8,0,8) })
    }
    private fun button(text: String, action: () -> Unit) {
        layout.addView(Button(this).apply { this.text = text; minHeight = (48*resources.displayMetrics.density).toInt(); setOnClickListener { action() } })
    }
    private val shake by lazy { ShakeTrigger(this) {
        if(active) { primaryShake() }
    } }
    private fun primaryShake() { beginVoiceSession(true) }
    private var primaryForShake: Button?=null
    private fun shakeOptions() {
        if(WatchVoiceSessionService.instance!=null) {
            AlertDialog.Builder(this).setTitle("Stop voice session")
                .setMessage("Shake → double vibration → speak → single vibration, even while the screen is asleep. Prompt and reply switches follow your phone. The microphone is idle between commands.")
                .setPositiveButton("Keep on",null).setNegativeButton("Stop session") { _,_ -> WatchVoiceSessionService.stop(this) }.show()
        } else beginVoiceSession(false)
    }
    private fun beginVoiceSession(capture: Boolean) {
        if(!active) return
        if(working) { reply("Wait for the result or press Stop.");return }
        WatchVoiceSessionService.instance?.let { if(capture) it.capture();return }
        if(phone==null) { reply("Select your phone once, then Speak or start Shake voice.");connect();return }
        if(!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) { reply("Watch recognition service unavailable. Use Type command.");return }
        if(getSystemService(android.hardware.SensorManager::class.java).getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER,true)==null) {
            reply("This watch has no wake-up accelerometer for screen-asleep shake.");return
        }
        if(!prefs.getBoolean("shakeVoiceDisclosureV1",false)) {
            AlertDialog.Builder(this).setTitle("Shake voice, including screen asleep or locked")
                .setMessage("Start an eight-hour voice session that continues while the watch is asleep or PIN-locked; the PIN lock stays intact. Three deliberate shakes give one prompt if enabled on your phone, then two ready vibrations before capturing up to ten seconds of speech. A single vibration marks the reply or error; it does not confirm aid receipt. The microphone stays idle between commands. Recognition and spoken replies use your watch providers, which may send audio or reply text over their networks. Phone/watch commands and replies use Google Play services. Accidental movement or other voices can trigger commands; this is not voice authentication. A persistent notification provides Stop. Ends on Stop, closing HeySig from Recents, process death or eight hours; never restarts after reboot. Prompt and reply switches follow your phone.")
                .setNegativeButton("Not now",null).setPositiveButton("Start session") { _,_ ->
                    if(prefs.edit().putBoolean("shakeVoiceDisclosureV1",true).putBoolean("speechDisclosureV1",true).commit()) beginVoiceSession(capture)
                    else reply("Could not save voice choice")
                }.show()
            return
        }
        val missing=mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) missing.add(android.Manifest.permission.POST_NOTIFICATIONS)
        val needed=missing.filter { checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED }
        if(needed.isNotEmpty()) { pendingSessionCapture=capture;requestPermissions(needed.toTypedArray(),732);return }
        if(!getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) { reply("Enable HeySig notifications to start shake voice.");return }
        val access=com.heysig.voice.WatchVoiceAccess(false,true,true,getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        if(!access.mayStartSession) { reply("Unlock once to start the voice session. It can then continue while locked.");return }
        try { reconnectProbe?.close();reconnectProbe=null;reconnecting=false;readStatusAfterConnect=false;shake.stop();WatchVoiceSessionService.start(this,capture) }
        catch(e: RuntimeException) { reply("Could not start shake voice. Open HeySig and try again.") }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code,permissions,grants)
        if(code==732) {
            val capture=pendingSessionCapture;pendingSessionCapture=null
            if(capture!=null && grants.isNotEmpty() && grants.all { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                pendingSessionCapture=capture
                if(active) { pendingSessionCapture=null;beginVoiceSession(capture) }
            }
            else reply("Shake voice is off. Microphone and notifications are needed for this session.")
        }
    }
    private var lastStatus = "Disconnected"
    private fun canSpeak() = phone != null && (ready || WatchVoiceSessionService.instance != null)
    private fun primaryAction() {
        when {
            phone == null -> connect()
            !canSpeak() -> reconnectSelectedPhone()
            else -> beginVoiceSession(true)
        }
    }
    private fun render() {
        val home = WatchStyle.home(this,WatchStyle.Labels(R.string.speak,R.string.stop,R.string.more,R.string.speech_off,R.string.connect,R.string.speak_command),lastStatus,
            !phoneSpeech.prompts && !phoneSpeech.responses,
            { primaryAction() },
            { if(WatchVoiceSessionService.instance!=null) WatchVoiceSessionService.stop(this) else { SleepingShakeService.stop(this); generation++; abandon(); working=false; reconnecting=false; readStatusAfterConnect=false; speech?.stop(); send("stop",listOf("stop")) } },
            { more() },connected=canSpeak(),tapBackgroundToSpeak=true)
        primaryForShake=home.primary; primary=home.primary
        status = home.status
        setContentView(home.view)
    }
    private var wifiCheck: WatchWifiCheck?=null
    private fun checkWifi() { wifiCheck?.close();wifiCheck=WatchWifiCheck(this) { send("status",listOf("status")) }.show() }
    private fun more() {
        val items = arrayOf("Read status","Check last result","Connect phone","Type command","Speech options","Privacy and data","Public privacy policy","Disconnect","Full status","Phone hands-free","Shake to speak","Check Wi-Fi","Stop voice session")
        AlertDialog.Builder(this).setTitle("More").setItems(items) { _,index ->
            when(index) {
                12 -> WatchVoiceSessionService.stop(this)
                11 -> checkWifi()
                10 -> shakeOptions()
                2 -> connect()
                0 -> send("status",listOf("status"))
                1 -> {
                    WatchVoiceSessionService.instance?.let { it.checkLastResult();return@setItems }
                    val id = prefs.getString("lastRequest",null)
                    if(id == null || prefs.getString("lastNode",null) != phone) reply("No retained request for this phone")
                    else if(!ready) reply("Connect the selected phone first")
                    else rpc(JSONObject().put("op","result").put("id",id)) { reply(it.optString("text","No confirmed result")) }
                }
                3 -> {
                    val input = EditText(this).apply { hint="Command"; setSingleLine(); setTextColor(Color.WHITE) }
                    AlertDialog.Builder(this).setTitle("Type command").setView(input)
                        .setNegativeButton("Cancel",null).setPositiveButton("Send") { _,_ -> send(input.text.toString(),listOf(input.text.toString())) }.show()
                }
                4 -> speechOptions()
                5 -> privacy()
                6 -> if(BuildConfig.PRIVACY_POLICY_URL.isEmpty()) reply("Public policy/contact is not configured yet; required before store submission.")
                    else try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
                    catch(e:Exception) { reply("Open Privacy and data on your phone to view the public policy") }
                7 -> { WatchVoiceSessionService.stop(this);disconnect(); reply("Disconnected. No command will replay.") }
                9 -> handsFreeControls()
                8 -> AlertDialog.Builder(this).setTitle("Status").setMessage(lastStatus).setPositiveButton("Close",null).show()
            }
        }.setNegativeButton("Close",null).show()
    }
    private fun speechOptions() {
        AlertDialog.Builder(this).setTitle("Speech follows your phone")
            .setMessage("Change Voice prompts and Spoken responses in HeySig on the phone. The watch reads both switches before each command and reply; there are no separate watch speech switches.")
            .setPositiveButton("Close",null).show()
    }
    override fun onResume() {
        super.onResume(); active = true; SleepingShakeService.activityVisible = true
        WatchVoiceSessionService.observer={ text,choices -> runOnUiThread {
            if(active) { val changed=phoneSpeech!=choices;phoneSpeech=choices;lastStatus=text
                if(changed) render() else { status.text=text;primary.setText(if(canSpeak()) R.string.speak else R.string.connect) }
            }
        } }
        WatchVoiceSessionService.instance?.let { phoneSpeech=it.choices;lastStatus=it.status;render() }
        if(WatchVoiceSessionService.instance==null && getSharedPreferences("heysig-gesture",MODE_PRIVATE).getBoolean("shake",false)) shake.start()
        status.text = lastStatus;primary.setText(if(canSpeak()) R.string.speak else R.string.connect)
        val current = ++listenerGeneration
        val receiver = MessageClient.OnMessageReceivedListener { event ->
            if(active && current == listenerGeneration) onMessageReceived(event)
        }
        listener = receiver
        messages.addListener(receiver).addOnSuccessListener {
            if(!active || current != listenerGeneration) messages.removeListener(receiver)
            else {
                listening = true
                // Pause invalidates a connection, not the saved phone choice.
                // Re-establish readiness only with a fresh, correlated hello.
                if(phone != null && WatchVoiceSessionService.instance == null) reconnectSelectedPhone()
            }
        }.addOnFailureListener { reply("Wear OS Data Layer unavailable. Check Google Play services.") }
    }
    override fun onPostResume() {
        super.onPostResume()
        pendingSessionCapture?.let { capture -> pendingSessionCapture=null;beginVoiceSession(capture) }
        consumeShakeTap()
    }
    override fun onPause() {
        wifiCheck?.close();wifiCheck=null;
        WatchVoiceSessionService.observer=null
        shake.stop()
        SleepingShakeService.activityVisible = false
        active = false; listenerGeneration++; disconnect(false); listener?.let { messages.removeListener(it) }; listener = null; listening = false; speech?.stop()
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    private fun consumeShakeTap() {
        if(SleepingShakeService.consume(intent)) {
            if(getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) reply("Unlock the watch, then tap Speak")
            else if(working) reply("Wait for the current result or press Stop")
            else beginVoiceSession(true)
        }
    }
    override fun onDestroy() { exportDocument = null; speech?.shutdown(); super.onDestroy() }
    private fun updateRequestScreen() {
        // A normal watch screen timeout must not abandon an in-flight result.
        // This applies only to the visible Activity and the bounded RPC wait;
        // explicit navigation/locking still disconnects without replay.
        if(active && pending.isNotEmpty()) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun abandon() { reconnectProbe?.close();reconnectProbe=null; pending.values.forEach { handler.removeCallbacks(it.timeout) }; pending.clear(); updateRequestScreen() }
    private fun disconnect(resetConversation: Boolean = true) {
        generation++; ready = false; reconnecting = false; readStatusAfterConnect = false; working = false; abandon()
        if(resetConversation) conversation = UUID.randomUUID().toString()
    }
    private fun reply(text: String, prompt: Boolean = false) {
        lastStatus = text; status.text = text
        primary.setText(if(canSpeak()) R.string.speak else R.string.connect)
        if(active && (if(prompt) phoneSpeech.prompts else phoneSpeech.responses)) {
            if(!speechReady) showSpeechIssue(speechIssue)
            else if(speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-result") != TextToSpeech.SUCCESS)
                showSpeechIssue("Speech output could not start. Check watch text-to-speech settings.")
        }
    }
    private fun showSpeechIssue(issue: String) {
        lastStatus = "$lastStatus\n$issue"; status.text = lastStatus
    }
    private fun handsFreeControls() {
        val commands=arrayOf("hands free on","pause listening for 30 minutes","hands free off","hands free status","end hands free session")
        AlertDialog.Builder(this).setTitle("Phone hands-free")
            .setItems(arrayOf("On","Pause 30 minutes","Off","Status","End session")) { _,index -> send(commands[index],listOf(commands[index])) }
            .setNegativeButton("Close",null).show()
    }
    /** Read-only reconnect; never queues or replays a hearing-aid command. */
    private fun reconnectSelectedPhone() {
        if(!active || !listening || reconnecting) return
        if(phone == null) { reply("Choose your phone with Connect first");return }
        reconnecting=true;ready=false
        lastStatus="Connecting to your selected phone…";status.text=lastStatus
        primary.setText(R.string.connect)
        val current=generation
        val probe=WatchVoiceClient(this);reconnectProbe=probe
        probe.connect { answer ->
            if(!active || current!=generation || reconnectProbe!==probe) { probe.close();return@connect }
            reconnectProbe=null;reconnecting=false
            ready=answer.optString("phase")=="READY"
            if(ready) {
                rpcPath=probe.connectedRoute;phoneSpeech=probe.choices
                prefs.edit().putString("connectionPath",rpcPath).apply()
            }
            probe.close()
            val requestedStatus=readStatusAfterConnect;readStatusAfterConnect=false
            lastStatus=answer.optString("text","Phone connection is not ready. Open HeySig on the phone and enable this watch.")
            render() // Refresh both the actual Connect/Speak state and phone speech switches.
            if(ready && requestedStatus) send("status",listOf("status"))
        }
    }
    private fun connect() {
        if(WatchVoiceSessionService.instance?.busy==true) { reply("Stop the current voice turn before changing phones.");return }
        WatchVoiceSessionService.stop(this)
        if(!active || !listening) { reply("Connection is starting; try Connect phone again"); return }
        disconnect()
        val current=generation
        fun choose(nodes: List<com.google.android.gms.wearable.Node>,path: String) {
            if(!active || current!=generation) return
            if(nodes.isEmpty()) { reply("No enabled phone found. Start a hands-free session on the paired phone, or open Watch connection and enable this watch.");return }
            AlertDialog.Builder(this).setTitle("Choose phone")
                .setItems(nodes.map { it.displayName }.toTypedArray()) { _,index ->
                    if(!active || current!=generation) return@setItems
                    val node=nodes[index];disconnect();phone=node.id;rpcPath=path
                    if(!prefs.edit().putString("phone",node.id).putString("phoneLabel",node.displayName).putString("connectionPath",path).commit()) { reply("Could not save phone selection");return@setItems }
                    reconnectSelectedPhone()
                }.show()
        }
        fun ordinary() {
            Wearable.getCapabilityClient(this).getCapability(CompanionSafety.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { choose(it.nodes.sortedBy { node -> node.displayName },CompanionSafety.PATH) }
                .addOnFailureListener { if(active && current==generation) reply("Could not discover phone. Check pairing and Google Play services.") }
        }
        Wearable.getCapabilityClient(this).getCapability(HandsFreeCompanion.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { if(it.nodes.isNotEmpty()) choose(it.nodes.sortedBy { node -> node.displayName },HandsFreeCompanion.PATH) else ordinary() }
            .addOnFailureListener { ordinary() }
    }
    private fun rpc(body: JSONObject, callback: (JSONObject) -> Unit) {
        val node = phone
        if(!active || !listening || node == null) { callback(JSONObject().put("phase","UNKNOWN").put("text","Connect phone first")); return }
        if(pending.size >= 4) { callback(JSONObject().put("phase","BUSY").put("text","Wait for the pending result")); return }
        val id = UUID.randomUUID().toString()
        val current = generation
        val timeout = Runnable {
            val request = pending.remove(id)
            updateRequestScreen()
            if(request != null && active && current == generation) {
                ready = false
                callback(JSONObject().put("phase","UNKNOWN").put("text","No confirmed result. Reconnect and use Check last result. No automatic retry."))
            }
        }
        pending[id] = Awaiting(node,timeout,callback)
        updateRequestScreen()
        handler.postDelayed(timeout,30000)
        val data = body.put("rpc",id).toString().toByteArray(Charsets.UTF_8)
        if(data.size > CompanionSafety.MAX_BYTES) { pending.remove(id); updateRequestScreen(); handler.removeCallbacks(timeout); callback(JSONObject().put("phase","BLOCKED").put("text","Command too large")); return }
        messages.sendMessage(node,rpcPath,data).addOnFailureListener {
            val request = pending.remove(id)
            updateRequestScreen()
            handler.removeCallbacks(timeout)
            if(request != null && active && current == generation) { ready = false; request.reply(JSONObject().put("phase","UNKNOWN").put("text","Phone disconnected. Reconnect and check the last result. No retry.")) }
        }
    }
    override fun onMessageReceived(event: MessageEvent) {
        if(!active || event.path != rpcPath || event.data.size !in 1..CompanionSafety.MAX_BYTES) return
        try {
            val answer = JSONObject(String(event.data,Charsets.UTF_8))
            val id = answer.getString("rpc")
            val waiting = pending[id] ?: return
            if(waiting.node != event.sourceNodeId) return
            pending.remove(id); handler.removeCallbacks(waiting.timeout)
            updateRequestScreen()
            phoneSpeech=WatchSpeechChoices.received(answer.opt("voicePrompts") as? Boolean,answer.opt("spokenResponses") as? Boolean)
            waiting.reply(answer)
        } catch(e:Exception) {
            ready = false; reconnecting = false; readStatusAfterConnect = false; working = false; abandon()
            reply("Invalid phone reply. Check the phone; no command is retried.")
        }
    }
    private fun send(text: String, alternatives: List<String>) {
        if(WatchWifiCheck.requested(text)) { checkWifi();return }
        WatchVoiceSessionService.instance?.let { it.typed(text,alternatives);return }
        if(!active || !listening) { reply("Watch connection is starting. Try again shortly.");return }
        if(!ready) {
            if(phone == null) { reply("Choose your phone with Connect first");return }
            // Only a newly requested, read-only status may wait for hello.
            // Any aid-changing command must be spoken/entered again after connection.
            if(text.trim().equals("status",ignoreCase=true)) readStatusAfterConnect=true
            reconnectSelectedPhone()
            if(!text.trim().equals("status",ignoreCase=true)) reply("Connecting to your phone. Give the command again when connected.")
            return
        }
        if(working) { reply("Wait for the current result or press Stop"); return }
        val id = UUID.randomUUID().toString()
        try { CompanionSafety.command(id,conversation,text,alternatives) } catch(e:Exception) { reply("Use a command of 1–512 characters"); return }
        val current = generation
        val deadline = SystemClock.elapsedRealtime()+15000
        working = true
        lastStatus=getString(R.string.preparing_request); status.text=lastStatus
        rpc(JSONObject().put("op","prepare").put("id",id)) { lease ->
            if(!active || current != generation) return@rpc
            if(lease.optString("phase") != "LEASE" || SystemClock.elapsedRealtime() >= deadline) {
                working = false; reply(lease.optString("text","Request expired; no command sent")); return@rpc
            }
            // Commit the lookup identifier before submission, never the raw recognized words.
            val stopping = text.trim().lowercase() in listOf("stop","cancel")
            if(!stopping && !prefs.edit().putString("lastRequest",id).putString("lastNode",phone).commit()) {
                working = false; reply("Could not remember request; nothing sent"); return@rpc
            }
            lastStatus=getString(R.string.sending_request); status.text=lastStatus
            rpc(JSONObject().put("op","command").put("id",id).put("conversation",conversation)
                .put("token",lease.getString("token")).put("text",text).put("alternatives",JSONArray(alternatives))) { answer ->
                working = false
                reply(answer.optString("text","No confirmed result; check phone"),answer.optBoolean("prompt",answer.optString("phase") == "CLARIFY"))
            }
        }
    }
    @Deprecated("Platform recognition/document result adapter")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code,result,data)
        if(code == 42) {
            val document = exportDocument; exportDocument = null
            if(result == RESULT_OK && document != null && data?.data != null) {
                try { checkNotNull(contentResolver.openOutputStream(data.data!!,"wt")).use { it.write(document.toByteArray()) }; reply("Export saved. Delete exported copies separately.") }
                catch(e:Exception) { reply("Export could not be saved") }
            }
        }
    }
    private fun privacy() {
        val body = PrivacyNotice.sections.joinToString("\n\n") { "${it.first}\n${it.second}" }
        AlertDialog.Builder(this).setTitle("Privacy and data").setMessage(body)
            .setPositiveButton("Close",null)
            .setNeutralButton("Export") { _,_ ->
                AlertDialog.Builder(this).setTitle("Export watch data?")
                    .setMessage("Saves phone selection, request identifiers, shake and speech choices to your chosen provider, which may upload them. Delete exported copies separately.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Choose location") { _,_ ->
                        exportDocument = JSONObject(prefs.all).put("gestureChoices",JSONObject(getSharedPreferences("heysig-gesture",MODE_PRIVATE).all)).toString(2)
                        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"heysig-watch-data.json") },42) }
                        catch(e:Exception) { exportDocument = null; reply("No document provider available; use Android app settings to manage local data") }
                    }.show()
            }
            .setNegativeButton("Delete local data") { _,_ ->
                if(working || WatchVoiceSessionService.instance?.busy==true) { reply("Wait for the result or Stop before deleting watch data"); return@setNegativeButton }
                AlertDialog.Builder(this).setTitle("Delete watch data?")
                    .setMessage("Android will close HeySig and erase its private data and permission choices on this watch, including phone selection, request identifiers, shake and speech choices. Does not change the aids or remove phone/provider/exported data.")
                    .setNegativeButton("Keep data",null).setPositiveButton("Delete") { _,_ ->
                        WatchVoiceSessionService.stop(this);disconnect(); exportDocument = null; speech?.stop()
                        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                        if(!manager.clearApplicationUserData()) reply("Android could not clear watch data. Use the app's storage settings.")
                    }.show()
            }.show()
    }
}
