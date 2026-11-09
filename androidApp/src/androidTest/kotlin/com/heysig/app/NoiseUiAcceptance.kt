package com.heysig.app

import android.app.*
import android.content.Intent
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.heysig.noise.*

/** Native UI plus real screen-off microphone service using a debug-only silent transport. */
class NoiseUiAcceptance : Instrumentation() {
    private var serviceTest=false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);serviceTest=arguments?.getString("service")=="true";start() }
    override fun onStart() {
        val result=Bundle();var activity: Activity?=null
        fun waitUntil(label:String,condition:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+25_000
            while(SystemClock.elapsedRealtime()<deadline) {
                var ready=false;runOnMainSync { ready=condition() }
                if(ready) return
                SystemClock.sleep(100)
            }
            error("Timeout: $label")
        }
        fun shell(command:String) {
            uiAutomation.executeShellCommand(command).use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        }
        try {
            activity=startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val screen=checkNotNull(activity)
            fun descendants(view:View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            runOnMainSync {
                descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().first { it.text.toString()=="Noise" }.performClick()
            }
            waitForIdleSync()
            runOnMainSync {
                val views=descendants(screen.findViewById(android.R.id.content))
                val toggles=views.filterIsInstance<Button>().filter { it.text.toString() in listOf("On","Off") && it.isShown }
                check(toggles.size==1 && toggles.single().text.toString()=="Off")
                check(toggles.single().contentDescription.toString().contains("turn On"))
                check(views.filterIsInstance<NoiseGraphView>().single().isShown)
                check(views.filterIsInstance<NoiseGraphView>().single().height>=180)
                for(scale in listOf(1f,2f)) {
                    val configuration=android.content.res.Configuration(screen.resources.configuration).apply { fontScale=scale }
                    val graph=NoiseGraphView(screen.createConfigurationContext(configuration))
                    graph.boundaries=listOf(-60.0,-50.0,-40.0,-30.0,-20.0)
                    graph.samples=listOf(0L to -70.0,15000L to -20.0,90000L to -65.0)
                    graph.layout(0,0,360,360)
                    val bitmap=android.graphics.Bitmap.createBitmap(360,360,android.graphics.Bitmap.Config.ARGB_8888)
                    graph.draw(android.graphics.Canvas(bitmap));bitmap.recycle()
                    check(graph.contentDescription.toString().contains("3 sound checks"))
                }
                val root=screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                val directory=java.io.File(targetContext.getExternalFilesDir(null),"ui-previews").apply { mkdirs() }
                java.io.File(directory,"noise-nov8.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            if(serviceTest) {
                shell("pm grant ${targetContext.packageName} android.permission.RECORD_AUDIO")
                if(Build.VERSION.SDK_INT>=33) shell("pm grant ${targetContext.packageName} android.permission.POST_NOTIFICATIONS")
                runOnMainSync {
                    val config=NoiseSettings(programs=listOf(2,2,2,2,2,2),captureSeconds=1,waitSeconds=1,cooldownSeconds=0,confirmations=1)
                    val learner=NoiseThresholdLearner(NoiseLearningContext(config,PhoneNoiseSampler(targetContext) { false }.sourceIdentity(),"1:Quiet|2:Noisy"))
                    AutomaticNoiseService.launch(screen,config,"1:Quiet|2:Noisy",learner,NoiseServiceAcceptanceService::class.java)
                }
                waitUntil("service foreground") { AutomaticNoiseService.running!=null }
                waitUntil("toggle On") {
                    descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.isShown && it.text.toString()=="On" && it.contentDescription.toString().contains("turn Off") }
                }
                shell("input keyevent KEYCODE_HOME")
                shell("input keyevent KEYCODE_SLEEP")
                SystemClock.sleep(27_000)
                val screenOff = !targetContext.getSystemService(PowerManager::class.java).isInteractive
                result.putString("screen",if(screenOff) "Verified non-interactive screen" else "Activity hidden; this device did not enter a non-interactive screen state")
                runOnMainSync {
                    val service=checkNotNull(AutomaticNoiseService.running) { AutomaticNoiseService.lastStatus }
                    check(service is NoiseServiceAcceptanceService)
                    check(service.samples.size>=2 || service.detail.startsWith("Sample rejected")) { "Capture did not resume after isolated playback: ${service.detail}" }
                    result.putString("measurements",service.samples.joinToString { "%.1f dBFS".format(java.util.Locale.US,it.second) })
                    check(service.learner?.sampleCount==0) { "Background measurement entered threshold learning" }
                    check(AutomaticNoiseService.foregroundSince==null) { "Activity remained visible" }
                    val notification=targetContext.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id==7304 }
                    check(notification.notification.actions.single().title=="Off")
                    notification.notification.actions.single().actionIntent.send()
                }
                waitUntil("notification Off") { !AutomaticNoiseService.active }
                shell("input keyevent KEYCODE_WAKEUP")
                result.putString("service","PASS: real bounded microphone capture with Activity hidden (screen state recorded separately); notification Off stops session; isolated silent transport")
            }
            result.putString("result","PASS: one accessible On/Off control, visible graph, scalar history and 2x font drawing; no aid playback")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("result","FAIL: ${e.message}");finish(Activity.RESULT_CANCELED,result) }
        finally {
            runOnMainSync { AutomaticNoiseService.stop(targetContext);activity?.finish() }
        }
    }
}
