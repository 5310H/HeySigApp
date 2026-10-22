package com.heysig.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
import com.heysig.control.*

/** A decorative sound scene and honest last-sent snapshot; never samples audio or controls aids. */
class SigSceneCard(context: Context, fitting: Fitting, state: RequestedState, teach: () -> Unit): LinearLayout(context) {
    init {
        orientation = VERTICAL
        val pad = PhoneStyle.dp(context,16)
        setPadding(pad,pad,pad,pad)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(18,40,66),Color.rgb(16,64,76))).apply { cornerRadius=PhoneStyle.dp(context,24).toFloat() }
        layoutParams = LayoutParams(-1,-2).apply { topMargin=PhoneStyle.dp(context,12); bottomMargin=PhoneStyle.dp(context,8) }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).also {
            it.text=value; PhoneStyle.text(it,size,bold,color); addView(it)
        }
        val white = Color.WHITE
        val soft = Color.rgb(201,222,233)
        label("Your Sig Scene",19f,white,true).isAccessibilityHeading=true
        addView(SceneArtwork(context),LayoutParams(-1,PhoneStyle.dp(context,64)))
        val program = fitting.programs.find { it.slot == state.program }?.name
        program?.let { label("Program: $it",15f,soft,true) }
        val levels=listOf(
            LevelControl.VOLUME to "Volume",
            LevelControl.SHARPNESS to "Balance",
            LevelControl.TINNITUS to "Tinnitus"
        ).mapNotNull { (control,name) -> state.levels[control]?.let { "$name: $it" } }
        if(levels.isNotEmpty()) label(levels.joinToString(" · "),13f,soft)
        addView(Button(context).apply {
            text="Teach a phrase"; PhoneStyle.action(this)
            setOnClickListener { teach() }
            contentDescription="Teach a personal phrase. Opens Voice tools and personal phrases."
        })
    }
    private class SceneArtwork(context: Context): View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND }
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Fit the complete scene, including the outer rings and stroke, without
            // stretching it or letting the View clip its top and bottom.
            val sceneWidth=320f
            val sceneHeight=160f
            val scale=minOf(width/sceneWidth,height/sceneHeight)
            canvas.save()
            canvas.translate((width-sceneWidth*scale)/2f,(height-sceneHeight*scale)/2f)
            canvas.scale(scale,scale)
            canvas.translate(0f,30f)
            paint.color=Color.rgb(74,160,185);paint.strokeWidth=1f
            for(radius in listOf(34f,51f,68f)) {
                paint.alpha=if(radius==34f) 110 else 50
                canvas.drawArc(160-radius,50-radius,160+radius,50+radius,-68f,136f,false,paint)
                canvas.drawArc(160-radius,50-radius,160+radius,50+radius,112f,136f,false,paint)
            }
            paint.color=Color.rgb(71,225,197);paint.alpha=255;paint.strokeWidth=3.5f
            canvas.save();canvas.translate(93f,-2f);canvas.scale(.39f,.39f)
            val ear=Path().apply {
                moveTo(140f,50f);cubicTo(200f,35f,245f,70f,245f,135f)
                cubicTo(245f,200f,200f,225f,170f,225f);cubicTo(145f,225f,130f,210f,130f,185f)
            }
            paint.strokeWidth=8f;canvas.drawPath(ear,paint)
            paint.alpha=140;paint.strokeWidth=6f
            canvas.drawPath(Path().apply { moveTo(150f,90f);cubicTo(185f,85f,210f,110f,210f,135f);cubicTo(210f,165f,180f,185f,155f,185f) },paint)
            canvas.restore()
            paint.alpha=160;paint.strokeWidth=2f
            for((x,y) in listOf(66f to 38f,252f to 63f)) {
                canvas.drawLine(x-3,y,x+3,y,paint);canvas.drawLine(x,y-3,x,y+3,paint)
            }
            canvas.restore()
        }
    }
}
