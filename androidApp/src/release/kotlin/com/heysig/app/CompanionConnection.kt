package com.heysig.app

import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.heysig.companion.CommandLeaseGate
import com.heysig.companion.CompanionSafety
import org.json.JSONObject
import java.util.UUID

/** Activity-scoped only: no service, background playback, raw socket or durable command queue. */
class CompanionConnection(
    private val activity: Activity,
    private val dispatch: (String, JSONObject, (JSONObject) -> Unit) -> Unit,
    private val lookup: (String, String) -> JSONObject?,
    private val disconnected: () -> Unit,
    private val report: (String) -> Unit
) : AutoCloseable, MessageClient.OnMessageReceivedListener {
    private val prefs = activity.getSharedPreferences("heysig-companion",Activity.MODE_PRIVATE)
    private val messages = Wearable.getMessageClient(activity)
    private val capabilities = Wearable.getCapabilityClient(activity)
    private var gate: CommandLeaseGate? = null
    private var selected: String? = null
    private var generation = 0
    private var foreground = false
    private var listener: MessageClient.OnMessageReceivedListener? = null
    private var connectionStatus: TextView? = null
    val enabled get() = gate != null && foreground
    fun resume() {
        foreground = true; refreshStatus()
        // Returning from recognition/import must preserve that feature's result.
        // The connection card already shows Off and the explicit enable action.
    }
    private fun refreshStatus() {
        connectionStatus?.text = activity.getString(R.string.watch_status,prefs.getString("label","none selected"),if(enabled) "Enabled while this phone stays open and unlocked" else "Off — enable before connecting on the watch")
    }
    fun accepts(node: String) = enabled && selected == node

    fun render(layout: LinearLayout) {
        fun line(text: String) = TextView(activity).also { it.text = text; it.textSize = 17f; layout.addView(it) }
        fun button(text: String, action: () -> Unit) { layout.addView(Button(activity).apply { this.text = text; setOnClickListener { action() } }) }
        line("Pair your watch with this Android phone using the watch's companion app, then install HeySig on both devices. No developer tools or relay are needed. iPhone and Chromebook pairing are not supported by this connection.")
        connectionStatus = line(""); refreshStatus()
        button("Find paired watches") {
            if(!foreground) return@button
            val current = generation
            Wearable.getNodeClient(activity).connectedNodes.addOnSuccessListener { nodes ->
                if(!foreground || current != generation) return@addOnSuccessListener
                if(nodes.isEmpty()) { report("No connected watch. Pair/connect it in the watch companion app first."); return@addOnSuccessListener }
                AlertDialog.Builder(activity).setTitle("Select your watch")
                    .setItems(nodes.map { it.displayName }.toTypedArray()) { _, index ->
                        val node = nodes[index]
                        consent(node.id,node.displayName)
                    }.show()
            }.addOnFailureListener { report("Wear OS connection unavailable. Check Google Play services and watch pairing.") }
        }
        button("Enable selected watch") {
            val node = prefs.getString("node",null)
            if(node == null) report("Find and select a paired watch first") else consent(node,prefs.getString("label","watch")!!)
        }
        button("Disconnect watch") { close(); report("Watch disconnected. No requests will replay on reconnect.") }
        button("Forget selected watch") {
            close()
            if(prefs.edit().clear().commit()) { refreshStatus(); report("Watch selection forgotten. System watch pairing and aid settings are unchanged.") }
            else report("Could not remove watch selection")
        }
    }
    private fun consent(node: String, label: String) {
        AlertDialog.Builder(activity).setTitle("Allow $label to control HeySig?")
            .setMessage("Watch command text, speech alternatives and requested-setting replies travel through Google Play services. Data Layer may use encrypted Google-server routing when Bluetooth is unavailable. This phone plays the validated command tones only while HeySig is open and unlocked. Command results are kept locally for up to 24 hours, limited to 64; clear them in Privacy. No command is retried automatically.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Enable watch") { _,_ ->
                if(!foreground) return@setPositiveButton
                if(!prefs.edit().putString("node",node).putString("label",label).commit()) { report("Could not save watch selection"); return@setPositiveButton }
                enable(node)
            }.show()
    }
    private fun enable(node: String) {
        close()
        if(!foreground) return
        val current = ++generation
        selected = node
        val receiver = MessageClient.OnMessageReceivedListener { event ->
            if(foreground && current == generation) onMessageReceived(event)
        }
        listener = receiver
        messages.addListener(receiver).addOnSuccessListener {
            if(!foreground || current != generation) { messages.removeListener(receiver); return@addOnSuccessListener }
            gate = CommandLeaseGate(node) { UUID.randomUUID().toString() }
            refreshStatus()
            capabilities.addLocalCapability(CompanionSafety.CAPABILITY).addOnSuccessListener {
                if(!foreground || current != generation) {
                    if(!enabled) capabilities.removeLocalCapability(CompanionSafety.CAPABILITY)
                }
                else report("Watch connection ready. Open HeySig on the watch and select Connect phone.")
            }.addOnFailureListener {
                if(current == generation) { close(); report("Could not advertise phone connection; no watch commands enabled") }
            }
        }.addOnFailureListener {
            if(current == generation) { close(); report("Wear OS Data Layer unavailable. Check Google Play services.") }
        }
    }
    override fun onMessageReceived(event: MessageEvent) {
        if(event.path != CompanionSafety.PATH || !accepts(event.sourceNodeId) || event.data.size !in 1..CompanionSafety.MAX_BYTES) return
        val node = event.sourceNodeId
        val current = generation
        var rpc: String? = null
        fun reply(value: JSONObject) {
            if(current != generation || !accepts(node)) return
            val data = value.put("rpc",rpc).toString().toByteArray(Charsets.UTF_8)
            if(data.size <= CompanionSafety.MAX_BYTES) messages.sendMessage(node,CompanionSafety.PATH,data)
        }
        try {
            val request = JSONObject(String(event.data,Charsets.UTF_8))
            rpc = request.getString("rpc"); CompanionSafety.id(rpc!!)
            when(request.getString("op")) {
                "hello" -> {
                    gate?.close()
                    gate = CommandLeaseGate(node) { UUID.randomUUID().toString() }
                    reply(JSONObject().put("phase","READY").put("text","Phone connected. Commands use the phone speaker; keep HeySig open and unlocked."))
                }
                "prepare" -> {
                    val id = request.getString("id")
                    val token = checkNotNull(gate).issue(node,id,SystemClock.elapsedRealtime())
                    reply(JSONObject().put("phase","LEASE").put("token",token))
                }
                "result" -> {
                    val id = request.getString("id"); CompanionSafety.id(id)
                    reply(lookup(node,id) ?: JSONObject().put("phase","UNKNOWN").put("text","No retained result. Do not repeat automatically; check the phone and aid state."))
                }
                "command" -> {
                    val alternatives = request.getJSONArray("alternatives")
                    val choices = (0 until alternatives.length()).map { alternatives.getString(it) }
                    val id = request.getString("id")
                    CompanionSafety.command(id,request.getString("conversation"),request.getString("text"),choices)
                    checkNotNull(gate).consume(node,id,request.getString("token"),SystemClock.elapsedRealtime())
                    dispatch(node,request,::reply)
                }
                else -> error("Unsupported companion request")
            }
        } catch(e:Exception) {
            if(rpc != null) reply(JSONObject().put("phase","BLOCKED").put("text","Request rejected or expired. No automatic retry. Reconnect and make a fresh request if needed."))
        }
    }
    override fun close() {
        generation++; gate?.close(); gate = null; selected = null
        refreshStatus()
        listener?.let { messages.removeListener(it) }; listener = null
        capabilities.removeLocalCapability(CompanionSafety.CAPABILITY)
        disconnected()
    }
    fun pause() { foreground = false; close() }
}
