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
    private lateinit var layout: LinearLayout
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var promptToggle: CheckBox? = null
    private var responseToggle: CheckBox? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        phone = prefs.getString("phone",null)
        speech = TextToSpeech(this) { speechReady = it == TextToSpeech.SUCCESS; if(speechReady) speech?.language = Locale.US }
        render()
    }
    private fun label(text: String, size: Float = 16f) {
        layout.addView(TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(0,8,0,8) })
    }
    private fun button(text: String, action: () -> Unit) {
        layout.addView(Button(this).apply { this.text = text; minHeight = (48*resources.displayMetrics.density).toInt(); setOnClickListener { action() } })
    }
    private fun render() {
        val side = if(resources.configuration.isScreenRound) 36 else 16
        val density = resources.displayMetrics.density
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((side*density).toInt(),(48*density).toInt(),(side*density).toInt(),(48*density).toInt())
            setBackgroundColor(Color.BLACK)
        }
        label("HeySig",22f)
        status = TextView(this).apply { text = "Connection off. Open HeySig on your paired phone, enable this watch, then Connect phone here."; textSize = 16f; setTextColor(Color.WHITE); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        layout.addView(status)
        label("Selected phone: ${prefs.getString("phoneLabel","none")}")
        label("Commands and replies use Google Play services. Keep HeySig open on your phone. Details in Privacy and data.")
        button("Connect phone") { connect() }
        button("Speak command") { if(working) reply("Wait or press Stop") else speechConsent() }
        button("Read status") { send("status",listOf("status")) }
        button("Check last result") {
            val id = prefs.getString("lastRequest",null)
            val node = prefs.getString("lastNode",null)
            if(id == null || node != phone) reply("No retained request for this phone")
            else if(!ready) reply("Connect the selected phone first")
            else rpc(JSONObject().put("op","result").put("id",id)) { reply(it.optString("text","No confirmed result")) }
        }
        button("Stop") {
            // Stop is a NEW bounded request. The interrupted command is never resubmitted.
            generation++; abandon(); working = false; speech?.stop()
            send("stop",listOf("stop"))
        }
        label("Typed commands use the same phone validation as speech.")
        val input = EditText(this).apply { hint = "program two / volume ten"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setSingleLine(); this@WatchActivity.layout.addView(this) }
        button("Send typed command") { send(input.text.toString(),listOf(input.text.toString())) }
        button("Clear display") { input.text.clear(); reply("Display cleared. Saved preferences and phone result history are unchanged.") }
        label(PrivacyNotice.SYNTHESIS)
        fun toggle(text: String, key: String): CheckBox = CheckBox(this).apply {
            this.text = text; setTextColor(Color.WHITE); minHeight = (48*density).toInt()
            isChecked = prefs.getBoolean(key,false)
            var reverting = false
            setOnCheckedChangeListener { box,checked ->
                if(reverting) return@setOnCheckedChangeListener
                if(!prefs.edit().putBoolean(key,checked).commit()) {
                    reverting = true; box.isChecked = !checked; reverting = false
                    reply("Could not save speech choice")
                }
                if(!checked) speech?.stop()
            }
            this@WatchActivity.layout.addView(this)
        }
        promptToggle = toggle("Voice prompts","voicePrompts")
        responseToggle = toggle("Spoken responses","spokenResponses")
        button("Privacy and data") { privacy() }
        button("Public privacy policy") {
            if(BuildConfig.PRIVACY_POLICY_URL.isEmpty()) reply("Public policy/contact is not configured yet; required before store submission.")
            else try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
            catch(e:Exception) { reply("Open Privacy and data on your phone to view the public policy") }
        }
        button("Disconnect") { disconnect(); reply("Disconnected. No command will replay.") }
        WatchStyle.prepareColumn(layout)
        WatchStyle.polish(layout)
        setContentView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; setBackgroundColor(Color.BLACK); addView(layout,ViewGroup.LayoutParams(-1,-2)) })
    }
    override fun onResume() {
        super.onResume(); active = true
        status.text = "Not connected. Tap Connect phone."
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
    override fun onPostResume() { super.onPostResume(); drainSpeech() }
    private fun drainSpeech() {
        if(!active || !listening) return
        val action = deferred ?: return
        deferred = null
        // Reconnect only for this freshly captured, user-reviewed speech request.
        // A prior submitted request is never resubmitted.
        rpc(JSONObject().put("op","hello")) {
            ready = it.optString("phase") == "READY"
            if(ready) { status.text = "Phone connected. Review the recognized command."; action() }
            else reply(it.optString("text","Reconnect the phone before speaking"))
        }
    }
    override fun onPause() {
        active = false; listenerGeneration++; disconnect(false); listener?.let { messages.removeListener(it) }; listener = null; listening = false; speech?.stop()
        super.onPause()
    }
    override fun onDestroy() { exportDocument = null; speech?.shutdown(); super.onDestroy() }
    private fun abandon() { pending.values.forEach { handler.removeCallbacks(it.timeout) }; pending.clear() }
    private fun disconnect(resetConversation: Boolean = true) {
        generation++; ready = false; working = false; abandon()
        if(resetConversation) conversation = UUID.randomUUID().toString()
    }
    private fun reply(text: String, prompt: Boolean = false) {
        status.text = text
        if(active && speechReady && prefs.getBoolean(if(prompt) "voicePrompts" else "spokenResponses",false)) speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"heysig-result")
    }
    private fun connect() {
        if(!active || !listening) { reply("Connection is starting; try Connect phone again"); return }
        disconnect()
        val current = generation
        Wearable.getCapabilityClient(this).getCapability(CompanionSafety.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capability ->
                if(!active || current != generation) return@addOnSuccessListener
                val nodes = capability.nodes.sortedBy { it.displayName }
                if(nodes.isEmpty()) { reply("No enabled phone found. Pair this watch normally and install HeySig on both devices. On the phone, open Watch connection and enable this watch."); return@addOnSuccessListener }
                AlertDialog.Builder(this).setTitle("Choose phone")
                    .setItems(nodes.map { it.displayName }.toTypedArray()) { _,index ->
                        val node = nodes[index]
                        if(!active) return@setItems
                        disconnect()
                        phone = node.id
                        if(!prefs.edit().putString("phone",node.id).putString("phoneLabel",node.displayName).commit()) { reply("Could not save phone selection"); return@setItems }
                        rpc(JSONObject().put("op","hello")) {
                            ready = it.optString("phase") == "READY"
                            reply(it.optString("text","Connection not ready"))
                        }
                    }.show()
            }.addOnFailureListener { if(active) reply("Could not discover phone. Check watch pairing and Google Play services.") }
    }
    private fun rpc(body: JSONObject, callback: (JSONObject) -> Unit) {
        val node = phone
        if(!active || !listening || node == null) { callback(JSONObject().put("phase","UNKNOWN").put("text","Connect phone first")); return }
        if(pending.size >= 4) { callback(JSONObject().put("phase","BUSY").put("text","Wait for the pending result")); return }
        val id = UUID.randomUUID().toString()
        val current = generation
        val timeout = Runnable {
            if(pending.remove(id) != null && active && current == generation) {
                ready = false
                callback(JSONObject().put("phase","UNKNOWN").put("text","No confirmed result. Reconnect and use Check last result. No automatic retry."))
            }
        }
        pending[id] = Awaiting(node,timeout,callback)
        handler.postDelayed(timeout,30000)
        val data = body.put("rpc",id).toString().toByteArray(Charsets.UTF_8)
        if(data.size > CompanionSafety.MAX_BYTES) { pending.remove(id); handler.removeCallbacks(timeout); callback(JSONObject().put("phase","BLOCKED").put("text","Command too large")); return }
        messages.sendMessage(node,CompanionSafety.PATH,data).addOnFailureListener {
            val request = pending.remove(id)
            handler.removeCallbacks(timeout)
            if(request != null && active && current == generation) { ready = false; request.reply(JSONObject().put("phase","UNKNOWN").put("text","Phone disconnected. Reconnect and check the last result. No retry.")) }
        }
    }
    override fun onMessageReceived(event: MessageEvent) {
        if(!active || event.path != CompanionSafety.PATH || event.data.size !in 1..CompanionSafety.MAX_BYTES) return
        try {
            val answer = JSONObject(String(event.data,Charsets.UTF_8))
            val id = answer.getString("rpc")
            val waiting = pending[id] ?: return
            if(waiting.node != event.sourceNodeId) return
            pending.remove(id); handler.removeCallbacks(waiting.timeout)
            waiting.reply(answer)
        } catch(e:Exception) {
            ready = false; working = false; abandon()
            reply("Invalid phone reply. Check the phone; no command is retried.")
        }
    }
    private fun send(text: String, alternatives: List<String>) {
        val local = VoicePreference.parse(text)
        if(local != null) {
            if(alternatives.any { VoicePreference.parse(it) != local }) { reply("Speech alternatives disagree"); return }
            if(!prefs.edit().putBoolean(local.key,local.enabled).commit()) { reply("Could not save speech choice"); return }
            speech?.stop()
            if(local.prompts) promptToggle?.isChecked = local.enabled else responseToggle?.isChecked = local.enabled
            reply(local.description,local.prompts); return
        }
        if(!active || !ready) { reply("Connect phone first"); return }
        if(working) { reply("Wait for the current result or press Stop"); return }
        val id = UUID.randomUUID().toString()
        try { CompanionSafety.command(id,conversation,text,alternatives) } catch(e:Exception) { reply("Use a command of 1–512 characters"); return }
        val current = generation
        val deadline = SystemClock.elapsedRealtime()+15000
        working = true
        status.text = "Preparing request…"
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
            status.text = "Sending requested command…"
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
                    .setMessage("Saves phone selection, request identifiers and speech choices to your chosen provider, which may upload them. Delete exported copies separately.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Choose location") { _,_ ->
                        exportDocument = JSONObject(prefs.all).toString(2)
                        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"heysig-watch-data.json") },42) }
                        catch(e:Exception) { exportDocument = null; reply("No document provider available; use Android app settings to manage local data") }
                    }.show()
            }
            .setNegativeButton("Delete local data") { _,_ ->
                if(working) { reply("Wait for the result or Stop before deleting watch data"); return@setNegativeButton }
                AlertDialog.Builder(this).setTitle("Delete watch data?")
                    .setMessage("Android will close HeySig and erase its private data and permission choices on this watch, including phone selection, request identifiers and speech choices. Does not change the aids or remove phone/provider/exported data.")
                    .setNegativeButton("Keep data",null).setPositiveButton("Delete") { _,_ ->
                        disconnect(); deferred = null; exportDocument = null; speech?.stop()
                        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                        if(!manager.clearApplicationUserData()) reply("Android could not clear watch data. Use the app's storage settings.")
                    }.show()
            }.show()
    }
}
