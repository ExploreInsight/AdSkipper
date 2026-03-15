package com.adskipper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import com.adskipper.app.MainActivity
import com.adskipper.app.R
import com.adskipper.app.state.AppState
import com.adskipper.app.state.StateMachine

class MediaInterceptorService : Service() {

    companion object {
        private const val TAG = "AdSkip:Media"
        private const val CHANNEL_ID = "ad_skip_channel"
        private const val NOTIFICATION_ID = 101
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val handler = Handler(Looper.getMainLooper())

    private val reactivateRunnable = object : Runnable {
        override fun run() {
            try {
                if (mediaSession.isActive) {
                    mediaSession.isActive = true
                }
            } catch (_: Exception) {}
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaInterceptorService starting")

        createNotificationChannel()
        setupMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.postDelayed(reactivateRunnable, 30_000)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AdSkipInterceptor").apply {

            setCallback(object : MediaSessionCompat.Callback() {

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession: onSkipToPrevious")

                    when (StateMachine.currentState) {
                        AppState.ARMED -> {
                            Log.d(TAG, "ARMED → forwarding to skip")
                            AdSkipAccessibilityService.instance?.performAdSkip()
                        }

                        AppState.PASSIVE -> {
                            Log.d(TAG, "PASSIVE → passing to real player")
                            passToRealPlayer(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        }

                        AppState.SKIPPED -> {
                            Log.d(TAG, "SKIPPED → ignoring")
                        }
                    }
                }

                override fun onPlay() {
                    passToRealPlayer(KeyEvent.KEYCODE_MEDIA_PLAY)
                }

                override fun onPause() {
                    passToRealPlayer(KeyEvent.KEYCODE_MEDIA_PAUSE)
                }

                override fun onSkipToNext() {
                    passToRealPlayer(KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                override fun onStop() {
                    passToRealPlayer(KeyEvent.KEYCODE_MEDIA_STOP)
                }
            })

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        PlaybackStateCompat.STATE_PAUSED,
                        0L,
                        0f
                    )
                    .setActions(
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )

            isActive = true
        }

        Log.d(TAG, "MediaSession created and active")
    }

    private fun passToRealPlayer(keyCode: Int) {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pass media key", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ad Skipper Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ad skipper running in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ad Skipper Active")
            .setContentText("Triple-tap earbuds when you hear the ting")
            .setSmallIcon(R.drawable.ic_skip)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaSession.isActive = false
            mediaSession.release()
        } catch (_: Exception) {}
        Log.d(TAG, "MediaInterceptorService destroyed")
        super.onDestroy()
    }
}
