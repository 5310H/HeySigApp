package com.heysig.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.wearable.*
import com.heysig.app.ManualRuntime
import com.heysig.app.WatchHistory
import com.heysig.companion.*
import com.heysig.control.AppIntent
import com.heysig.voice.HandsFreeCompanion
import org.json.JSONObject
import java.util.UUID

/** Listener exists only inside the explicitly started foreground session. No manifest background receiver. */
class HandsFreeWatchConnection(private val context: Context,private val dispatch: (String,String,(String)->Unit)->Unit) : AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private val messages=Wearable.getMessageClient(context)
    private val capabilities=Wearable.getCapabilityClient(context)
    private val selected=context.getSharedPreferences("heysig-companion",0).getString("node",null)
    private val history=WatchHistory(context)
    private var gate: CommandLeaseGate?=null
    private var active=false
    private val listener=MessageClient.OnMessageReceivedListener { event ->
        if(event.path==HandsFreeCompanion.PATH && event.data.size in 1..CompanionSafety.MAX_BYTES) {
            val node=event.sourceNodeId;val data=event.data.copyOf()
            handler.post { receive(node,data) }
        }
    }
    fun start() {
        if(selected==null || !context.getSharedPreferences("heysig-handsfree",0).getBoolean("watchControlV1",false)) return
        active=true;gate=CommandLeaseGate(selected) { UUID.randomUUID().toString() }
        messages.addListener(listener).addOnSuccessListener {
            if(active) capabilities.addLocalCapability(HandsFreeCompanion.CAPABILITY)
            else messages.removeListener(listener)
        }.addOnFailureListener { close() }
    }
    private fun receive(node: String,data: ByteArray) {
        if(!active || node!=selected || context.getSharedPreferences("heysig-companion",0).getString("node",null)!=node) return
        var rpc: String?=null
        fun reply(value: JSONObject) {
            if(!active) return
            val bytes=value.put("rpc",rpc).toString().toByteArray(Charsets.UTF_8)
            if(bytes.size<=CompanionSafety.MAX_BYTES) messages.sendMessage(node,HandsFreeCompanion.PATH,bytes)
        }
        try {
            val request=JSONObject(String(data,Charsets.UTF_8));rpc=request.getString("rpc");CompanionSafety.id(rpc!!)
            when(request.getString("op")) {
                "hello" -> { gate?.close();gate=CommandLeaseGate(node) { UUID.randomUUID().toString() };reply(JSONObject().put("phase","READY").put("text","Phone listening ${if(HandsFreeService.running?.isListening==true) "On" else "Off"}. Session connected; commands may play while locked.")) }
                "prepare" -> reply(JSONObject().put("phase","LEASE").put("token",checkNotNull(gate).issue(node,request.getString("id"),SystemClock.elapsedRealtime())))
                "result" -> {
                    val id=request.getString("id");CompanionSafety.id(id)
                    reply(history.get(node,id) ?: JSONObject().put("phase","UNKNOWN").put("text","No retained result. Do not retry automatically."))
                }
                "command" -> {
                    val id=request.getString("id");val conversation=request.getString("conversation");val text=request.getString("text")
                    val alternatives=request.getJSONArray("alternatives").let { values -> (0 until values.length()).map { values.getString(it) } }
                    CompanionSafety.command(id,conversation,text,alternatives)
                    checkNotNull(gate).consume(node,id,request.getString("token"),SystemClock.elapsedRealtime())
                    history.get(node,id)?.let { reply(it);return }
                    val fit=ManualRuntime.get(context).fitting
                    val identities=alternatives.map { ManualRuntime.get(context).personalPhrases.identity(it,fit) }.filterNot { it is AppIntent.Unsupported }.distinct()
                    if(identities.size>1) { reply(JSONObject().put("phase","CLARIFY").put("text","Speech alternatives disagree; nothing sent"));return }
                    check(history.put(node,id,JSONObject().put("phase","UNKNOWN").put("text","Request started; never replay automatically"))) { "Watch journal unavailable" }
                    dispatch(text,"paired:$node:$conversation") { result ->
                        val prompt=HandsFreeService.running?.isPromptFor("paired:$node:$conversation")==true
                        val value=JSONObject().put("phase",if(prompt) "CLARIFY" else "FINAL").put("prompt",prompt).put("text",result)
                        if(history.put(node,id,value)) reply(value)
                        else reply(JSONObject().put("phase","UNKNOWN").put("text","Result storage failed; check aid state before another request"))
                    }
                }
                else -> error("Unsupported request")
            }
        } catch(e: Exception) { reply(JSONObject().put("phase","BLOCKED").put("text","Request invalid, expired or unavailable; nothing retried")) }
    }
    override fun close() { active=false;gate?.close();gate=null;messages.removeListener(listener);capabilities.removeLocalCapability(HandsFreeCompanion.CAPABILITY) }
}
