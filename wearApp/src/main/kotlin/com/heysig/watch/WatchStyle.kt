package com.heysig.watch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*

/** Native, OLED-friendly companion styling shared by development and release screens. */
object WatchStyle {
    private val blue = Color.rgb(72,151,255)
    private val muted = Color.rgb(190,194,204)
    fun dp(context: Context, value: Int) = (value*context.resources.displayMetrics.density+.5f).toInt()
    private fun pill(context: Context, color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(context,26).toFloat() }
    fun polish(view: View) {
        val context = view.context
        when(view) {
            is CompoundButton -> {
                view.setTextColor(Color.WHITE); view.textSize = 15f
                view.buttonTintList = ColorStateList.valueOf(blue)
                view.minHeight = dp(context,52)
                view.setPadding(dp(context,10),dp(context,10),dp(context,10),dp(context,10))
            }
            is Button -> {
                val primary = view.text.toString() in listOf("Speak command","Speak","Connect")
                val danger = view.text.toString() == "Stop"
                view.isAllCaps = false; view.textSize = 15f
                view.typeface = Typeface.create("sans-serif",Typeface.BOLD)
                view.setTextColor(if(primary) Color.BLACK else if(danger) Color.rgb(255,177,183) else Color.WHITE)
                view.backgroundTintList = null
                view.background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),pill(context,if(primary) blue else if(danger) Color.rgb(66,25,30) else Color.rgb(32,34,40)),null)
                view.minHeight = dp(context,52); view.minimumHeight = dp(context,52)
                view.setPadding(dp(context,12),dp(context,12),dp(context,12),dp(context,12))
                view.stateListAnimator = null
                view.layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(context,4); bottomMargin = dp(context,4) }
            }
            is EditText -> {
                view.setTextColor(Color.WHITE); view.setHintTextColor(muted); view.textSize = 15f
                view.minHeight = dp(context,52)
                view.background = pill(context,Color.rgb(32,34,40))
                view.setPadding(dp(context,14),dp(context,10),dp(context,14),dp(context,10))
            }
            is TextView -> {
                view.setTextColor(muted)
                view.setLineSpacing(dp(context,3).toFloat(),1f)
                if(view.text.toString() in listOf("HeySig","HeySig Watch")) {
                    view.textSize = minOf(26f,36f/context.resources.configuration.fontScale); view.setTextColor(Color.WHITE)
                    view.typeface = Typeface.create("sans-serif",Typeface.BOLD)
                    view.isAccessibilityHeading = true
                }
            }
        }
        if(view is ViewGroup) for(index in 0 until view.childCount) polish(view.getChildAt(index))
    }
    data class Labels(val speak: Int, val stop: Int, val more: Int, val speechOff: Int, val connect: Int, val speakCommand: Int)
    data class Home(val view: ScrollView, val status: TextView, val primary: Button)
    /** The real home layout is also exercised on isolated round displays by instrumentation. */
    fun home(context: Context, labels: Labels, initialStatus: String, speechOff: Boolean, speak: () -> Unit, stop: () -> Unit, more: () -> Unit, connected: Boolean = true, tapBackgroundToSpeak: Boolean = false): Home {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
            val horizontal = if(resources.configuration.isScreenRound) 32 else 16
            setPadding(dp(context,horizontal),dp(context,12),dp(context,horizontal),dp(context,12))
            setBackgroundColor(Color.BLACK)
        }
        val status = TextView(context).apply {
            text = initialStatus; setTextColor(muted); textSize = minOf(14f,20f/resources.configuration.fontScale)
            gravity = android.view.Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }; column.addView(status,LinearLayout.LayoutParams(-1,-2))
        if(speechOff) column.addView(TextView(context).apply {
            text = context.getString(labels.speechOff); setTextColor(muted)
            textSize = minOf(11f,14f/resources.configuration.fontScale); gravity = android.view.Gravity.CENTER
        },LinearLayout.LayoutParams(-1,-2))
        fun action(label:Int, invoke:()->Unit) = Button(context).apply {
            setText(label); setOnClickListener { invoke() }; polish(this)
            textSize = minOf(15f,22f/resources.configuration.fontScale)
            setPadding(dp(context,4),dp(context,4),dp(context,4),dp(context,4))
        }
        val primary=action(if(connected) labels.speak else labels.connect,speak).apply { if(connected) contentDescription=context.getString(labels.speakCommand) }
        column.addView(primary)
        val row = LinearLayout(context)
        row.addView(action(labels.stop,stop),LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(context,4) })
        row.addView(action(labels.more,more),LinearLayout.LayoutParams(0,-2,1f))
        column.addView(row,LinearLayout.LayoutParams(-1,-2))
        // Children consume their own taps, so Stop/More retain their actions.
        // Only background taps reach this detector; scrolling is not a speech trigger.
        val surface=object: ScrollView(context) {
            private val taps=GestureDetector(context,object: GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent)=true
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    performClick();return true
                }
            })
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val handled=super.onTouchEvent(event)
                return (tapBackgroundToSpeak && taps.onTouchEvent(event)) || handled
            }
            override fun performClick(): Boolean {
                val handled=super.performClick()
                if(tapBackgroundToSpeak) { speak();return true }
                return handled
            }
        }.apply {
            isFillViewport=true;isVerticalScrollBarEnabled=false
            setBackgroundColor(Color.BLACK);addView(column)
        }
        return Home(surface,status,primary)
    }
    fun prepareColumn(column: LinearLayout) {
        val context = column.context
        val horizontal = if(context.resources.configuration.isScreenRound) 32 else 16
        column.setPadding(dp(context,horizontal),dp(context,32),dp(context,horizontal),dp(context,40))
        column.setBackgroundColor(Color.BLACK)
    }
}
