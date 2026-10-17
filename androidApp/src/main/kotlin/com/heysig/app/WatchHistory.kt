package com.heysig.app

import android.content.Context
import com.heysig.companion.CompanionSafety
import org.json.JSONObject

/** Bounded local reply journal. Stores no command bodies or speech alternatives. */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("heysig-companion-results-v1",Context.MODE_PRIVATE)
    fun prune() {
        val times = prefs.all.mapValues { (_,value) ->
            runCatching { JSONObject(value as String).getLong("time") }.getOrDefault(-1)
        }
        val keep = CompanionSafety.retained(times,System.currentTimeMillis())
        val editor = prefs.edit()
        times.keys.filter { it !in keep }.forEach { editor.remove(it) }
        check(editor.commit()) { "Cannot expire watch history" }
    }
    fun get(node: String, id: String): JSONObject? {
        prune()
        return prefs.getString("$node:$id",null)?.let { JSONObject(it).getJSONObject("reply") }
    }
    fun put(node: String, id: String, reply: JSONObject): Boolean {
        return runCatching {
            prune()
            val key = "$node:$id"
            val times = prefs.all.filterKeys { it != key }.mapValues { (_,v) -> JSONObject(v as String).getLong("time") }
            val keep = CompanionSafety.retained(times,System.currentTimeMillis(),63)
            val editor = prefs.edit()
            times.keys.filter { it !in keep }.forEach { editor.remove(it) }
            editor.putString(key,JSONObject().put("time",System.currentTimeMillis()).put("reply",reply).toString()).commit()
        }.getOrDefault(false)
    }
    fun clear() = prefs.edit().clear().commit()
    fun export(): JSONObject { prune(); return JSONObject(prefs.all) }
}
