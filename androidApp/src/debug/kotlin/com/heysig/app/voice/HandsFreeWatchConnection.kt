package com.heysig.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.heysig.app.*
import com.heysig.companion.CompanionSafety
import com.heysig.control.AppIntent
import com.heysig.voice.HandsFreeCompanion
import org.json.JSONObject

/** Existing authenticated loopback development transport, never included in production. */
class HandsFreeWatchConnection(private val context: Context,private val dispatch: (String,String,(String)->Unit)->Unit) : AutoCloseable {
    private val handler=Handler(Looper.getMainLooper())
    private var bridge: WatchBridge?=null
    private var active=false
    fun start() {
        if(!context.getSharedPreferences("heysig-handsfree",0).getBoolean("watchControlV1",false) ||
            !context.getSharedPreferences("heysig-watch-dev",0).contains("token")) return
        active=true
        bridge=WatchBridge(context) { request,reply -> handler.post {
            if(!active) { reply(JSONObject().put("phase","BLOCKED").put("text","Session ended"));return@post }
            try {
                val id=request.getString("id");val text=request.getString("text");val conversation=request.getString("conversation")
                val choices=request.optJSONArray("alternatives")?.let { values -> (0 until values.length()).map { values.getString(it) } } ?: listOf(text)
                CompanionSafety.command(id,conversation,text,choices)
                val history=WatchHistory(context)
                history.get("development",id)?.let { reply(it);return@post }
                val identities=choices.map { ManualRuntime.get(context).personalPhrases.identity(it,ManualRuntime.get(context).fitting) }.filterNot { it is AppIntent.Unsupported }.distinct()
                check(identities.size<=1) { "Speech alternatives disagree" }
                check(history.put("development",id,JSONObject().put("phase","UNKNOWN").put("text","Started; do not replay")))
                dispatch(text,"development:$conversation") { result ->
                    val prompt=HandsFreeService.running?.isPromptFor("development:$conversation")==true
                    val value=JSONObject().put("phase",if(prompt) "CLARIFY" else "FINAL").put("prompt",prompt).put("text",result)
                    if(history.put("development",id,value)) reply(value) else reply(JSONObject().put("phase","UNKNOWN").put("text","Result storage failed"))
                }
            } catch(e: Exception) { reply(JSONObject().put("phase","BLOCKED").put("text","Watch request invalid or unavailable; nothing retried")) }
        } }
    }
    override fun close() { active=false;bridge?.close();bridge=null }
}
