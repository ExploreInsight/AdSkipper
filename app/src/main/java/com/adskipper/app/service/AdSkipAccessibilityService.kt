package com.adskipper.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adskipper.app.accessibility.NodeFinder
import com.adskipper.app.audio.TingPlayer
import com.adskipper.app.state.AppState
import com.adskipper.app.state.StateMachine

class AdSkipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkip:Service"

        @Volatile
        var instance: AdSkipAccessibilityService? = null
            private set

        private const val TRIGGER_KEYCODE = KeyEvent.KEYCODE_MEDIA_PREVIOUS
    }

    private lateinit var tingPlayer: TingPlayer
    private val nodeFinder = NodeFinder()
    private val handler = Handler(Looper.getMainLooper())

    private var lastTriggerTime = 0L
    private val debounceMs = 500L

    private val resetDelayMs = 2000L

    private var lastCheckTime = 0L
    private val checkThrottleMs = 800L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tingPlayer = TingPlayer(this)
        StateMachine.reset()
        Log.d(TAG, "✅ Service connected, state → PASSIVE")
    }

    override fun onDestroy() {
        instance = null
        tingPlayer.release()
        handler.removeCallbacksAndMessages(null)
        StateMachine.reset()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val validTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED
        )
        if (event.eventType !in validTypes) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckTime < checkThrottleMs) return
        lastCheckTime = now

        if (StateMachine.currentState == AppState.SKIPPED) return

        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return

        try {
            val skipNode = nodeFinder.findSkipButton(root)

            when (StateMachine.currentState) {
                AppState.PASSIVE -> {
                    if (skipNode != null) {
                        StateMachine.transitionTo(AppState.ARMED)
                        tingPlayer.playTing()
                        Log.d(TAG, "🔔 ARMED — Skip button detected, ting played")
                    }
                }

                AppState.ARMED -> {
                    if (skipNode == null) {
                        Log.d(TAG, "Skip button gone, resetting to PASSIVE")
                        StateMachine.reset()
                    }
                }

                AppState.SKIPPED -> {
                }
            }

            skipNode?.let { safeRecycle(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        } finally {
            safeRecycle(root)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {

        if (event.keyCode != TRIGGER_KEYCODE) {
            return super.onKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < debounceMs) return true
        lastTriggerTime = now

        Log.d(TAG, "🎧 Earbud tap detected, current state: ${StateMachine.currentState}")

        return when (StateMachine.currentState) {

            AppState.ARMED -> {
                Log.d(TAG, "⚡ Executing skip!")
                performAdSkip()
                true
            }

            AppState.PASSIVE -> {
                Log.d(TAG, "PASSIVE — letting tap pass through")
                false
            }

            AppState.SKIPPED -> {
                Log.d(TAG, "SKIPPED — ignoring tap")
                true
            }
        }
    }

    fun performAdSkip() {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        if (root == null) {
            Log.w(TAG, "No root window, cannot skip")
            StateMachine.reset()
            return
        }

        try {
            val skipNode = nodeFinder.findSkipButton(root)

            if (skipNode != null) {
                val success = skipNode.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
                Log.d(TAG, "✅ Skip Ad clicked: $success")
                safeRecycle(skipNode)

                StateMachine.transitionTo(AppState.SKIPPED)

                handler.postDelayed({
                    StateMachine.reset()
                    Log.d(TAG, "🔄 Reset to PASSIVE")
                }, resetDelayMs)

            } else {
                Log.w(TAG, "Was ARMED but skip button not found now")
                StateMachine.reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during skip", e)
            StateMachine.reset()
        } finally {
            safeRecycle(root)
        }
    }

    fun performGestureTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture tap completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture tap cancelled")
            }
        }, null)
    }

    private fun safeRecycle(node: AccessibilityNodeInfo) {
        try {
            node.recycle()
        } catch (_: Exception) {}
    }
}
