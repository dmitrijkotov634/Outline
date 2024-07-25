package com.wavecat.outline.api.locks

import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.utils.Lock

class InternalAnnouncement(
    private val announcement: String
) : Lock() {
    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean =
        accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                accessibilityEvent.text.contains(announcement)
}