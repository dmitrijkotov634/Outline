package com.wavecat.outline.api.locks.utils

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class StepsLock(
    private val locks: List<Lock>,
    private val interval: Long
) : Lock() {
    private var latestTime = System.currentTimeMillis()

    private var waitingFor = locks[0]

    private inline fun tryUnlockForType(block: (Lock) -> Boolean?): Boolean? {
        val currentTime = System.currentTimeMillis()

        if ((currentTime - latestTime) > interval) {
            Log.i(TAG, "$this time reset")
            waitingFor = locks[0]
        }

        latestTime = currentTime

        val status = block(waitingFor)

        if (status == true) {
            val index = locks.indexOf(waitingFor)

            if (index == locks.size - 1) {
                Log.i(TAG, "$this unlock")
                waitingFor = locks[0]
                return true
            }

            waitingFor = locks[index + 1]
            Log.i(TAG, "$this waiting for the next step")
            return null
        }

        if (status == false) {
            Log.i(TAG, "$this chain reset")
            waitingFor = locks[0]
        }

        return status
    }

    override fun tryUnlock(event: KeyEvent): Boolean? =
        tryUnlockForType { it.tryUnlock(event) }

    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean? =
        tryUnlockForType { it.tryUnlock(accessibilityEvent) }

    companion object {
        const val TAG = "StepsLock"
    }
}