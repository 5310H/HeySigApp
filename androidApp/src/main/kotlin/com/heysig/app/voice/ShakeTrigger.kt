package com.heysig.app.voice

import android.content.Context
import android.hardware.*
import android.os.*
import com.heysig.voice.ShakeGate

/** Session/activity owned; no sensor values are retained. */
class ShakeTrigger(private val context: Context, peakG: Double = 2.7, private val action: () -> Unit): SensorEventListener {
    private val manager=context.getSystemService(SensorManager::class.java)
    private val gate=ShakeGate.phone(peakG)
    fun start(): Boolean {
        stop()
        val sensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        return manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI)
    }
    fun stop() { manager.unregisterListener(this) }
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        val v=event.values
        val g=kotlin.math.sqrt((v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).toDouble())/SensorManager.GRAVITY_EARTH
        if(gate.sample(g,SystemClock.elapsedRealtime())) action()
    }
    companion object {
        fun acknowledge(context: Context) {
            val vibrator=context.getSystemService(Vibrator::class.java)
            if(vibrator?.hasVibrator()==true) vibrator.vibrate(VibrationEffect.createOneShot(60,VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
