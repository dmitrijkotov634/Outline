package com.wavecat.outline.api

import android.view.KeyEvent
import com.wavecat.outline.api.locks.IgnoreKeyEvent
import com.wavecat.outline.api.locks.WaitKeyLock
import com.wavecat.outline.api.locks.utils.StepsLock
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.toIntOrNull
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.varArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.lib.jse.CoerceJavaToLua

private fun keyClickFunction(count: Int) =
    twoArgFunction { keyCodeArg, intervalArg ->
        val keyCode = keyCodeArg.checkint()
        val interval = intervalArg.optlong(700)
        CoerceJavaToLua.coerce(
            StepsLock(
                locks = buildList {
                    repeat(count) {
                        add(
                            StepsLock(
                                locks = listOf(
                                    WaitKeyLock(keyCode, KeyEvent.ACTION_DOWN),
                                    WaitKeyLock(keyCode, KeyEvent.ACTION_UP)
                                ),
                                interval = interval
                            )
                        )
                    }
                },
                interval = interval
            )
        )
    }

fun Globals.installKeyEventLib() {
    for (i in KeyEvent::class.java.declaredFields)
        if (i.name.startsWith("KEYCODE_"))
            set(i.name.substring(8), i.getInt(null))

    set("DoubleClick", keyClickFunction(2))
    set("TripleClick", keyClickFunction(3))
    set("QuadrupleClick", keyClickFunction(4))

    set("Key", varArgFunction { args ->
        CoerceJavaToLua.coerce(
            WaitKeyLock(
                nextKeyCode = args.arg1().toIntOrNull(),
                nextAction = args.arg(2).toIntOrNull(),
            )
        )
    })

    set("ACTION_UP", KeyEvent.ACTION_UP)
    set("ACTION_DOWN", KeyEvent.ACTION_DOWN)

    set("ignoreKey", varArgFunction {
        coroutineYield(CoerceJavaToLua.coerce(IgnoreKeyEvent()))
    })
}
