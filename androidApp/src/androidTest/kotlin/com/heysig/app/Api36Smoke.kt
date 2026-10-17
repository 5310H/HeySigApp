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
                waitForIdleSync()
                runOnMainSync {
                    val root = screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                    val bitmap = android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    val directory = java.io.File(targetContext.getExternalFilesDir(null),"ui-previews").apply { mkdirs() }
                    java.io.File(directory,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
            }
            fun open(label:String) {
                runOnMainSync {
                    descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                        .first { it.text.toString().equals(label,true) }.performClick()
                }
                waitForIdleSync()
            }
            capture("home")
            open("Noise")
            capture("noise")
            open("Settings")
            capture("settings")
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
                        .any { it.text.toString() == "Your hearing" }
                }
                if(!home) Thread.sleep(50)
            }
            check(home) { "Back did not return to Home" }
            result.putString("navigation","Home, Noise, Settings, Voice, Privacy and Watch opened; Back returned through Settings to Home; no capture or playback")
            runOnMainSync {
                val minimum = (48*screen.resources.displayMetrics.density).toInt()
                val buttons = descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                check(buttons.all { it.measuredHeight >= minimum }) { "A control is below the 48dp touch target" }
                result.putString("layout","PASS: native controls at least 48dp; font scale ${screen.resources.configuration.fontScale}; width ${screen.resources.configuration.screenWidthDp}dp")
            }
            runOnMainSync {
                for(scale in listOf(1f,2f)) {
                    val configuration = android.content.res.Configuration(screen.resources.configuration).apply {
                        fontScale = scale; screenWidthDp = 192; screenHeightDp = 192
                        screenLayout = (screenLayout and android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or android.content.res.Configuration.SCREENLAYOUT_ROUND_YES
                    }
                    val context = android.view.ContextThemeWrapper(screen.createConfigurationContext(configuration),android.R.style.Theme_Material_NoActionBar)
                    val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
                    com.heysig.watch.WatchStyle.prepareColumn(column)
                    column.addView(android.widget.TextView(context).apply { text = "HeySig" })
                    val speak = Button(context).apply { text = "Speak command"; column.addView(this) }
                    val stop = Button(context).apply { text = "Stop"; column.addView(this) }
                    column.addView(android.widget.CheckBox(context).apply { text = "Spoken responses" })
                    com.heysig.watch.WatchStyle.polish(column)
                    val viewport = android.widget.ScrollView(context).apply { setBackgroundColor(android.graphics.Color.BLACK); addView(column) }
                    val size = (192*context.resources.displayMetrics.density).toInt()
                    val spec = View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY)
                    viewport.measure(spec,spec); viewport.layout(0,0,size,size)
                    val minimum = (48*context.resources.displayMetrics.density).toInt()
                    check(listOf(speak,stop).all { it.measuredHeight >= minimum && it.measuredWidth >= minimum })
                    check(column.paddingLeft == com.heysig.watch.WatchStyle.dp(context,32))
                    check(listOf(speak,stop).all { control -> (0 until control.layout.lineCount).all { control.layout.getEllipsisCount(it) == 0 } })
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
                result.putString("roundWatch","PASS: isolated 192dp round native-style contexts at font scales 1 and 2; 48dp targets and no text ellipses; actual Wear device acceptance pending")
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
