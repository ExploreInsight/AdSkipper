package com.adskipper.app.state

import android.util.Log

object StateMachine {

    private const val TAG = "AdSkip:State"

    @Volatile
    var currentState: AppState = AppState.PASSIVE
        private set

    private val listeners = mutableListOf<(AppState) -> Unit>()

    fun addListener(listener: (AppState) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (AppState) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun transitionTo(newState: AppState) {
        val old = currentState
        if (old == newState) return

        currentState = newState
        Log.d(TAG, "Transition: $old → $newState")

        synchronized(listeners) {
            listeners.forEach { it(newState) }
        }
    }

    fun reset() {
        transitionTo(AppState.PASSIVE)
    }
}
