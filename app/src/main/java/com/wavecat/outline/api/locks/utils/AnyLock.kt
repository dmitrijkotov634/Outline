package com.wavecat.outline.api.locks.utils

import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent


class AnyLock(
    private val locks: List<Lock>
) : Lock() {
    constructor(vararg locks: Lock) : this(locks.toList())

    override fun tryUnlock(event: KeyEvent): Boolean =
        locks.any { it.tryUnlock(event) == true }

    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean =
        locks.any { it.tryUnlock(accessibilityEvent) == true }
}