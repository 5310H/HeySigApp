package com.heysig.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Bounded scalar history only; gaps are shown instead of implying continuous capture. */
class NoiseGraphView(context: Context): View(context) {
    var samples: List<Pair<Long,Double>> = emptyList()
        set(value) { if(field==value) return;field=value.takeLast(120);contentDescription=if(field.isEmpty()) "Noise graph: waiting for measurements" else "Sound around you over time. ${field.size} sound checks. Earlier checks are on the left; the latest is on the right. Higher dots mean louder sound.";invalidate() }
    var boundaries: List<Double> = emptyList()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription="Sound graph: waiting for the first sound check";minimumHeight=PhoneStyle.dp(context,180);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale=resources.displayMetrics.density
        val textScale=resources.configuration.fontScale
        val left=72f*scale*textScale.coerceAtMost(1.5f);val right=width-12f*scale
        val top=16f*scale;val bottom=height-30f*scale*textScale
        val low=minOf(-80.0,(samples.map { it.second }+boundaries).minOrNull() ?: -80.0).coerceAtLeast(-120.0)
        val high=maxOf(-10.0,(samples.map { it.second }+boundaries).maxOrNull() ?: -10.0).coerceAtMost(0.0)
        fun y(value:Double)=(bottom-(value-low)/(high-low)*(bottom-top)).toFloat()
        paint.textSize=11f*scale*textScale;paint.strokeWidth=scale
        listOf(low,(low+high)/2,high).forEach {
            paint.color=android.graphics.Color.LTGRAY;canvas.drawLine(left,y(it),right,y(it),paint)
            paint.color=PhoneStyle.secondary
            val label=if(it==high) "Louder" else if(it==low) "Quieter" else ""
            canvas.drawText(label,4f*scale,y(it)+4f*scale,paint)
        }
        paint.color=PhoneStyle.secondary;canvas.drawText("Earlier",left,height-5f*scale,paint)
        val nowLabel="Latest";canvas.drawText(nowLabel,right-paint.measureText(nowLabel),height-5f*scale,paint)
        paint.color=0x66707070;paint.pathEffect=android.graphics.DashPathEffect(floatArrayOf(4f*scale,4f*scale),0f)
        boundaries.forEach { canvas.drawLine(left,y(it),right,y(it),paint) };paint.pathEffect=null
        if(samples.isEmpty()) { paint.color=PhoneStyle.secondary;canvas.drawText("Waiting for sound checks",left,top+30f*scale,paint);return }
        val end=samples.last().first;val start=minOf(samples.first().first,end-60_000)
        fun x(time:Long)=left+(right-left)*(time-start).toFloat()/(end-start).toFloat()
        paint.color=PhoneStyle.blue;paint.strokeWidth=2.5f*scale;paint.style=Paint.Style.STROKE
        val path=Path()
        samples.forEachIndexed { i,sample ->
            if(i==0 || sample.first-samples[i-1].first>60_000) path.moveTo(x(sample.first),y(sample.second)) else path.lineTo(x(sample.first),y(sample.second))
        }
        canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        samples.forEach { canvas.drawCircle(x(it.first),y(it.second),2.5f*scale,paint) }
    }
}
