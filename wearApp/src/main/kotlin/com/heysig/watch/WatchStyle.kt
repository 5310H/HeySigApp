package com.heysig.watch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
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
                val primary = view.text.toString() in listOf("Speak command","Speak")
                if(primary) { view.text = "Speak"; view.contentDescription = "Speak command" }
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
                    view.text = "HeySig"; view.textSize = minOf(26f,36f/context.resources.configuration.fontScale); view.setTextColor(Color.WHITE)
                    view.typeface = Typeface.create("sans-serif",Typeface.BOLD)
                    view.isAccessibilityHeading = true
                }
            }
        }
        if(view is ViewGroup) for(index in 0 until view.childCount) polish(view.getChildAt(index))
    }
    fun prepareColumn(column: LinearLayout) {
        val context = column.context
        val horizontal = if(context.resources.configuration.isScreenRound) 32 else 16
        column.setPadding(dp(context,horizontal),dp(context,32),dp(context,horizontal),dp(context,40))
        column.setBackgroundColor(Color.BLACK)
    }
}
