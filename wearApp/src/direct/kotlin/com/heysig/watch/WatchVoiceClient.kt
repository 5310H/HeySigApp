package com.heysig.watch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.wearable.*
import com.heysig.companion.CompanionSafety
import com.heysig.voice.HandsFreeCompanion
import com.heysig.voice.WatchSpeechChoices
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Selected-node, one-use request client. No discovery chooser, persistent words, or command retry. */
internal class WatchVoiceClient(private val context: Context) : AutoCloseable {
    private val prefs=context.getSharedPreferences("heysig-watch-v1",0)
    private val messages=Wearable.getMessageClient(context)
    private val handler=Handler(Looper.getMainLooper())
    private data class Pending(val timeout: Runnable,val reply: (JSONObject)->Unit)
    private val pending=mutableMapOf<String,Pending>()
    private var generation=0
    private var node: String?=null
    private var path=CompanionSafety.PATH
    private var receiver: MessageClient.OnMessageReceivedListener?=null
    val connectedRoute get() = path
    var choices=WatchSpeechChoices(); private set
    private fun failure(text: String)=JSONObject().put("phase","UNKNOWN").put("text",text)
    fun connect(done: (JSONObject)->Unit) {
        close(); choices=WatchSpeechChoices()
        val selected=prefs.getString("phone",null)
        if(selected==null) { done(failure("Select your phone once in More → Connect phone.")); return }
        node=selected
        val current=generation
        var finished=false
        fun complete(reply: JSONObject) {
            if(current!=generation || finished) return
            finished=true;done(reply)
        }
        val deadline=Runnable { complete(failure("Phone unavailable. Open HeySig on the phone or start its hands-free session, then shake again.")) }
        handler.postDelayed(deadline,10_000)
        val listener=MessageClient.OnMessageReceivedListener { event ->
            val bytes=event.data.copyOf(); val sender=event.sourceNodeId;val route=event.path
            handler.post {
                if(current!=generation || sender!=node || route!=path || bytes.size !in 1..CompanionSafety.MAX_BYTES) return@post
                try {
                    val reply=JSONObject(String(bytes,Charsets.UTF_8))
                    val rpc=reply.getString("rpc")
                    val waiting=pending.remove(rpc) ?: return@post
                    handler.removeCallbacks(waiting.timeout)
                    choices=WatchSpeechChoices.received(reply.opt("voicePrompts") as? Boolean,reply.opt("spokenResponses") as? Boolean)
                    waiting.reply(reply)
                } catch(e: Exception) {
                    val callbacks=pending.values.toList();pending.clear()
                    callbacks.forEach { handler.removeCallbacks(it.timeout);it.reply(failure("Invalid phone reply. Nothing is retried.")) }
                }
            }
        }
        receiver=listener
        fun hello(route: String) {
            if(current!=generation || finished) return
            path=route
            rpc(JSONObject().put("op","hello")) { reply -> handler.removeCallbacks(deadline);complete(reply) }
        }
        fun ordinary() {
            Wearable.getCapabilityClient(context).getCapability(CompanionSafety.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { result ->
                    if(current!=generation || finished) return@addOnSuccessListener
                    if(result.nodes.any { it.id==selected }) hello(CompanionSafety.PATH)
                    else { handler.removeCallbacks(deadline);complete(failure("Phone connection is off. Enable this watch on the phone or start its hands-free session.")) }
                }.addOnFailureListener { handler.removeCallbacks(deadline);complete(failure("Phone discovery unavailable. Shake again when connected.")) }
        }
        messages.addListener(listener).addOnSuccessListener {
            if(current!=generation || finished) { messages.removeListener(listener);return@addOnSuccessListener }
            Wearable.getCapabilityClient(context).getCapability(HandsFreeCompanion.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { result ->
                    if(current!=generation || finished) return@addOnSuccessListener
                    if(result.nodes.any { it.id==selected }) hello(HandsFreeCompanion.PATH) else ordinary()
                }.addOnFailureListener { if(current==generation && !finished) ordinary() }
        }.addOnFailureListener { handler.removeCallbacks(deadline);complete(failure("Watch connection unavailable. Check Google Play services.")) }
    }
    fun command(text: String,alternatives: List<String>,conversation: String,fresh: ()->Boolean,done: (JSONObject)->Unit) {
        val id=UUID.randomUUID().toString()
        try { CompanionSafety.command(id,conversation,text,alternatives) }
        catch(e: Exception) { done(failure("No valid speech recognized. Shake to try again."));return }
        rpc(JSONObject().put("op","prepare").put("id",id)) { lease ->
            if(lease.optString("phase")!="LEASE" || !fresh()) { done(failure("Request expired. Nothing sent; shake to try again."));return@rpc }
            val token=lease.optString("token")
            if(token.isEmpty()) { done(failure("Phone grant unavailable. Nothing sent."));return@rpc }
            if(text.trim().lowercase() !in listOf("stop","cancel") && !prefs.edit().putString("lastRequest",id).putString("lastNode",node).commit()) {
                done(failure("Could not remember request. Nothing sent."));return@rpc
            }
            rpc(JSONObject().put("op","command").put("id",id).put("conversation",conversation)
                .put("token",token).put("text",text).put("alternatives",JSONArray(alternatives)),done)
        }
    }
    fun environment(body: JSONObject,done: (JSONObject)->Unit) {
        if(path!=CompanionSafety.PATH) { done(failure("For monitoring, end phone hands-free and enable Watch connection with HeySig open on the phone."));return }
        val id=UUID.randomUUID().toString()
        val started=SystemClock.elapsedRealtime()
        rpc(JSONObject().put("op","prepare").put("id",id).put("scope","environment")) { lease ->
            if(lease.optString("phase")!="LEASE" || SystemClock.elapsedRealtime()-started>5000) {
                done(failure("Monitoring grant expired; reconnect explicitly"));return@rpc
            }
            rpc(JSONObject().put("op","environment").put("id",id).put("token",lease.getString("token")).put("body",body),done)
        }
    }
    fun lastResult(done: (JSONObject)->Unit) {
        val id=prefs.getString("lastRequest",null)
        if(id==null || prefs.getString("lastNode",null)!=node) { done(failure("No retained request for this phone"));return }
        rpc(JSONObject().put("op","result").put("id",id),done)
    }
    private fun rpc(body: JSONObject,done: (JSONObject)->Unit) {
        val selected=node ?: run { done(failure("Phone not selected"));return }
        if(pending.size>=4) { done(failure("Wait for the current result"));return }
        val id=UUID.randomUUID().toString(); val current=generation
        val timeout=Runnable { pending.remove(id)?.reply?.invoke(failure("No confirmed result. Check last result on the watch; no command is retried.")) }
        val bytes=body.put("rpc",id).toString().toByteArray(Charsets.UTF_8)
        if(bytes.size>CompanionSafety.MAX_BYTES) { done(failure("Command too large. Nothing sent."));return }
        pending[id]=Pending(timeout,done);handler.postDelayed(timeout,30_000)
        messages.sendMessage(selected,path,bytes).addOnFailureListener {
            if(current!=generation) return@addOnFailureListener
            val waiting=pending.remove(id) ?: return@addOnFailureListener
            handler.removeCallbacks(timeout);waiting.reply(failure("Phone disconnected. Check last result; nothing is retried."))
        }
    }
    override fun close() {
        generation++
        receiver?.let { messages.removeListener(it) };receiver=null
        pending.values.forEach { handler.removeCallbacks(it.timeout) };pending.clear()
        handler.removeCallbacksAndMessages(null);node=null
    }
}
