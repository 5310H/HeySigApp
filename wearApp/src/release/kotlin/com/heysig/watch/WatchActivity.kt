package com.heysig.watch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.*
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.heysig.companion.CompanionSafety
import com.heysig.companion.PrivacyNotice
import com.heysig.control.VoicePreference
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
    private var ready = false
    private var working = false
    private var generation = 0
    private var listenerGeneration = 0
    private var conversation = UUID.randomUUID().toString()
    private var deferred: (() -> Unit)? = null
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
        if(active) { ShakeTrigger.acknowledge(this); primaryShake() }
    } }
    private fun primaryShake() { findViewById<android.view.View>(android.R.id.content)?.let { primaryForShake?.performClick() } }
    private var primaryForShake: Button?=null
    private fun shakeOptions() {
        val choices=getSharedPreferences("heysig-gesture",MODE_PRIVATE)
        android.app.AlertDialog.Builder(this).setTitle("Shake to speak")
            .setMessage("Three deliberate shakes start Speak while HeySig is open. A short vibration acknowledges the gesture. For screen-off use, choose Sleeping-watch shake in More. Movement may trigger speech accidentally.")
            .setPositiveButton(if(choices.getBoolean("shake",false)) "Turn Off" else "Turn On") { _,_ ->
                val enabled=!choices.getBoolean("shake",false)
                if(choices.edit().putBoolean("shake",enabled).commit()) { shake.stop();if(enabled && active) shake.start() }
            }.setNegativeButton("Cancel",null).show()
    }
    private var lastStatus = "Disconnected"
    private fun render() {
        val home = WatchStyle.home(this,WatchStyle.Labels(R.string.speak,R.string.stop,R.string.more,R.string.speech_off,R.string.connect,R.string.speak_command),lastStatus,
            !prefs.getBoolean("voicePrompts",false) && !prefs.getBoolean("spokenResponses",false),
            { if(working) reply("Wait or press Stop") else if(!ready) connect() else speechConsent() },
            { SleepingShakeService.stop(this); generation++; abandon(); working=false; speech?.stop(); send("stop",listOf("stop")) },
            { more() },connected=ready)
        primaryForShake=home.primary; primary=home.primary
        status = home.status
        setContentView(home.view)
    }
    private var wifiCheck: WatchWifiCheck?=null
    private fun checkWifi() { wifiCheck?.close();wifiCheck=WatchWifiCheck(this) { send("status",listOf("status")) }.show() }
    private fun more() {
        val items = arrayOf("Read status","Check last result","Connect phone","Type command","Speech options","Privacy and data","Public privacy policy","Disconnect","Full status","Phone hands-free","Shake to speak","Check Wi-Fi","Sleeping-watch shake")
        AlertDialog.Builder(this).setTitle("More").setItems(items) { _,index ->
            when(index) {
                12 -> SleepingShakeOptions.show(this)
                11 -> checkWifi()
                10 -> shakeOptions()
                2 -> connect()
                0 -> send("status",listOf("status"))
                1 -> {
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
                7 -> { disconnect(); reply("Disconnected. No command will replay.") }
                9 -> handsFreeControls()
                8 -> AlertDialog.Builder(this).setTitle("Status").setMessage(lastStatus).setPositiveButton("Close",null).show()
            }
        }.setNegativeButton("Close",null).show()
    }
    private fun speechOptions() {
        val keys = arrayOf("voicePrompts","spokenResponses")
        AlertDialog.Builder(this).setTitle("Speech")
            .setMultiChoiceItems(arrayOf("Voice prompts","Spoken responses"),keys.map { prefs.getBoolean(it,false) }.toBooleanArray()) { dialog,index,value ->
                if(!prefs.edit().putBoolean(keys[index],value).commit()) {
                    (dialog as AlertDialog).listView.setItemChecked(index,!value); reply("Could not save speech choice")
                }
                if(!value) speech?.stop()
            }.setNeutralButton("Test reply") { _,_ ->
                reply(if(prefs.getBoolean("spokenResponses",false)) "This is a HeySig spoken reply test." else "Spoken responses are off. Turn them on to test speech.")
            }
            .setPositiveButton("Done",null).setOnDismissListener { render() }.show()
    }
    override fun onResume() {
        super.onResume(); active = true; SleepingShakeService.activityVisible = true
        if(getSharedPreferences("heysig-gesture",MODE_PRIVATE).getBoolean("shake",false)) shake.start()
        lastStatus = getString(R.string.watch_disconnected); status.text = lastStatus; primary.setText(R.string.connect)
        val current = ++listenerGeneration
        val receiver = MessageClient.OnMessageReceivedListener { event ->
            if(active && current == listenerGeneration) onMessageReceived(event)
        }
        listener = receiver
        messages.addListener(receiver).addOnSuccessListener {
            if(!active || current != listenerGeneration) messages.removeListener(receiver)
            else { listening = true; drainSpeech() }
        }.addOnFailureListener { reply("Wear OS Data Layer unavailable. Check Google Play services.") }
    }
    override fun onPostResume() { super.onPostResume(); consumeShakeTap(); drainSpeech() }
    private fun drainSpeech() {
        if(!active || !listening) return
        val action = deferred ?: return
        deferred = null
        // Reconnect only for this freshly captured, user-reviewed speech request.
        // A prior submitted request is never resubmitted.
        rpc(JSONObject().put("op","hello")) {
            ready = it.optString("phase") == "READY"
            if(ready) { lastStatus=getString(R.string.watch_connected); status.text=lastStatus; primary.setText(R.string.speak); action() }
            else reply(it.optString("text","Reconnect the phone before speaking"))
        }
    }
    override fun onPause() {
        wifiCheck?.close();wifiCheck=null;
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
            else speechConsent()
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
    private fun abandon() { pending.values.forEach { handler.removeCallbacks(it.timeout) }; pending.clear(); updateRequestScreen() }
    private fun disconnect(resetConversation: Boolean = true) {
        generation++; ready = false; working = false; abandon()
        if(resetConversation) conversation = UUID.randomUUID().toString()
    }
    private fun reply(text: String, prompt: Boolean = false) {
        lastStatus = text; status.text = text
        primary.setText(if(ready) R.string.speak else R.string.connect)
        if(active && prefs.getBoolean(if(prompt) "voicePrompts" else "spokenResponses",false)) {
            if(!speechReady) showSpeechIssue(speechIssue)
            else if(speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-result") != TextToSpeech.SUCCESS)
                showSpeechIssue("Speech output could not start. Check watch text-to-speech settings.")
        }
    }
    private fun showSpeechIssue(issue: String) {
        lastStatus = "$lastStatus\n$issue"; status.text = lastStatus
        AlertDialog.Builder(this).setTitle("Spoken reply unavailable").setMessage(issue)
            .setPositiveButton("Close",null).show()
    }
    private fun handsFreeControls() {
        val commands=arrayOf("hands free on","pause listening for 30 minutes","hands free off","hands free status","end hands free session")
        AlertDialog.Builder(this).setTitle("Phone hands-free")
            .setItems(arrayOf("On","Pause 30 minutes","Off","Status","End session")) { _,index -> send(commands[index],listOf(commands[index])) }
            .setNegativeButton("Close",null).show()
    }
    private fun connect() {
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
                    rpc(JSONObject().put("op","hello")) {
                        ready=it.optString("phase")=="READY";reply(it.optString("text","Connection not ready"))
                    }
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
            waiting.reply(answer)
        } catch(e:Exception) {
            ready = false; working = false; abandon()
            reply("Invalid phone reply. Check the phone; no command is retried.")
        }
    }
    private fun send(text: String, alternatives: List<String>) {
        if(WatchWifiCheck.requested(text)) { checkWifi();return }
        val local = VoicePreference.parse(text)
        if(local != null) {
            if(alternatives.any { VoicePreference.parse(it) != local }) { reply("Speech alternatives disagree"); return }
            if(!prefs.edit().putBoolean(local.key,local.enabled).commit()) { reply("Could not save speech choice"); return }
            speech?.stop()
            render()
            reply(local.description,local.prompts); return
        }
        if(!active || !ready) { reply("Connect phone first"); return }
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
    private fun speechConsent() {
        if(!prefs.getBoolean("speechDisclosureV1",false)) {
            AlertDialog.Builder(this).setTitle("Optional speech recognition").setMessage(PrivacyNotice.SPEECH)
                .setNegativeButton("Not now",null).setPositiveButton("Continue") { _,_ ->
                    if(prefs.edit().putBoolean("speechDisclosureV1",true).commit()) launchSpeech() else reply("Could not save speech choice")
                }.show()
        } else launchSpeech()
    }
    private fun launchSpeech() {
        speech?.stop()
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if(prefs.getBoolean("voicePrompts",false)) putExtra(RecognizerIntent.EXTRA_PROMPT,"HeySig command")
            },41)
        } catch(e:Exception) { reply("Speech service unavailable. Use typed commands.") }
    }
    @Deprecated("Platform recognition/document result adapter")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code,result,data)
        if(code == 41 && result == RESULT_OK) {
            val alternatives = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty().take(5)
            val captured = SystemClock.elapsedRealtime()
            if(alternatives.isNotEmpty()) deferred = {
                // Leaving for recognition disconnected the listener. Require explicit reconnect;
                // retain the words only long enough for a user-reviewed send dialog.
                if(SystemClock.elapsedRealtime()-captured < 15000) {
                    AlertDialog.Builder(this).setTitle("Review recognized command").setMessage(alternatives[0])
                        .setNegativeButton("Cancel",null).setPositiveButton("Send") { _,_ ->
                            if(SystemClock.elapsedRealtime()-captured < 15000) send(alternatives[0],alternatives)
                            else reply("Speech expired. Please speak again.")
                        }.show()
                } else reply("Speech expired. Please speak again.")
            }
        }
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
                if(working) { reply("Wait for the result or Stop before deleting watch data"); return@setNegativeButton }
                AlertDialog.Builder(this).setTitle("Delete watch data?")
                    .setMessage("Android will close HeySig and erase its private data and permission choices on this watch, including phone selection, request identifiers, shake and speech choices. Does not change the aids or remove phone/provider/exported data.")
                    .setNegativeButton("Keep data",null).setPositiveButton("Delete") { _,_ ->
                        disconnect(); deferred = null; exportDocument = null; speech?.stop()
                        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                        if(!manager.clearApplicationUserData()) reply("Android could not clear watch data. Use the app's storage settings.")
                    }.show()
            }.show()
    }
}
