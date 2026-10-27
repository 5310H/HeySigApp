package com.heysig.app

import android.content.Context
import org.json.JSONObject

/** Read effective phone choices at reply time, including the existing legacy response fallback. */
object CompanionSpeech {
    fun attach(context: Context, reply: JSONObject): JSONObject {
        val p=context.getSharedPreferences("heysig-core-v1",0)
        return reply.put("voicePrompts",p.getBoolean("voicePrompts",false))
            .put("spokenResponses",p.getBoolean("spokenResponses",p.getBoolean("spokenResults",false)))
    }
}
