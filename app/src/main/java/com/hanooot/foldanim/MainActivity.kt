package com.hanooot.foldanim

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Simple control screen: request the "draw over other apps" permission once,
 * then start/stop the background HingeOverlayService that actually renders
 * the fold/unfold effect system-wide.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }

        statusLabel = TextView(this).apply {
            textSize = 16f
            text = "checking overlay permission…"
        }

        val grantButton = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener { requestOverlayPermission() }
        }

        val startButton = Button(this).apply {
            text = "Start background fold effect"
            setOnClickListener { startOverlayService() }
        }

        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopOverlayService() }
        }

        root.addView(statusLabel)
        root.addView(grantButton)
        root.addView(startButton)
        root.addView(stopButton)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        statusLabel.text = if (hasOverlayPermission()) {
            "overlay permission: granted ✓\ntap Start to enable the effect"
        } else {
            "overlay permission: NOT granted\ntap Grant first"
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        val serviceIntent = Intent(this, HingeOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, HingeOverlayService::class.java))
    }
}
