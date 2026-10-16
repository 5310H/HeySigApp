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
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
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
            fun open(label:String) {
                runOnMainSync {
                    descendants(screen.findViewById(android.R.id.content)).filterIsInstance<Button>()
                        .first { it.text.toString().equals(label,true) }.performClick()
                }
                waitForIdleSync()
            }
            open("Noise")
            open("Voice")
            runOnMainSync { result.putBoolean("windowFocusBeforeBack",screen.hasWindowFocus()) }
            check(uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            var home = false
            val deadline = android.os.SystemClock.elapsedRealtime()+5000
            while(!home && android.os.SystemClock.elapsedRealtime()<deadline) {
                waitForIdleSync()
                runOnMainSync {
                    home = descendants(screen.findViewById(android.R.id.content)).filterIsInstance<android.widget.TextView>()
                        .any { it.text.toString() == "HeySig • Home" }
                }
                if(!home) Thread.sleep(50)
            }
            check(home) { "Back did not return to Home" }
            result.putString("navigation","Home, Noise and Voice opened; system Back returned Home; no capture or playback")
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
