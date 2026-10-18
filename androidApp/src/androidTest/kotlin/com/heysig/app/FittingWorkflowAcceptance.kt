package com.heysig.app

import android.content.Context
import com.heysig.control.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object FittingWorkflowAcceptance {
    fun run(context: Context) {
        val prefs=context.getSharedPreferences("acceptance-isolated-fitting-workflow",0)
        val phrasePrefs=context.getSharedPreferences("acceptance-isolated-phrase-import",0)
        val original="""{"control":12,"left":12,"right":12,"name1":"Universal","tin1":"SUPPORTED"}"""
        val snapshot="""{"program":1,"VOLUME":8}"""
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) { error("Setup must not inspect playback") }
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) { error("Setup must never play") }
        }
        check(prefs.edit().clear().putString("fitting",original).putString("saved",snapshot).putString("requested",snapshot).commit())
        check(phrasePrefs.edit().clear().commit())
        try {
            val runtime=ManualRuntime.isolated(prefs,transport)
            val oldFit=requireNotNull(runtime.fitting)
            val flow=runtime.fittingWorkflow
            fun say(text: String)=flow.handle(text,runtime.fitting,"phone",10).orEmpty()
            say("configure hearing aids");say("name program one as everyday")
            check(prefs.getString("fitting",null)==original && prefs.getString("saved",null)==snapshot)
            check(say("save aid setup").contains("review"))
            check(say("review aid setup").contains("everyday"))
            check(say("save aid setup").contains("saved"))
            check(runtime.requested==RequestedState() && runtime.saved==null)
            val archives=prefs.all.filterKeys { it.startsWith("setup-archive-") }
            check(archives.size==1)
            val archive=JSONObject(archives.values.single() as String)
            check(archive.getString("fitting")==original && archive.getString("saved")==snapshot)
            check(runtime.saveFitting(requireNotNull(runtime.fitting)))
            check(prefs.all.keys.count { it.startsWith("setup-archive-") }==1)
            val exported=JSONObject().put("format","heysig-local-export-v1").put("settings",JSONObject().put("fitting",original)).toString()
            check(FittingImport.decode(exported)==oldFit)
            for(bad in listOf("{}",exported.replace("SUPPORTED","madeup"),exported.replace("12","256")," ".repeat(65_537)))
                check(runCatching { FittingImport.decode(bad) }.isFailure)
            val phrases=PersonalPhraseStore(phrasePrefs)
            phrases.manage("when I say movie time I mean program Universal",oldFit,"phone")
            phrases.manage("remember that correction",oldFit,"phone")
            val phraseExport=phrases.exportReviewed(oldFit)
            check(phraseExport.contains("movie time"))
            check(phrases.manage("reset learned phrases",oldFit,"phone")!!.contains("Delete"))
            phrases.manage("confirm reset learned phrases",oldFit,"phone")
            check(phrases.importForReview(phraseExport,oldFit,"phone",System.currentTimeMillis()).contains("movie time means"))
            check(phrases.resolve("movie time",oldFit) is AppIntent.Unsupported)
            check(phrases.manage("confirm phrase import",oldFit,"phone")!!.contains("Imported"))
            check(phrases.resolve("movie time",oldFit) is AppIntent.Control)
        } finally { prefs.edit().clear().commit();phrasePrefs.edit().clear().commit() }
    }
}
