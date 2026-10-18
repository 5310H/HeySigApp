package com.heysig.watch

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.os.*
import com.heysig.voice.ShakeGate
import com.heysig.voice.GestureSession
import java.util.UUID
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity

/** Explicit, non-sticky motion session. Never opens UI or a microphone itself. */
class SleepingShakeService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val gate = ShakeGate()
    private lateinit var sensors: SensorManager
    private val expire = Runnable { stopSelf() }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); sensors = getSystemService(SensorManager::class.java) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if(intent?.action != START || !startAuthorized) { stopSelf(); return START_NOT_STICKY }
        startAuthorized = false
        val manager = getSystemService(NotificationManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        if(sensor == null || !manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            stopSelf(); return START_NOT_STICKY
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Shake to speak",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            stopSelf(); return START_NOT_STICKY
        }
        val stop = PendingIntent.getService(this,1,Intent(this,SleepingShakeService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this,3,Intent().setClassName(packageName,"com.heysig.watch.WatchActivity")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sleeping-watch shake is on")
            .setContentText("Shake, then tap to speak. Ends in 8 hours.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0,"Stop",stop)
        OngoingActivity.Builder(this,1,builder).setStaticIcon(android.R.drawable.ic_btn_speak_now)
            .setTouchIntent(open).build().apply(this)
        val notification = builder.build()
        try {
            if(Build.VERSION.SDK_INT >= 34) startForeground(1,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1,notification)
            running = sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI)
            session.start(SystemClock.elapsedRealtime())
            if(!running) stopSelf()
            handler.postDelayed(expire,8*60*60*1000L)
        } catch(e: RuntimeException) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(!session.active(SystemClock.elapsedRealtime())) { stopSelf(); return }
        if(!running || activityVisible) return
        val v = event.values
        val g = kotlin.math.sqrt((v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).toDouble()) / SensorManager.GRAVITY_EARTH
        if(!gate.sample(g,SystemClock.elapsedRealtime())) return
        val token = UUID.randomUUID().toString()
        if(!session.offer(token,SystemClock.elapsedRealtime())) return
        val tap = PendingIntent.getActivity(this,2,Intent().setClassName(packageName,"com.heysig.watch.WatchActivity")
            .putExtra(EXTRA,token).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) { stopSelf(); return }
        try {
            manager.notify(2,Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Tap to speak").setContentText("No microphone is active. Tap within 30 seconds.")
                .setContentIntent(tap).setAutoCancel(true).setTimeoutAfter(30_000).build())
            ShakeTrigger.acknowledge(this)
        } catch(e: SecurityException) { stopSelf() }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        running = false; startAuthorized = false; session.stop()
        sensors.unregisterListener(this); handler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java).cancel(2)
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "sleeping-shake"
        private const val START = "start-shake"
        private const val STOP = "stop-shake"
        private const val EXTRA = "shake-tap"
        private var startAuthorized = false
        private val session = GestureSession()
        var running = false; private set
        var activityVisible = false
        fun available(context: Context) = context.getSystemService(SensorManager::class.java)
            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true) != null
        fun start(activity: Activity) {
            if(running) return
            startAuthorized = true
            try { activity.startForegroundService(Intent(activity,SleepingShakeService::class.java).setAction(START)) }
            catch(e: RuntimeException) { startAuthorized = false; throw e }
        }
        fun stop(context: Context) { session.stop(); context.stopService(Intent(context,SleepingShakeService::class.java)) }
        fun consume(intent: Intent): Boolean {
            val token = intent.getStringExtra(EXTRA) ?: return false
            intent.removeExtra(EXTRA)
            return running && session.consume(token,SystemClock.elapsedRealtime())
        }
    }
}
