package com.wavecat.outline.api

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.IgnoreKeyEvent
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.coroutineResume
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.runOnUiThread
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.util.Timer
import kotlin.concurrent.timerTask

class Script(
    val name: String,
    private val globals: Globals,
    private val coroutine: LuaValue,
    private var onResume: (Script) -> Unit,
    private var onRecycle: (Script) -> Unit,
    private var lock: Lock? = null,
) {
    private var timer: Timer = Timer()

    var latestKeyEvent: KeyEvent? = null
    var latestAccessibilityEvent: AccessibilityEvent? = null

    fun resumeCoroutine(throwErrors: Boolean = true): Varargs {
        val result = globals.coroutineResume(coroutine)

        if (throwErrors && !result.arg(1).optboolean(true)) {
            recycle()
            onRecycle(this)
            throw LuaError(result.arg(2))
        }

        return result
    }

    fun resume(): Boolean {
        onResume(this)

        Log.i(TAG, "$name resumed")

        when (val value = resumeCoroutine().arg(2)) {
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
            runOnUiThread {
                resume()
            }
        }, interval)

        globals.coroutineYield(CoerceJavaToLua.coerce(Lock()))
    }

    fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (lock?.tryUnlock(accessibilityEvent) == true) {
            latestAccessibilityEvent = accessibilityEvent
            resume()
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (lock?.tryUnlock(event) == true) {
            latestKeyEvent = event
            return resume()
        }

        return false
    }

    fun recycle() {
        timer.cancel()
        timer.purge()
    }

    companion object {
        const val TAG = "Script"
    }
}