package com.hanooot.foldanim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Keeps the fold/unfold visual alive system-wide, on top of whatever the user
 * is doing, instead of only while MainActivity is open. This is what makes it
 * feel like a real device feature rather than a single-app demo.
 */
class HingeOverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var panelLeft: View
    private lateinit var panelRight: View
    private lateinit var hingeLine: View
    private lateinit var angleLabel: TextView

    private lateinit var sensorManager: SensorManager
    private var hingeSensor: Sensor? = null

    companion object {
        const val CHANNEL_ID = "fold_overlay_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        setupOverlayWindow()
        setupHingeSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // restart the service if the system kills it
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- notification required to keep a foreground service alive ----

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fold overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fold animation active")
            .setContentText("Tracking hinge angle in the background")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    // ---- the overlay window itself ----

    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fold, null)
        panelLeft = overlayView.findViewById(R.id.panelLeft)
        panelRight = overlayView.findViewById(R.id.panelRight)
        hingeLine = overlayView.findViewById(R.id.hingeLine)
        angleLabel = overlayView.findViewById(R.id.angleLabel)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            // NOT_TOUCHABLE + NOT_FOCUSABLE so it never blocks taps to whatever
            // app is underneath — this is a visual layer only
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(overlayView, params)

        // start fully transparent — it should only appear DURING a fold/unfold
        overlayView.alpha = 0f
    }

    // ---- hinge sensor wiring, same math as the in-app version ----

    private fun setupHingeSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val angle = event.values[0] // 0 = closed, 180 = flat
        applyFoldTransition(angle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun applyFoldTransition(angleDeg: Float) {
        angleLabel.text = "hinge: ${angleDeg.toInt()}°"

        panelLeft.pivotX = panelLeft.width.toFloat()
        panelLeft.pivotY = panelLeft.height / 2f
        panelRight.pivotX = 0f
        panelRight.pivotY = panelRight.height / 2f

        val foldAmount = (180f - angleDeg) / 180f
        val rotation = foldAmount * 90f

        panelLeft.rotationY = rotation
        panelRight.rotationY = -rotation
        hingeLine.alpha = foldAmount

        val brightness = 1f - (foldAmount * 0.6f)
        panelLeft.alpha = brightness
        panelRight.alpha = brightness

        // only show the overlay while actively mid-fold; fade out once fully
        // open (flat) or fully closed and settled, so it never sits there
        // covering the real screen at rest
        val midFold = angleDeg in 8f..172f
        overlayView.animate().alpha(if (midFold) 1f else 0f).setDuration(120).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
