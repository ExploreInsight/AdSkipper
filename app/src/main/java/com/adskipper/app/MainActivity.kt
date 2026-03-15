package com.adskipper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.adskipper.app.service.MediaInterceptorService
import com.adskipper.app.state.AppState
import com.adskipper.app.state.StateMachine

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvState: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnStartService: Button

    private val handler = Handler(Looper.getMainLooper())

    private val stateListener: (AppState) -> Unit = { state ->
        handler.post { updateStateDisplay(state) }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshUI()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvState = findViewById(R.id.tvState)
        tvAccessibility = findViewById(R.id.tvAccessibility)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnStartService = findViewById(R.id.btnStartService)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnStartService.setOnClickListener {
            startMediaService()
        }

        StateMachine.addListener(stateListener)

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        StateMachine.removeListener(stateListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshUI() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        tvAccessibility.text = if (accessibilityEnabled) {
            "✅ Accessibility Service: ENABLED"
        } else {
            "❌ Accessibility Service: DISABLED"
        }

        btnAccessibility.text = if (accessibilityEnabled) {
            "Accessibility Settings ✓"
        } else {
            "Enable Accessibility Service"
        }

        tvStatus.text = if (accessibilityEnabled) {
            "Ad Skipper is ACTIVE"
        } else {
            "Ad Skipper needs setup"
        }

        updateStateDisplay(StateMachine.currentState)
    }

    private fun updateStateDisplay(state: AppState) {
        tvState.text = when (state) {
            AppState.PASSIVE -> "🟢 PASSIVE — Watching for ads..."
            AppState.ARMED   -> "🟡 ARMED — Skip button found! Tap earbuds!"
            AppState.SKIPPED -> "🔵 SKIPPED — Cooling down..."
        }
    }

    private fun startMediaService() {
        val intent = Intent(this, MediaInterceptorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        AlertDialog.Builder(this)
            .setTitle("Service Started")
            .setMessage("Media interceptor is running.\n\nMake sure Accessibility Service is also enabled.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedId = "$packageName/.service.AdSkipAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .any { it.equals(expectedId, ignoreCase = true) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
