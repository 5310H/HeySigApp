package com.heysig.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Native controls and scalable type; styling never reads or writes aid settings. */
object PhoneStyle {
    val background = Color.rgb(242,242,247)
    val ink = Color.rgb(28,28,30)
    val secondary = Color.rgb(88,88,96)
    val blue = Color.rgb(0,91,202)
    val red = Color.rgb(185,36,46)
    val tint = Color.rgb(232,240,255)
    fun dp(context: Context, value: Int) = (value*context.resources.displayMetrics.density+.5f).toInt()
    fun shape(context: Context, color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context,radius).toFloat()
        if(stroke != null) setStroke(dp(context,1),stroke)
    }
    fun text(view: TextView, size: Float = 16f, bold: Boolean = false, color: Int = ink) {
        view.textSize = size; view.setTextColor(color)
        view.typeface = Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        view.setLineSpacing(dp(view.context,3).toFloat(),1f)
        view.includeFontPadding = true
    }
    fun action(view: Button, role: String = "secondary") {
        val context = view.context
        val primary = role == "primary" || role == "selected"
        val destructive = role == "danger"
        val fill = if(primary) blue else if(destructive) Color.rgb(255,235,237) else if(role == "program") background else Color.WHITE
        val foreground = if(primary) Color.WHITE else if(destructive) red else if(role == "program") ink else blue
        view.tag = role
        text(view,16f,true,foreground)
        view.isAllCaps = false
        view.minHeight = dp(context,52); view.minimumHeight = dp(context,52)
        view.setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12))
        view.backgroundTintList = null
        view.background = RippleDrawable(ColorStateList.valueOf(0x18005BCA),shape(context,fill),null)
        view.alpha = if(view.isEnabled) 1f else .45f
        view.stateListAnimator = null
        view.layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(context,4); bottomMargin = dp(context,4) }
    }
    fun card(context: Context, parent: LinearLayout): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context,18),dp(context,16),dp(context,18),dp(context,16))
        background = shape(context,Color.WHITE,22)
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(context,16) })
    }
    fun heading(context: Context, parent: LinearLayout, title: String) = TextView(context).also {
        text(it,20f,true); it.text = title; it.isAccessibilityHeading = true
        it.setPadding(0,dp(context,2),0,dp(context,10)); parent.addView(it)
    }
    /** Includes feature adapters so Setup, Noise, Watch and Privacy share one visual language. */
    fun polish(view: View) {
        val context = view.context
        when(view) {
            is CompoundButton -> {
                text(view,16f); view.minHeight = dp(context,52)
                view.buttonTintList = ColorStateList.valueOf(blue)
                view.setPadding(dp(context,4),dp(context,8),dp(context,4),dp(context,8))
            }
            is Button -> if(view.tag == null) action(view)
            is EditText -> {
                text(view,17f)
                view.minHeight = dp(context,52)
                view.setPadding(dp(context,14),dp(context,10),dp(context,14),dp(context,10))
                view.background = shape(context,background,12)
                view.setHintTextColor(secondary)
                view.layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(context,12) }
            }
            is Spinner -> {
                view.minimumHeight = dp(context,52)
                view.background = shape(context,background,12)
                view.setPadding(dp(context,10),dp(context,6),dp(context,10),dp(context,6))
                view.layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(context,10) }
            }
        }
        if(view is ViewGroup) for(index in 0 until view.childCount) polish(view.getChildAt(index))
    }
    fun icon(context: Context, kind: String, color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun draw(canvas: Canvas) {
            canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
            when(kind) {
                "Home" -> { val p=android.graphics.Path(); p.moveTo(3f,11f); p.lineTo(12f,3f); p.lineTo(21f,11f); canvas.drawPath(p,paint); canvas.drawRoundRect(6f,10f,18f,21f,2f,2f,paint) }
                "Noise" -> { listOf(6f to 6f,10f to 11f,14f to 8f,18f to 4f).forEach { (x,h) -> canvas.drawLine(x,12-h/2,x,12+h/2,paint) } }
                "Saved" -> { canvas.drawRoundRect(5f,3f,19f,21f,2f,2f,paint); canvas.drawLine(9f,8f,15f,8f,paint); canvas.drawLine(9f,12f,15f,12f,paint) }
                else -> { canvas.drawCircle(12f,12f,7f,paint); canvas.drawCircle(12f,12f,2.5f,paint); for(a in 0..7) { val angle=a*Math.PI/4; canvas.drawLine((12+7*kotlin.math.cos(angle)).toFloat(),(12+7*kotlin.math.sin(angle)).toFloat(),(12+9*kotlin.math.cos(angle)).toFloat(),(12+9*kotlin.math.sin(angle)).toFloat(),paint) } }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = dp(context,24)
        override fun getIntrinsicHeight() = dp(context,24)
    }.apply { setBounds(0,0,dp(context,24),dp(context,24)) }
}
