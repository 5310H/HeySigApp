package com.heysig.app

import android.content.Context
import com.heysig.control.*
import com.heysig.noise.NoiseVoice
import com.heysig.voice.HandsFreeControl
import org.json.JSONArray
import org.json.JSONObject

/** Phone-owned local store, deliberately excluded from the general settings export. */
class PersonalPhraseStore internal constructor(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context): this(context.applicationContext.getSharedPreferences("heysig-personal-phrases-v1",Context.MODE_PRIVATE))
    private var valid = true
    private val phrases = PersonalPhrases(load()) { records ->
        prefs.edit().putString("records",JSONArray().apply {
            records.forEach { r -> put(JSONObject().put("id",r.id).put("phrase",r.phrase).put("target",r.target)
                .put("fitting",r.fittingKey).put("source",r.source).put("confirmedAt",r.confirmedAt).put("schema",r.schemaVersion)) }
        }.toString()).commit()
    }
    private fun load(): List<PersonalPhrase> = try {
        val array = JSONArray(prefs.getString("records","[]"))
        require(array.length() <= 200)
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            PersonalPhrase(it.getString("id"),it.getString("phrase"),it.getString("target"),it.getString("fitting"),
                it.getString("source"),it.getLong("confirmedAt"),it.getInt("schema"))
        } }
    } catch(e: Exception) { valid = false; emptyList() }
    // Each detected setup change advances a durable generation, so changing back cannot revive old aliases.
    private fun key(fit: Fitting): String? {
        val signature = fit.toString()
        if(prefs.getString("setup",null) != signature) {
            val next = prefs.getLong("generation",0) + 1
            if(!prefs.edit().putString("setup",signature).putLong("generation",next).commit()) return null
            phrases.cancel()
        }
        return prefs.getLong("generation",0).toString()
    }
    fun identity(text: String, fit: Fitting?): Any {
        val exact = com.heysig.voice.HandsFreeCompanion.identity(text,fit)
        if(exact !is AppIntent.Unsupported) return exact
        if(fit != null) com.heysig.sleep.SleepVoice.parse(text,fit)?.let { return it }
        val normalized = phrases.normalize(text)
        if(Regex("^(when i say |confirm phrase import$|learn phrase |forget phrase |list my (?:phrases|corrections)|remember that correction$|reset learned phrases$|confirm reset learned phrases$|learned phrases (?:on|off)$)").containsMatchIn(normalized))
            return "personal-phrase-management:$normalized"
        return if(fit != null) resolve(text,fit) else exact
    }
    fun resolve(text: String, fit: Fitting): AppIntent {
        val key = key(fit) ?: return VoiceCommands.parse(text,fit)
        return if(valid && prefs.getBoolean("enabled",true)) phrases.resolve(text,fit,key) else VoiceCommands.parse(text,fit)
    }
    fun exportReviewed(fit: Fitting): String {
        check(valid) { "Phrase storage needs review" }
        val key=requireNotNull(key(fit)) { "Cannot check setup" }
        val entries=phrases.records.filter { it.fittingKey == key && it.schemaVersion == 1 }
        return JSONObject().put("format","heysig-phrases-v1").put("entries",JSONArray().apply {
            entries.forEach { put(JSONObject().put("phrase",it.phrase).put("target",it.target)) }
        }).toString(2)
    }
    fun importForReview(text: String, fit: Fitting, origin: String, now: Long): String {
        check(valid) { "Phrase storage needs review" }
        require(text.length <= 65_536) { "Phrase import is limited to 64 KiB" }
        val root=JSONObject(text)
        require(root.getString("format") == "heysig-phrases-v1") { "Choose a HeySig phrase export" }
        val array=root.getJSONArray("entries")
        require(array.length() in 1..200) { "Import one to 200 phrases" }
        val entries=(0 until array.length()).map { i ->
            val item=array.getJSONObject(i)
            item.getString("phrase") to item.getString("target")
        }
        return phrases.stageImport(entries,fit,requireNotNull(key(fit)),origin,now) { reserved(it,fit) }
    }
    private fun reserved(text: String, fit: Fitting): Boolean = DataVoice.parse(text) != null || FittingWorkflow.accepts(text) ||
        HandsFreeControl.parse(text) != null || VoicePreference.parse(text) != null || NoiseVoice.parse(text) != null ||
        text.startsWith("when i say ") || text.startsWith("learned phrases ") ||
        com.heysig.sleep.SleepVoice.parse(text,fit) != null
    fun setupChanged(fit: Fitting) { key(fit) }
    val awaitingConfirmation: Boolean get() = phrases.awaitingConfirmation
    fun isPromptFor(origin: String) = phrases.isPromptFor(origin)
    fun cancel() = phrases.cancel()
    fun enabled() = prefs.getBoolean("enabled",true)
    fun enable(value: Boolean): Boolean { cancel(); return prefs.edit().putBoolean("enabled",value).commit() }
    fun manage(text: String, fit: Fitting, origin: String): String? {
        val input = phrases.normalize(text)
        if(input in listOf("learned phrases on", "learned phrases off"))
            return if(enable(input.endsWith(" on"))) "Learned phrases ${if(enabled()) "on" else "off"}. Nothing sent." else "Could not save phrase choice."
        if(!valid && input !in listOf("reset learned phrases", "confirm reset learned phrases", "cancel", "stop")) {
            return if(input.startsWith("when i say ") || input == "confirm phrase import" || input.startsWith("learn phrase ") || input.startsWith("forget phrase ") || input.startsWith("list my ") || input == "remember that correction")
                "Personal phrase storage needs review. Existing records were preserved. Use reset learned phrases to delete them explicitly."
            else null
        }
        val key = key(fit) ?: return "Cannot check phrase setup. Nothing changed."
        val result = phrases.manage(text,fit,key,origin,System.currentTimeMillis()) {
            reserved(it,fit)
        }
        if(result == "All learned phrases deleted. Nothing sent.") valid = true
        return result
    }
}
