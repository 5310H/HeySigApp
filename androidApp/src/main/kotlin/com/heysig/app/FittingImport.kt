package com.heysig.app

import com.heysig.control.*
import org.json.JSONObject

/** Only the fitting is read. Exported commands, snapshots, permissions and secrets never replay. */
object FittingImport {
    fun decode(text: String): Fitting {
        require(text.length <= 65_536) { "Setup import is limited to 64 KiB" }
        val root=JSONObject(text)
        require(root.getString("format") == "heysig-local-export-v1") { "Choose a HeySig local export" }
        val stored=root.getJSONObject("settings").get("fitting")
        val json=when(stored) { is String -> JSONObject(stored); is JSONObject -> stored; else -> error("Missing fitting") }
        fun integer(key: String): Int {
            val value=json.get(key)
            require(value is Number && value.toDouble() == value.toInt().toDouble()) { "Invalid $key" }
            return value.toInt()
        }
        val programs=(1..6).mapNotNull { slot ->
            val name=if(json.has("name$slot")) json.getString("name$slot").trim() else ""
            if(name.isEmpty()) null else {
                require(name.length <= 60 && name.none { it.isISOControl() }) { "Invalid program name" }
                Program(slot,name,TinnitusCapability.valueOf(json.getString("tin$slot")))
            }
        }
        return Fitting(integer("control"),integer("left"),integer("right"),programs)
    }
}
