package com.heysig.app

import android.content.Context
import com.heysig.audio.Yamnet
import com.heysig.noise.*
import org.json.JSONObject

/** Bundled recorded fixture and isolated preferences only; no microphone or aid transport. */
object CombinedAutomaticAcceptance {
    fun verify(app:Context,fixtures:Context):String {
        val manifest=JSONObject(fixtures.assets.open("masking-fixtures/manifest.json").bufferedReader().use { it.readText() })
        val entry=manifest.getJSONArray("fixtures").getJSONObject(0)
        val raw=fixtures.assets.open("masking-fixtures/"+entry.getString("file")).use { it.readBytes() }
        val pcm=ShortArray(160000) { i -> val n=i%(raw.size/2); ((raw[n*2].toInt() and 255) or (raw[n*2+1].toInt() shl 8)).toShort() }
        val prefs=app.getSharedPreferences("acceptance-combined-memory",0)
        check(prefs.edit().clear().commit())
        try {
            val analyzer=CombinedSoundAnalyzer(app)
            var result:MaskingEvidence?=null
            Yamnet(app,observeEvidence={ sound,pool,frames ->
                check(frames.size==19)
                result=analyzer.classify(sound,pool,frames,NoiseSettings())
            }).use { it.classify(pcm) }
            val evidence=checkNotNull(result)
            check(checkNotNull(evidence.scene).matches(evidence.severity))
            check(analyzer.lastSections.size==3)
            check(evidence.soundContext!=null && pcm.indices.all { i -> val n=i%(raw.size/2);pcm[i]==((raw[n*2].toInt() and 255) or (raw[n*2+1].toInt() shl 8)).toShort() })
            check(runCatching { analyzer.classify(pcm,null,emptyList(),NoiseSettings()) { true } }.isFailure)
            val scope=AutomaticMemoryStore.scope("phone","isolated-fitting")
            val row=AutomaticExperience(scope,checkNotNull(evidence.soundContext),AutomaticScene.TV,1L)
            val store=AutomaticMemoryStore(app,prefs)
            check(!store.enabled() && store.book().experiences.isEmpty())
            store.saveReviewed(AutomaticMemory().remember(row))
            val restored=AutomaticMemoryStore(app,prefs)
            check(restored.enabled() && restored.recall(scope,row.context)==AutomaticScene.TV)
            check(restored.recall(AutomaticMemoryStore.scope("watch","isolated-fitting"),row.context)==null)
            check(prefs.edit().putBoolean("enabled",false).commit())
            check(restored.recall(scope,row.context)==null && restored.book().experiences==listOf(row))
            check(prefs.edit().putString("experiences","corrupt").commit())
            check(restored.book().experiences.isEmpty() && prefs.getString("experiences",null)=="corrupt")
            return "PASS: pinned TV head and combined ten-second/three-section native fixture, valid scene/context, cancellation and unchanged input; isolated reviewed-memory restart, disable, source isolation and corrupt-byte preservation. No microphone or commands; consistency, not field accuracy."
        } finally { pcm.fill(0);raw.fill(0);check(prefs.edit().clear().commit()) }
    }
}
