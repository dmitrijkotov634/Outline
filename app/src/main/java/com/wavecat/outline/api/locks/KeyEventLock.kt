package com.wavecat.outline.api.locks

import android.view.KeyEvent
import com.wavecat.outline.api.locks.utils.Lock

class KeyEventLock(
    private val nextKeyCode: Int?,
    private val nextAction: Int?,
) : Lock() {
    override fun tryUnlock(event: KeyEvent): Boolean =
        (nextAction == null || event.action == nextAction) && (nextKeyCode == null || event.keyCode == nextKeyCode)
}