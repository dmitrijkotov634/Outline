package com.wavecat.outline.api

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.IgnoreKeyEvent
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.coroutineResume
import com.wavecat.outline.utils.coroutineStatus
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.runOnUiThread
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.util.Timer
import kotlin.concurrent.timerTask

class Script(
    private val name: String,
    private val globals: Globals,
    private val coroutine: LuaValue,
    private var lock: Lock? = null,
) {
    private var timer: Timer = Timer()

    fun resume(): Boolean {
        if (globals.coroutineStatus(coroutine) == "dead")
            return false

        globals.set("context", CoerceJavaToLua.coerce(this))

        Log.i(TAG, "$name resumed")

        val result = globals.coroutineResume(coroutine)

        when (val value = result.arg(2)) {
            is LuaString -> throw LuaError(value)
            is LuaUserdata -> {
                val resultLock = value.touserdata()

                if (resultLock is IgnoreKeyEvent) {
                    Log.i(TAG, "$name flagged ignore KeyEvent")

                    resume()
                    return false
                }

                Log.i(TAG, "$name locked by $resultLock")

                lock = resultLock as Lock
            }
        }

        return true
    }

    fun delay(interval: Long) {
        Log.i(TAG, "$name start $interval delay")

        timer.schedule(timerTask {
            runOnUiThread { resume() }
        }, interval)

        globals.coroutineYield(CoerceJavaToLua.coerce(Lock()))
    }

    fun notifyAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (lock?.tryUnlock(accessibilityEvent) == true)
            resume()
    }

    fun notifyKeyEvent(event: KeyEvent): Boolean {
        if (lock?.tryUnlock(event) == true)
            return resume()

        return false
    }

    companion object {
        const val TAG = "Script"
    }
}