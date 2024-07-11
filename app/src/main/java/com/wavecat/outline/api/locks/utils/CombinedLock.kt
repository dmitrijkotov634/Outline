package com.wavecat.outline.api.locks.utils

import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

class CombinedLock(
    private val locks: List<Lock>,
    private val interval: Long
) : Lock() {
    private val eventTimes = ConcurrentHashMap<Lock, Long>().apply {
        locks.forEach { put(it, System.currentTimeMillis() - interval) }
    }

    private inline fun tryUnlockForType(block: (Lock) -> Boolean?): Boolean {
        val currentTime = System.currentTimeMillis()

        for (lock in locks) {
            if (block(lock) == true)
                eventTimes[lock] = currentTime
        }

        return eventTimes.values.all { (currentTime - it) < interval }
    }

    override fun tryUnlock(event: KeyEvent): Boolean = tryUnlockForType { it.tryUnlock(event) }

    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean =
        tryUnlockForType { it.tryUnlock(accessibilityEvent) }
}