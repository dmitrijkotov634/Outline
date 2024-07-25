package com.wavecat.outline.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent


class WindowContentService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this

        super.onServiceConnected()

        val info = AccessibilityServiceInfo()

        info.flags =
            AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_ANNOUNCEMENT

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK

        serviceInfo = info
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val service = OutlineService.instance ?: return

        if (event?.source != null)
            service.onAccessibilityEvent(event)
    }

    companion object {
        @Suppress("StaticFieldLeak")
        var instance: WindowContentService? = null
    }
}