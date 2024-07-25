package com.wavecat.outline.api.locks.filter

import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.utils.Lock

class EventTypeLock(private val eventType: Int) : Lock() {
    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean =
        accessibilityEvent.eventType == eventType
}