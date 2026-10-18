package com.heysig.watch

import android.Manifest
import android.app.*
import android.content.pm.PackageManager
import android.os.Build

/** Notification permission is requested only after the feature explanation. */
object SleepingShakeOptions {
    fun show(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Sleeping-watch shake")
            .setMessage("While this session is on, motion is processed in memory, including with the screen off. Three shakes vibrate and offer Tap to speak for 30 seconds. No microphone starts until you tap. Speech uses the installed recognition provider, which may send audio over the network. Movement can trigger it accidentally and battery use may increase. Ends after eight hours, Stop, closing HeySig from Recents, or process death. Does not restart after reboot.")
            .setPositiveButton(if(SleepingShakeService.running) "Stop" else "Start") { _,_ ->
                if(SleepingShakeService.running) SleepingShakeService.stop(activity)
                else if(!SleepingShakeService.available(activity)) message(activity,"This watch has no wake-up accelerometer. Foreground shake remains available.")
                else if(Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),731)
                    message(activity,"After allowing notifications, open Sleeping-watch shake and choose Start again.")
                } else if(!activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) message(activity,"Enable HeySig notifications before starting this session.")
                else try { SleepingShakeService.start(activity) }
                catch(e: RuntimeException) { message(activity,"Could not start motion session. Try again while HeySig is visible.") }
            }.setNegativeButton("Cancel",null).show()
    }
    private fun message(activity: Activity,text: String) {
        AlertDialog.Builder(activity).setMessage(text).setPositiveButton("OK",null).show()
    }
}
