package com.heysig.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.view.View
import android.view.ViewGroup

/** No microphone or aid playback. Run on the explicitly selected phone/Chromebook. */
class Api36Smoke : Instrumentation() {
    private var captureScreens = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments); captureScreens = arguments?.getString("screenshots") == "true"; start()
    }
    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        try {
            check(targetContext.applicationInfo.targetSdkVersion == 36)
            activity = startActivitySync(Intent(targetContext,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val screen = checkNotNull(activity)
            fun descendants(view:View): List<View> = listOf(view) + if(view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            fun shellInput(command: String) {
                uiAutomation.executeShellCommand(command).use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
                waitForIdleSync()
            }
            val display = if(Build.VERSION.SDK_INT >= 30) screen.display?.displayId ?: 0 else 0
            result.putInt("displayId",display)
            fun focusWindow() {
                shellInput("am start --display $display --activity-reorder-to-front --activity-single-top -n ${screen.componentName.flattenToShortString()}")
                val bounds = android.graphics.Rect()
                var safeY = 0
                runOnMainSync {
                    val root = screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                    check(root.getGlobalVisibleRect(bounds))
                    safeY = (bounds.top+root.paddingTop+12*screen.resources.displayMetrics.density).toInt()
                }
                // ARC apps can live on secondary displays. Target that display's heading,
                // never an aid control, rather than injecting into the blank default display.
                shellInput("input -d $display tap ${bounds.centerX()} $safeY")
            }
            fun systemBack() { shellInput("input -d $display keyevent KEYCODE_BACK") }
            focusWindow()
            fun capture(name: String) {
                if(!captureScreens) return
                val deadline=android.os.SystemClock.uptimeMillis()+5000
                while(android.os.SystemClock.uptimeMillis()<deadline) {
                    waitForIdleSync()
                    var captured=false
                    var failure: Throwable?=null
                    runOnMainSync {
                        try {
                            val root=screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                            if(root.width>0 && root.height>0) {
                                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                                try {
                                    root.draw(android.graphics.Canvas(bitmap))
                                    val directory=java.io.File(targetContext.getExternalFilesDir(null),"ui-previews").apply { mkdirs() }
                                    java.io.File(directory,"$name.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
                                    captured=true
                                } finally { bitmap.recycle() }
                            }
                        } catch(error: Throwable) { failure=error }
                    }
                    failure?.let { throw it }
                    if(captured) return
                    android.os.SystemClock.sleep(20)
                }
                error("View did not finish layout for screenshot: $name")
            }

            fun open(label:String) {
                runOnMainSync {
                    descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                        .first { it.text.toString().equals(label,true) }.performClick()
                }
                waitForIdleSync()
            }
            capture("home")
            runOnMainSync {
                val buttons=descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().filter { it.isShown }
                buttons.forEach { button ->
                    val visible=android.graphics.Rect()
                    check(button.getGlobalVisibleRect(visible) && visible.height() >= button.height-1) { "Home action requires scrolling: ${button.text}" }
                }
            }
            open("Volume")
            runOnMainSync {
                descendants(screen.findViewById(android.R.id.content)).filterIsInstance<android.widget.SeekBar>().first().let { it.progress=(it.progress+1).coerceAtMost(it.max) }
            }
            capture("volume")
            open("‹ Back")
            open("Noise")
            capture("noise")
            open("2. Learn sound levels")
            var calibrationReady=false
            var calibrationControls=""
            val calibrationDeadline=android.os.SystemClock.elapsedRealtime()+5000
            while(!calibrationReady && android.os.SystemClock.elapsedRealtime()<calibrationDeadline) {
                waitForIdleSync()
                runOnMainSync {
                    val controls=descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                    calibrationReady=controls.any { it.text.toString()=="Start new calibration" && it.isShown }
                    calibrationControls=controls.joinToString { "${it.text}: shown=${it.isShown}, visibility=${it.visibility}" }
                }
                if(!calibrationReady) Thread.sleep(20)
            }
            check(calibrationReady) { "Calibration screen did not settle: $calibrationControls" }
            capture("calibration")
            open("‹ Back")
            open("1. Choose programs")
            runOnMainSync {
                check(descendants(screen.findViewById(android.R.id.content)).filterIsInstance<android.widget.Spinner>()
                    .any { it.contentDescription == "Protected Sleep program" })
            }
            open("Advanced measurement settings")
            open("‹ Back")
            open("‹ Back")
            open("3. Preview suggested changes")
            runOnMainSync {
                check(descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                    .first { it.text == "Start Automatic" }.isEnabled == false)
                check(!com.heysig.noise.NoiseReleaseGate.PHYSICAL_ACCEPTANCE_PASSED)
                result.putString("noiseControls","PASS: focused overview, calibration, assignments, measurements and preview routes; Automatic locked; no capture/playback")
            }
            open("‹ Back")
            open("Settings")
            capture("settings")
            open("Getting started")
            capture("getting-started")
            open("‹ Back")
            open("Sleep routine")
            capture("sleep-dashboard")
            runOnMainSync {
                descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                    .firstOrNull { it.text.toString()=="Edit bedtime & morning routine" }?.performClick()
            }
            waitForIdleSync()
            runOnMainSync {
                val controls=descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                check(controls.any { it.text.toString()=="Review & save routine" })
                check(controls.any { it.text.toString()=="Enable sleep reminders" })
                check(controls.any { it.text.toString()=="Sleep now" })
                check(controls.any { it.text.toString()=="Wake now" })
            }
            capture("sleep")
            open("Settings")
            open("Voice & speech")
            capture("voice")
            open("Settings")
            open("Privacy and data")
            runOnMainSync {
                check(descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                    .any { it.text.toString() == "Delete local app data" })
            }
            open("Settings")
            open("Watch connection")
            focusWindow()
            runOnMainSync { result.putBoolean("windowFocusBeforeBack",screen.hasWindowFocus()) }
            systemBack()
            waitForIdleSync()
            val settingsDeadline = android.os.SystemClock.elapsedRealtime()+5000
            var settings = false
            while(!settings && android.os.SystemClock.elapsedRealtime()<settingsDeadline) {
                runOnMainSync { settings = descendants(screen.findViewById(android.R.id.content)).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Settings" && it !is Button } }
                if(!settings) Thread.sleep(50)
            }
            check(settings) { "Back did not return to Settings" }
            systemBack()
            var home = false
            val deadline = android.os.SystemClock.elapsedRealtime()+5000
            while(!home && android.os.SystemClock.elapsedRealtime()<deadline) {
                waitForIdleSync()
                runOnMainSync {
                    home = descendants(screen.findViewById(android.R.id.content)).filterIsInstance<android.widget.TextView>()
                        .any { it.text.toString() == "HeySig" }
                }
                if(!home) Thread.sleep(50)
            }
            check(home) { "Back did not return to Home" }
            result.putString("navigation","Home, Noise, Settings, Voice, Privacy and Watch opened; Back returned through Settings to Home; no capture or playback")
            runOnMainSync {
                val minimum = (48*screen.resources.displayMetrics.density).toInt()
                val buttons = descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>().filter { it.isShown }
                check(buttons.all { it.measuredHeight >= minimum }) { "A control is below the 48dp touch target" }
                result.putString("layout","PASS: native controls at least 48dp; font scale ${screen.resources.configuration.fontScale}; width ${screen.resources.configuration.screenWidthDp}dp")
            }
            runOnMainSync {
                for(scale in listOf(1f,2f)) {
                    val configuration = android.content.res.Configuration(screen.resources.configuration).apply {
                        fontScale = scale; screenWidthDp = 192; screenHeightDp = 192
                        screenLayout = (screenLayout and android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or android.content.res.Configuration.SCREENLAYOUT_ROUND_YES
                    }
                    val context = android.view.ContextThemeWrapper(this@Api36Smoke.context.createConfigurationContext(configuration),android.R.style.Theme_Material_NoActionBar)
                    fun label(name: String) = context.resources.getIdentifier(name,"string",context.packageName).also { check(it != 0) }
                    val labels = com.heysig.watch.WatchStyle.Labels(label("speak"),label("stop"),label("more"),label("speech_off"),label("connect"),label("speak_command"))
                    val home = com.heysig.watch.WatchStyle.home(context,labels,"Ready",true,{},{},{})
                    val viewport = home.view
                    val size = (192*context.resources.displayMetrics.density).toInt()
                    val spec = View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY)
                    viewport.measure(spec,spec); viewport.layout(0,0,size,size)
                    val minimum = (48*context.resources.displayMetrics.density).toInt()
                    val actions = descendants(viewport).filterIsInstance<Button>()
                    check(actions.map { it.text.toString() }.toSet() == setOf("Speak","Stop","More"))
                    check(actions.all { it.measuredHeight >= minimum && it.measuredWidth >= minimum })
                    check(actions.all { control -> (0 until control.layout.lineCount).all { control.layout.getEllipsisCount(it) == 0 } })
                    check(actions.all { action ->
                        val bounds=android.graphics.Rect(0,0,action.width,action.height)
                        viewport.offsetDescendantRectToMyCoords(action,bounds)
                        bounds.top >= 0 && bounds.bottom <= size
                    }) { "Watch home action needs scrolling at font scale $scale" }
                    if(captureScreens) {
                        val bitmap = android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.clipPath(android.graphics.Path().apply { addCircle(size/2f,size/2f,size/2f,android.graphics.Path.Direction.CW) })
                        viewport.draw(canvas)
                        val directory = java.io.File(targetContext.getExternalFilesDir(null),"ui-previews").apply { mkdirs() }
                        java.io.File(directory,"round-watch-${scale}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                        bitmap.recycle()
                    }
                }
                result.putString("roundWatch","PASS: real compact watch home in isolated 192dp round contexts at font scales 1 and 2; Speak/Stop/More, 48dp targets and no ellipses; hardware acceptance pending")
            }
            SavedSettingsAcceptance.run(targetContext)
            result.putString("savedSettings","PASS: explicit Save only; manual/battery/automatic/partial restore and restart preserve snapshot bytes; busy/invalid/failed save retain old record; corrupt snapshot preserves fitting and bytes")
            SleepRoutineAcceptance.run(targetContext)
            result.putString("sleepRoutine","PASS: isolated save/validation, restart persistence, Off gate, draft source/expiry/cancel, revision conflicts, fitting invalidation and corrupt-data preservation; no alarm or playback")
            SleepManagementAcceptance.run(targetContext)
            result.putString("sleepManagement","PASS: isolated saved targets, shared executor, sleep/wake completion, noise priority, manual override, partial failure, pending restart and fitting invalidation; fake transport only")
            PersonalPhraseAcceptance.run(targetContext)
            result.putString("personalPhrases","PASS: isolated explicit consent, restart persistence, disable/forget/reset, source confirmation, setup invalidation and corruption recovery; no microphone or playback")
            HandsFreeAcceptance.run(targetContext)
            result.putString("handsFree","PASS: isolated shared executor serializes locked-session playback, journals before transport, rejects inactive sessions/storage failure and never replays interrupted state; native keyword model decodes silence without capture")
            // Version-1 migration and release lock use isolated preferences and a playback spy.
            runOnMainSync {
                val store = targetContext.getSharedPreferences("acceptance-isolated-noise",0)
                check(store.edit().clear().commit())
                try {
                    val old = org.json.JSONObject().put("version",1).put("fitting","1:Awake|2:Sleep")
                        .put("programs",org.json.JSONArray(listOf(1,2,org.json.JSONObject.NULL,1,2,1)))
                        .put("boundaries",org.json.JSONArray(listOf(-60.0,-50.0,-40.0,-30.0,-20.0)))
                        .put("margin",2.0).put("confirmations",3).put("cooldown",60)
                        .put("capture",5).put("wait",10).put("freshness",30)
                    check(store.edit().putString("settings",old.toString()).putString("calibration","corrupt").commit())
                    var playback = 0
                    var reply = ""
                    val feature = NoiseFeature(screen,{
                        listOf(com.heysig.control.Program(1,"Awake",com.heysig.control.TinnitusCapability.UNKNOWN),
                            com.heysig.control.Program(2,"Sleep",com.heysig.control.TinnitusCapability.UNKNOWN))
                    },{ false },{ text,_ -> reply=text },{ com.heysig.noise.NoiseSafety() },{ 1 },
                        { _,_ -> playback++ },store)
                    feature.handle(com.heysig.noise.NoiseCommand.Automatic)
                    check(reply.contains("locked pending physical acceptance"))
                    check(!store.contains("microphoneDisclosureV2") && playback == 0)
                    feature.handle(com.heysig.noise.NoiseCommand.Save)
                    check(reply.startsWith("Noise settings saved"))
                    val migrated = org.json.JSONObject(checkNotNull(store.getString("settings",null)))
                    check(migrated.getJSONArray("programs").toString() == old.getJSONArray("programs").toString())
                    check(migrated.getJSONArray("boundaries").toString() == old.getJSONArray("boundaries").toString())
                    check(migrated.isNull("sleepProgram") && !store.contains("calibration"))
                    feature.stop()
                    result.putString("noiseStorage","PASS: isolated v1 migration preserves mappings/thresholds; corrupt calibration fails closed; Automatic lock sends no playback or permission request")
                } finally { check(store.edit().clear().commit()) }
            }
            // Exercise history deletion/expiry in isolated preferences, never the user's fitting.
            val isolated = object : android.content.ContextWrapper(targetContext) {
                override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                    targetContext.getSharedPreferences("acceptance-isolated-$name",mode)
            }
            val history = WatchHistory(isolated)
            check(history.clear())
            val retainedFitting = isolated.getSharedPreferences("fitting-sentinel",0)
            check(retainedFitting.edit().putString("fitting","preserve").commit())
            repeat(66) { check(history.put("test-node","id-$it",org.json.JSONObject().put("text","test result"))) }
            check(history.export().length() == 64)
            val store = isolated.getSharedPreferences("heysig-companion-results-v1",0)
            check(store.edit().putString("expired",org.json.JSONObject().put("time",0).put("reply",org.json.JSONObject()).toString())
                .putString("corrupt","not-json").commit())
            history.prune()
            check(!store.contains("expired") && !store.contains("corrupt"))
            check(history.clear() && history.export().length() == 0)
            check(retainedFitting.getString("fitting",null) == "preserve")
            retainedFitting.edit().clear().commit()
            result.putString("privacy","PASS: isolated journal cap/expiry/corruption/deletion; unrelated fitting retained; real fitting untouched")
            runOnMainSync {
                if(Build.VERSION.SDK_INT >= 30) {
                    val root = (screen.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
                    val insets = root.rootWindowInsets ?: error("Window insets not delivered")
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                    check(root.paddingTop == bars.top && root.paddingBottom == bars.bottom)
                }
            }
            result.putString("result","PASS: target 36; navigation and current-runtime insets; Android SDK ${Build.VERSION.SDK_INT}")
            if(Build.VERSION.SDK_INT < 36) result.putString("limitation","Android 16 runtime coverage still pending")
            finish(Activity.RESULT_OK,result)
        } catch(e:Exception) {
            result.putString("result","FAIL: ${e.message}"); finish(Activity.RESULT_CANCELED,result)
        }
    }
}
