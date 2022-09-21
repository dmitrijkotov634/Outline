package com.wavecat.outline

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.luaj.vm2.*
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.util.*


class OutlineService : AccessibilityService() {

    private lateinit var globals: Globals

    var timer: Timer? = null
    var result: Varargs? = null

    interface Lock {
        fun tryUnlock(event: KeyEvent): Boolean
    }

    class Ignore

    class WaitForLock(
        private val nextKeyCode: LuaValue,
        private val nextAction: LuaValue,
    ) : Lock {
        override fun tryUnlock(event: KeyEvent): Boolean =
            (nextAction.isnil() || event.action == nextAction.toint()) && (nextKeyCode.isnil() || event.keyCode == nextKeyCode.toint())
    }

    class Script(
        private val globals: Globals,
        private val coroutine: LuaValue,
        private var lock: Lock,
    ) {

        private fun next(): Boolean {
            val result = globals
                .get(COROUTINE)
                .get(RESUME)
                .invoke(coroutine)

            when (val value = result.arg(2)) {
                is LuaString -> throw LuaError(value)
                is LuaUserdata -> {
                    val resultLock = value.touserdata()

                    if (resultLock is Ignore) {
                        next()
                        return false
                    }

                    lock = resultLock as Lock
                }
            }

            return true
        }

        fun notify(event: KeyEvent): Boolean {
            val status = lock.tryUnlock(event)

            if (status) {
                return next()
            }

            return false
        }
    }

    private val scripts = mutableListOf<Script>()

    override fun onServiceConnected() {
        instance = this

        super.onServiceConnected()

        val info = AccessibilityServiceInfo()

        info.flags =
            AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

        info.eventTypes = 0
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK

        serviceInfo = info

        globals = JsePlatform.standardGlobals()

        for (i in KeyEvent::class.java.declaredFields) {
            if (i.name.startsWith("KEYCODE_")) {
                globals.set(i.name.substring(8), i.getInt(null))
            }
        }

        for (i in AccessibilityService::class.java.declaredFields) {
            if (i.name.startsWith("GLOBAL_ACTION_")) {
                globals.set(i.name.substring(14), i.getInt(null))
            }
        }

        globals.set("service", CoerceJavaToLua.coerce(this))

        globals.set("ACTION_UP", KeyEvent.ACTION_UP)
        globals.set("ACTION_DOWN", KeyEvent.ACTION_DOWN)

        globals.set("timerTask", object : OneArgFunction() {
            override fun call(arg: LuaValue?): LuaValue {
                return CoerceJavaToLua.coerce(object : TimerTask() {
                    override fun run() {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                arg?.call()
                            } catch (e: Exception) {
                                notifyError(e)
                            }
                        }
                    }
                })
            }
        })

        globals.set("waitFor", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                globals.get(COROUTINE).get(YIELD).call(
                    CoerceJavaToLua.coerce(
                        WaitForLock(
                            args.arg1()!!,
                            args.arg(2)!!,
                        )
                    )
                )

                return result!!
            }
        })

        globals.set("ignore", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                globals.get(COROUTINE).get(YIELD).call(
                    CoerceJavaToLua.coerce(Ignore())
                )

                return result!!
            }
        })

        globals.set("mediaKeyEvent", object : TwoArgFunction() {
            override fun call(arg1: LuaValue?, arg2: LuaValue?): LuaValue {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.dispatchMediaKeyEvent(KeyEvent(arg1!!.checkint(), arg2!!.checkint()))
                return NIL
            }
        })

        globals.set("perform", object : OneArgFunction() {
            override fun call(arg: LuaValue?): LuaValue {
                performGlobalAction(arg!!.checkint())
                return NIL
            }
        })

        globals.set("toast", object : OneArgFunction() {
            override fun call(arg: LuaValue?): LuaValue {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, arg?.optjstring(""), Toast.LENGTH_SHORT)
                        .show()
                }

                return NIL
            }
        })

        loadScript()
    }

    fun loadScript() = try {
        scripts.clear()

        timer?.cancel()
        timer = Timer()

        val coroutine = globals.get(COROUTINE).get(CREATE).call(
            globals.load(
                PreferenceManager.getDefaultSharedPreferences(this).getString("script", ""),
                "script"
            )
        )

        scripts.add(
            Script(
                globals,
                coroutine,
                globals.get(COROUTINE).get(RESUME).invoke(coroutine)
                    .checkuserdata(2, Lock::class.java) as Lock
            )
        )

    } catch (e: Exception) {
        notifyError(e)
    }

    private fun notifyError(e: Exception) {
        Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        var ignore = false

        if (event != null) {
            result =
                LuaValue.varargsOf(
                    arrayOf(
                        LuaValue.valueOf(event.keyCode),
                        LuaValue.valueOf(event.action),
                        CoerceJavaToLua.coerce(event)
                    )
                )

            try {
                for (script in scripts)
                    if (script.notify(event))
                        ignore = true
            } catch (e: LuaError) {
                notifyError(e)
            }
        }

        return ignore
    }

    companion object {
        const val COROUTINE = "coroutine"
        const val YIELD = "yield"
        const val RESUME = "resume"
        const val CREATE = "create"

        var instance: OutlineService? = null
    }
}