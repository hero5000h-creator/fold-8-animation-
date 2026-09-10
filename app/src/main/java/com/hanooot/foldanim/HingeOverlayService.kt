package com.hanooot.foldanim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

class HingeOverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var panelLeft: View
    private lateinit var panelRight: View
    private lateinit var hingeLine: View

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fold overlay", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fold animation active")
            .setContentText("Tracking hinge angle")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fold, null)
        panelLeft = overlayView.findViewById(R.id.panelLeft)
        panelRight = overlayView.findViewById(R.id.panelRight)
        hingeLine = overlayView.findViewById(R.id.hingeLine)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(overlayView, params)

        val d = resources.displayMetrics.density
        panelLeft.cameraDistance = 12000 * d
        panelRight.cameraDistance = 12000 * d

        overlayView.alpha = 0f
    }

    private fun setupHingeSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        applyFoldTransition(event.values[0])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun applyFoldTransition(angleDeg: Float) {
        panelLeft.pivotX = panelLeft.width.toFloat()
        panelLeft.pivotY = panelLeft.height / 2f
        panelRight.pivotX = 0f
        panelRight.pivotY = panelRight.height / 2f

        val foldAmount = ((180f - angleDeg) / 180f).coerceIn(0f, 1f)
        val rotation = foldAmount * 88f

        panelLeft.rotationY = rotation
        panelRight.rotationY = -rotation
        hingeLine.alpha = foldAmount

        val show = angleDeg < 176f
        overlayView.alpha = if (show) 1f else 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
