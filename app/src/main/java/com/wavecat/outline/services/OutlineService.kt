package com.wavecat.outline.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wavecat.outline.api.Script
import com.wavecat.outline.api.installAccessibilityEventLib
import com.wavecat.outline.api.installAutomationLib
import com.wavecat.outline.api.installKeyEventLib
import com.wavecat.outline.api.installUtilsLib
import com.wavecat.outline.api.locks.utils.AllLock
import com.wavecat.outline.utils.CustomDebugLib
import com.wavecat.outline.utils.coroutineCreate
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.oneArgFunction
import com.wavecat.outline.utils.runOnUiThread
import com.wavecat.outline.utils.toList
import com.wavecat.outline.utils.tojstringuserdata
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.varArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform


class OutlineService : AccessibilityService() {
    private val debugLib = CustomDebugLib()

    private var scriptContext: Script? = null

    private val globals: Globals by lazy {
        JsePlatform.standardGlobals().apply {
            for (i in AccessibilityService::class.java.declaredFields)
                if (i.name.startsWith("GLOBAL_ACTION_"))
                    set(i.name.substring(14), i.getInt(null))

            set("service", CoerceJavaToLua.coerce(this))

            set("delay", oneArgFunction { arg ->
                if (scriptContext == null)
                    throw LuaError("scriptContext == null")

                scriptContext?.delay(arg.checklong())
                NIL
            })

            installAccessibilityEventLib()
            installUtilsLib()
            installKeyEventLib()

            installAutomationLib(applicationContext)

            set("waitFor", varArgFunction { args ->
                if (args.narg() == 1)
                    return@varArgFunction coroutineYield(args.arg1())

                coroutineYield(CoerceJavaToLua.coerce(AllLock(args.toList())))
            })

            set("mediaKeyEvent", twoArgFunction { arg1, arg2 ->
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.dispatchMediaKeyEvent(KeyEvent(arg1.checkint(), arg2.checkint()))
                NIL
            })

            set("perform", oneArgFunction { arg ->
                performGlobalAction(arg.checkint())
                NIL
            })

            set("toast", oneArgFunction { arg ->
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        arg.tojstringuserdata(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                NIL
            })

            load(debugLib)
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

        loadScripts()
    }

    fun loadScripts() = runCatching {
        debugLib.interrupted = true

        scripts.forEach {
            try {
                it.destroy()
                it.resume()
            } catch (e: CustomDebugLib.ScriptInterruptException) {
                e.printStackTrace()
            }
        }

        debugLib.interrupted = false

        scripts.clear()

        val code = PreferenceManager.getDefaultSharedPreferences(this).getString("script", "")

        code!!
            .split("-----\n")
            .forEachIndexed { index, script ->
                scripts.add(
                    Script(
                        name = "script#$index",
                        globals = globals,
                        coroutine = globals.coroutineCreate(
                            globals.load(script, "script#$index")
                        ),
                        onResume = { scriptContext = it },
                        onDeath = { scripts.remove(it) }
                    )
                        .apply {
                            resume()
                        }
                )
            }
    }
        .onFailure {
            notifyError(it)
        }

    private fun notifyError(e: Throwable) {
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
            globals.set("keyEvent", CoerceJavaToLua.coerce(event))

            runCatching {
                scripts.forEach {
                    if (it.notifyKeyEvent(event))
                        ignore = true
                }
            }
                .onFailure {
                    notifyError(it)
                }
        }

        return ignore
    }

    fun notifyAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        globals.set("event", CoerceJavaToLua.coerce(accessibilityEvent))

        runCatching {
            scripts.forEach { it.notifyAccessibilityEvent(accessibilityEvent) }
        }.onFailure {
            notifyError(it)
        }
    }

    companion object {
        var instance: OutlineService? = null
    }
}