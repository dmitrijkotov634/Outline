package com.wavecat.outline.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wavecat.outline.api.Script
import com.wavecat.outline.api.installAccessibilityEventLib
import com.wavecat.outline.api.installAutomationLib
import com.wavecat.outline.api.installKeyEventLib
import com.wavecat.outline.api.installUtilsLib
import com.wavecat.outline.api.locks.utils.AllLock
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.CustomDebugLib
import com.wavecat.outline.utils.coroutineCreate
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.oneArgFunction
import com.wavecat.outline.utils.runOnUiThread
import com.wavecat.outline.utils.toList
import com.wavecat.outline.utils.toStringUserdata
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.varArgFunction
import com.wavecat.outline.utils.zeroArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform


class OutlineService : AccessibilityService() {
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

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

            set("getPreferences", zeroArgFunction { CoerceJavaToLua.coerce(preferences) })

            installAccessibilityEventLib()
            installUtilsLib()
            installKeyEventLib()

            installAutomationLib(applicationContext)

            set("waitFor", varArgFunction { args ->
                coroutineYield(
                    when (args.narg()) {
                        0 -> CoerceJavaToLua.coerce(Lock())
                        1 -> args.arg1()
                        else -> CoerceJavaToLua.coerce(AllLock(args.toList()))
                    }
                )
            })

            set("getAccessibilityEvent", zeroArgFunction {
                CoerceJavaToLua.coerce(scriptContext?.latestAccessibilityEvent)
            })

            set("runScriptUnique", twoArgFunction { function, name ->
                val targetName = name.optjstring("")

                if (scripts.find { it.name == targetName } != null)
                    return@twoArgFunction NIL

                CoerceJavaToLua.coerce(runScript(function.checkfunction(), targetName))
            })

            set("runScript", twoArgFunction { function, name ->
                CoerceJavaToLua.coerce(runScript(function.checkfunction(), name.optjstring("")))
            })

            set("findRoot", oneArgFunction { event ->
                if (scriptContext == null)
                    throw LuaError("scriptContext == null")

                var root: AccessibilityNodeInfo? =
                    (if (event.isnil()) scriptContext?.latestAccessibilityEvent else
                        event.checkuserdata() as AccessibilityEvent)?.source

                while (root?.parent != null)
                    root = root.parent

                CoerceJavaToLua.coerce(root)
            })

            set("getKeyEvent", zeroArgFunction {
                CoerceJavaToLua.coerce(scriptContext?.latestKeyEvent)
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
                        arg.toStringUserdata(),
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

        if (preferences.getBoolean(AUTO_START_PREF, true))
            loadScripts()
    }

    fun loadScripts() = runCatching {
        destroyScripts()

        val code = preferences.getString("script", "")

        code!!
            .split("-----\n")
            .forEachIndexed { index, script -> runScript(globals.load(script, "#$index")) }
    }
        .onFailure {
            notifyError(it)
        }

    private fun destroyScripts() = debugLib.apply {
        interrupted = true

        scripts.forEach {
            it.resumeCoroutine(false)
        }

        scripts.clear()

        interrupted = false
    }

    private fun runScript(function: LuaValue, name: String = ""): Script =
        Script(
            name = name.ifEmpty { "#${scripts.size}" },
            globals = globals,
            coroutine = globals.coroutineCreate(function),
            onResume = { scriptContext = it },
            onRecycle = { scripts.remove(it) }
        )
            .apply {
                resume()
                scripts.add(this)
            }

    private fun notifyError(e: Throwable) {
        Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        runCatching {
            scripts.forEach {
                it.onAccessibilityEvent(event)
            }
        }
            .onFailure {
                notifyError(it)
            }
    }

    override fun onInterrupt() {
        instance = null
        destroyScripts()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        var ignore = false

        if (event != null) {
            runCatching {
                scripts.forEach {
                    if (it.onKeyEvent(event))
                        ignore = true
                }
            }
                .onFailure {
                    notifyError(it)
                }
        }

        return ignore
    }

    companion object {
        var instance: OutlineService? = null

        const val AUTO_START_PREF = "auto_start"
    }
}