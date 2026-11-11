package com.heysig.watch

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.*
import com.heysig.companion.CompanionSafety
import com.heysig.noise.WatchAutomaticProfile
import org.json.JSONObject
import java.util.UUID

/** Selected-phone, session-bound RPC. No persistent payloads, automatic retry or audio transfer. */
internal class WatchAutomaticClient(private val context:Context,private val ended:(String)->Unit):AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private val messages=Wearable.getMessageClient(context)
    private val node=context.getSharedPreferences("heysig-watch-v1",0).getString("phone",null)
    private var session:String?=null
    private var closed=false
    private data class Pending(val timeout:Runnable,val done:(JSONObject)->Unit)
    private val pending=mutableMapOf<String,Pending>()
    private val listener=MessageClient.OnMessageReceivedListener { event ->
        if(event.path==WatchAutomaticProfile.PATH && event.sourceNodeId==node && event.data.size in 1..WatchAutomaticProfile.MAX_BYTES) {
            val bytes=event.data.copyOf()
            handler.post {
                if(!closed) try {
                    val reply=JSONObject(String(bytes,Charsets.UTF_8))
                    if(reply.optString("op")=="closed") {
                        if(session!=null && reply.optString("session")==session) ended(reply.optString("text","Phone Automatic stopped"))
                    } else {
                        val id=reply.getString("rpc");val waiting=pending.remove(id)
                        if(waiting!=null) {
                            handler.removeCallbacks(waiting.timeout)
                            check(reply.getString("profile")==WatchAutomaticProfile.ID) { "Update both phone and watch" }
                            val returned=reply.getString("session");CompanionSafety.id(returned)
                            check(session==null || session==returned) { "Phone Automatic session changed" }
                            session=returned;waiting.done(reply)
                        }
                    }
                } catch(e:Exception) { ended(e.message ?: "Invalid phone Automatic reply") }
                bytes.fill(0)
            }
        }
    }
    fun connect(done:(JSONObject)->Unit) {
        if(node==null) { ended("Select your phone in More → Connect phone first");return }
        messages.addListener(listener).addOnSuccessListener {
            if(closed) { messages.removeListener(listener);return@addOnSuccessListener }
            Wearable.getCapabilityClient(context).getCapability(WatchAutomaticProfile.CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { result ->
                    if(closed) return@addOnSuccessListener
                    if(result.nodes.none { it.id==node }) ended("On the phone, choose watch microphone in Noise → Choose programs and turn Automatic On first")
                    else request("hello",done=done)
                }.addOnFailureListener { ended("Phone connection unavailable; restart explicitly") }
        }.addOnFailureListener { ended("Watch connection unavailable") }
    }
    fun request(op:String,extra:JSONObject=JSONObject(),done:(JSONObject)->Unit) {
        if(closed || node==null) return
        check(pending.size<2) { "Wait for the current watch request" }
        val id=UUID.randomUUID().toString()
        val timeout=Runnable { if(pending.remove(id)!=null) ended("Phone did not confirm the request; Automatic stopped without retry") }
        val body=extra.put("op",op).put("rpc",id)
        session?.let { body.put("session",it) }
        val bytes=body.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size<=WatchAutomaticProfile.MAX_BYTES) { "Watch request too large" }
        pending[id]=Pending(timeout,done);handler.postDelayed(timeout,5_000)
        messages.sendMessage(node,WatchAutomaticProfile.PATH,bytes).addOnFailureListener {
            if(!closed) ended("Phone disconnected; Automatic stopped without retry")
        }
    }
    fun stopPhone() {
        if(node!=null && session!=null) {
            val body=JSONObject().put("op","stop").put("rpc",UUID.randomUUID().toString()).put("session",session)
            messages.sendMessage(node,WatchAutomaticProfile.PATH,body.toString().toByteArray(Charsets.UTF_8))
        }
    }
    override fun close() {
        if(closed) return
        closed=true;messages.removeListener(listener)
        pending.values.forEach { handler.removeCallbacks(it.timeout) };pending.clear();handler.removeCallbacksAndMessages(null)
    }
}
