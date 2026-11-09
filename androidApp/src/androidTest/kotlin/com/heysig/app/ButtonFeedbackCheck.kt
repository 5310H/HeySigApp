package com.heysig.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Button
import com.heysig.watch.WatchStyle

/** Exercises visual states on isolated controls; never clicks an aid command. */
object ButtonFeedbackCheck {
    fun run(activity: Activity) {
        fun checkFeedback(button: Button) {
            button.layout(0,0,240,100)
            fun color(): Int {
                val bitmap=Bitmap.createBitmap(240,100,Bitmap.Config.ARGB_8888)
                return try { button.background.setBounds(0,0,240,100);button.background.draw(Canvas(bitmap));bitmap.getPixel(120,50) }
                finally { bitmap.recycle() }
            }
            val initial=color();val text=button.currentTextColor
            button.isPressed=true
            check(color()==text && button.currentTextColor==initial) { "Button does not reverse: ${button.text}" }
            button.isPressed=false
            check(color()==initial && button.currentTextColor==text) { "Button did not restore after release" }
            button.isPressed=true;button.cancelPendingInputEvents();button.isPressed=false
            check(color()==initial && button.currentTextColor==text) { "Canceled button remained pressed" }
            button.isEnabled=false;button.isPressed=true
            check(color()==initial) { "Disabled button shows an active press" }
            button.isPressed=false
        }
        for(role in listOf("primary","secondary","selected","program","danger","noiseOn","noiseOff")) {
            checkFeedback(Button(activity).apply { text=role;PhoneStyle.action(this,role) })
        }
        checkFeedback(Button(activity).apply { text="Navigation";PhoneStyle.pressFeedback(this,android.graphics.Color.WHITE,PhoneStyle.blue,0) })
        for(label in listOf("Speak","Connect","Stop","More")) {
            checkFeedback(Button(activity).apply { text=label;WatchStyle.polish(this) })
        }
        val dialog=AlertDialog.Builder(activity).setMessage("Button feedback check").setPositiveButton("Done",null).create()
        try { dialog.show();checkFeedback(dialog.getButton(AlertDialog.BUTTON_POSITIVE)) }
        finally { dialog.dismiss() }
    }
}
