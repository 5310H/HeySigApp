package com.heysig.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.wearable.*
import com.heysig.companion.CommandLeaseGate
import com.heysig.companion.CompanionSafety
import com.heysig.noise.*
import org.json.JSONObject
import java.util.UUID

/** Present only during an explicitly armed phone service; no durable listener or replay. */
internal class WatchAutomaticEndpoint(private val context:Context,private val node:String,
    private val status:()->JSONObject,private val evidence:(MaskingEvidence,Long,Double)->Unit,
    private val stop:(String)->Unit):AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private val messages=Wearable.getMessageClient(context)
    private val capabilities=Wearable.getCapabilityClient(context)
    private val session=UUID.randomUUID().toString()
    private val gate=CommandLeaseGate(node,4096) { UUID.randomUUID().toString() }
    private val samples=WatchAutomaticEvidenceGate(node,session)
    private var closed=false
    private val issued=mutableMapOf<String,Long>()
    var hasPeer=false;private set
    var ready=false;private set
    var lastContact=SystemClock.elapsedRealtime();private set
    private val listener=MessageClient.OnMessageReceivedListener { event ->
        if(event.path==WatchAutomaticProfile.PATH && event.sourceNodeId==node && event.data.size in 1..WatchAutomaticProfile.MAX_BYTES) {
            val bytes=event.data.copyOf()
            handler.post { if(!closed) receive(bytes) }
        }
    }
    fun start() {
        messages.addListener(listener).addOnSuccessListener {
            if(closed) messages.removeListener(listener)
            else capabilityChanges.update(true,{ capabilities.addLocalCapability(WatchAutomaticProfile.CAPABILITY) },{ !closed }).addOnSuccessListener {
                if(!closed) ready=true
            }.addOnFailureListener {
                if(BuildConfig.DEBUG) android.util.Log.w("HeySigWatchAutomatic","Capability registration failed",it)
                handler.post { if(!closed) stop("Watch connection unavailable") }
            }
        }.addOnFailureListener { handler.post { if(!closed) stop("Watch connection unavailable") } }
    }
    private fun receive(bytes:ByteArray) {
        var rpc:String?=null
        fun reply(value:JSONObject) {
            value.put("rpc",rpc).put("session",session).put("profile",WatchAutomaticProfile.ID)
            val data=value.toString().toByteArray(Charsets.UTF_8)
            if(data.size<=WatchAutomaticProfile.MAX_BYTES) messages.sendMessage(node,WatchAutomaticProfile.PATH,data)
                .addOnFailureListener { handler.post { if(!closed) stop("Watch disconnected; restart Automatic explicitly") } }
        }
        try {
            val binding=context.getSharedPreferences("heysig-companion",0)
            check(binding.getString("node",null)==node && binding.getBoolean("foregroundEnabledV1",false)) { "Selected watch changed or disconnected" }
            val request=JSONObject(String(bytes,Charsets.UTF_8));rpc=request.getString("rpc");CompanionSafety.id(rpc!!)
            val op=request.getString("op")
            if(op!="hello" && request.optString("session")!=session) {
                reply(JSONObject().put("active",false).put("text","Old watch session rejected; no action in the current session"))
                return
            }
            lastContact=SystemClock.elapsedRealtime()
            when(op) {
                "hello","status" -> { hasPeer=true;reply(status().put("active",true)) }
                "prepare" -> {
                    issued.entries.removeAll { lastContact-it.value>=5000 }
                    val id=request.getString("id");val token=gate.issue(node,id,lastContact);issued[id]=lastContact
                    reply(status().put("active",true).put("token",token))
                }
                "sample" -> {
                    gate.consume(node,request.getString("id"),request.getString("token"),lastContact)
                    val ageBound=watchAutomaticAgeBound(request.getJSONObject("body").getLong("ageMillis"),lastContact-checkNotNull(issued.remove(request.getString("id"))))
                    val body=request.getJSONObject("body")
                    val (value,capturedAt)=samples.accept(node,session,body.getString("profile"),body.getLong("sequence"),
                        ageBound,lastContact,body.getString("severity"),body.getString("type"),
                        body.getDouble("score"),body.getDouble("rmsDbfs"))
                    evidence(value,capturedAt,body.getDouble("rmsDbfs"))
                    reply(status().put("active",!closed))
                }
                "stop" -> { reply(JSONObject().put("active",false).put("text","Automatic Off"));stop("Watch Automatic Off") }
                else -> error("Unknown watch Automatic request")
            }
        } catch(e:Exception) {
            reply(JSONObject().put("active",false).put("text",e.message ?: "Invalid watch evidence"))
            stop("Watch request rejected; restart explicitly")
        } finally { bytes.fill(0) }
    }
    override fun close() {
        if(closed) return
        closed=true;ready=false;gate.close();samples.close();issued.clear();handler.removeCallbacksAndMessages(null)
        messages.removeListener(listener)
        capabilityChanges.update(false,{ capabilities.removeLocalCapability(WatchAutomaticProfile.CAPABILITY) })
        val body=JSONObject().put("op","closed").put("session",session).put("text","Phone Automatic stopped")
        messages.sendMessage(node,WatchAutomaticProfile.PATH,body.toString().toByteArray(Charsets.UTF_8))
    }
    private companion object { val capabilityChanges=WatchAutomaticCapabilityQueue() }
}
