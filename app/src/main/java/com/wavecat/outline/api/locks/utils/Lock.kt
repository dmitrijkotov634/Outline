package com.wavecat.outline.api.locks.utils

import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

open class Lock {
    open fun tryUnlock(event: KeyEvent): Boolean? = null

    open fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean? = null
}