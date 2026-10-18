package com.heysig.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.heysig.control.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real runtime/storage with fake transport; never touches user preferences, microphone or aids. */
object SavedSettingsAcceptance {
    fun run(context: Context) {
        val prefs=context.getSharedPreferences("acceptance-isolated-saved-settings",0)
        val original="""{"program":1,"VOLUME":6}"""
        check(prefs.edit().clear()
            .putString("fitting","""{"control":12,"left":12,"right":12,"name1":"Awake","tin1":"SUPPORTED","name2":"Sleep","tin2":"SUPPORTED"}""")
            .putString("requested","""{"program":1}""").putString("saved",original).commit())
        var stages=0
        var failAt=Int.MAX_VALUE
        val transport=object: CommandTransport {
            override fun inspect(allowLocked: Boolean) = Unit
            override fun play(pcm: ByteArray,rate: Int,cancelled: AtomicBoolean,allowLocked: Boolean) {
                check(pcm.isNotEmpty() && rate==44100)
                stages++
                check(stages != failAt) { "Injected interrupted transport" }
            }
        }
        val main=Handler(Looper.getMainLooper())
        fun play(runtime: ManualRuntime, plan: List<ManualRequest>, restoring: Boolean=false, automatic: Boolean=false): Boolean {
            val latch=CountDownLatch(1);var success=false
            main.post { runtime.execute(plan,restoring,automatic,{true},{false},{}) { _,ok -> success=ok;latch.countDown() } }
            check(latch.await(20,TimeUnit.SECONDS)) { "Isolated snapshot playback did not finish" }
            return success
        }
        fun preserved() { check(prefs.getString("saved",null)==original) { "Snapshot changed without explicit Save" } }
        try {
            var runtime=ManualRuntime.isolated(prefs,transport)
            check(play(runtime,listOf(ManualRequest.SelectProgram(2))));preserved()
            check(play(runtime,listOf(ManualRequest.SetLevel(LevelControl.VOLUME,9))));preserved()
            check(play(runtime,listOf(VolumeSteps.request(1,runtime.requested,requireNotNull(runtime.fitting)))));preserved()
            check(runtime.requested.levels[LevelControl.VOLUME]==10)
            check(play(runtime,listOf(VolumeSteps.request(-1,runtime.requested,requireNotNull(runtime.fitting)))));preserved()
            check(runtime.requested.levels[LevelControl.VOLUME]==9)
            check(play(runtime,listOf(ManualRequest.SetLevel(LevelControl.SHARPNESS,-3))));preserved()
            check(play(runtime,listOf(ManualRequest.SetLevel(LevelControl.TINNITUS,4))));preserved()
            for (control in listOf(LevelControl.SHARPNESS,LevelControl.TINNITUS)) {
                val baseline=requireNotNull(runtime.requested.levels[control])
                for (delta in listOf(1,-1)) {
                    check(play(runtime,listOf(LevelSteps.request(control,delta,runtime.requested,requireNotNull(runtime.fitting)))));preserved()
                    check(runtime.requested.levels[control]==baseline+if(delta==1) 1 else 0)
                }
            }
            val programTwo=runtime.requested
            check(play(runtime,listOf(ManualRequest.SelectProgram(1))));preserved()
            runtime=ManualRuntime.isolated(prefs,transport)
            check(play(runtime,listOf(ManualRequest.SelectProgram(2))));preserved()
            check(runtime.requested==programTwo) { "Program levels were not restored after restart" }
            check(play(runtime,listOf(ManualRequest.Battery(true,true))));preserved()
            check(play(runtime,listOf(ManualRequest.SelectProgram(1)),automatic=true));preserved()
            check(play(runtime,runtime.restorePlan(),restoring=true));preserved()
            check(runtime.requested==RequestedState(1,mapOf(LevelControl.VOLUME to 6)))
            failAt=stages+2
            check(!play(runtime,runtime.restorePlan(),restoring=true));preserved()
            check(runtime.saved==RequestedState(1,mapOf(LevelControl.VOLUME to 6)))
            failAt=Int.MAX_VALUE
            runtime=ManualRuntime.isolated(prefs,transport)
            check(runtime.saved?.levels?.get(LevelControl.VOLUME)==6);preserved()
            val current=RequestedState(1,mapOf(LevelControl.VOLUME to 7,LevelControl.SHARPNESS to -2,LevelControl.TINNITUS to 3))
            val refreshPlan=runtime.restorePlan(current)
            check(refreshPlan==listOf(ManualRequest.SelectProgram(1),
                ManualRequest.SetLevel(LevelControl.VOLUME,7),ManualRequest.SetLevel(LevelControl.SHARPNESS,-2),ManualRequest.SetLevel(LevelControl.TINNITUS,3)))
            check(play(runtime,refreshPlan,restoring=true));preserved()
            check(runtime.requested==current) { "Refresh used saved snapshot instead of current settings" }
            check(play(runtime,runtime.restorePlan(RequestedState(1)),restoring=true));preserved()
            check(runtime.requested==RequestedState(1)) { "Refresh filled unknown levels from remembered program values" }
            runtime.requested=RequestedState(2,mapOf(LevelControl.VOLUME to 8))
            runtime.busy.set(true)
            check(runtime.saveSnapshot().startsWith("Busy"));preserved()
            runtime.busy.set(false)
            runtime.requested=RequestedState()
            check(!runtime.saveSnapshot().startsWith("Requested settings saved"));preserved()
            runtime.requested=RequestedState(6)
            check(!runtime.saveSnapshot().startsWith("Requested settings saved"));preserved()
            val failing=object: SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor=prefs.edit()
                    return object: SharedPreferences.Editor by editor {
                        override fun putString(key: String,value: String?): SharedPreferences.Editor { editor.putString(key,value);return this }
                        override fun commit()=false
                    }
                }
            }
            val failure=ManualRuntime.isolated(failing,transport)
            failure.requested=RequestedState(2)
            check(failure.saveSnapshot().contains("previous snapshot retained"));preserved()
            check(failure.saved?.program==1)
            runtime.requested=RequestedState(2,mapOf(LevelControl.VOLUME to 8))
            check(runtime.saveSnapshot().startsWith("Requested settings saved"))
            val explicit=prefs.getString("saved",null)
            check(explicit != original)
            runtime=ManualRuntime.isolated(prefs,transport)
            check(runtime.saved==RequestedState(2,mapOf(LevelControl.VOLUME to 8)))
            check(prefs.edit().putBoolean("pending",true).commit())
            runtime=ManualRuntime.isolated(prefs,transport)
            check(runtime.requested==RequestedState() && runtime.saved?.program==2)
            check(prefs.getString("saved",null)==explicit)
            check(prefs.edit().putBoolean("pending",false).putString("saved","broken-snapshot").commit())
            runtime=ManualRuntime.isolated(prefs,transport)
            check(runtime.fitting != null && runtime.saved==null && runtime.savedIssue!=null)
            check(play(runtime,listOf(ManualRequest.SelectProgram(1))))
            check(prefs.getString("saved",null)=="broken-snapshot")
            check(runtime.saveSnapshot().startsWith("Requested settings saved"))
            check(runtime.savedIssue==null && runtime.saved?.program==1)
        } finally { check(prefs.edit().clear().commit()) }
    }
}

/** Runs only isolated storage/transport checks, leaving the live fitting and aids untouched. */
class ProgramSettingsSmoke : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=android.os.Bundle()
        try {
            FittingWorkflowAcceptance.run(targetContext)
            SavedSettingsAcceptance.run(targetContext)
            val activity=startActivitySync(android.content.Intent(targetContext,HomeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            fun buttons(): List<android.widget.Button> {
                fun views(view: android.view.View): List<android.view.View> = listOf(view) +
                    if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
                return views(activity.findViewById(android.R.id.content)).filterIsInstance<android.widget.Button>()
            }
            runOnMainSync {
                check(buttons().none { it.text.toString()=="Batteries" })
                if(ManualRuntime.get(targetContext).fitting!=null) check(buttons().any { it.text.toString()=="Refresh" })
                buttons().first { it.text.toString()=="Settings" }.performClick()
            }
            waitForIdleSync()
            runOnMainSync {
                check(buttons().any { it.text.toString()=="Battery check" })
                buttons().first { it.text.toString()=="Saved settings" }.performClick()
            }
            waitForIdleSync()
            runOnMainSync {
                check(buttons().none { it.text.toString().contains("Restore",ignoreCase=true) })
                activity.finish()
            }
            result.putString("result","PASS: program persistence, current refresh plan, snapshot protection and Home/Settings actions")
            finish(android.app.Activity.RESULT_OK,result)
        } catch(e: Throwable) {
            result.putString("result","FAIL: ${e.message}")
            finish(android.app.Activity.RESULT_CANCELED,result)
        }
    }
}
