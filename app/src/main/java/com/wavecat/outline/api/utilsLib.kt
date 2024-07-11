package com.wavecat.outline.api

import com.wavecat.outline.api.locks.utils.AllLock
import com.wavecat.outline.api.locks.utils.AnyLock
import com.wavecat.outline.api.locks.utils.CombinedLock
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.api.locks.utils.StepsLock
import com.wavecat.outline.utils.toList
import com.wavecat.outline.utils.varArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.jse.CoerceJavaToLua

fun Globals.installUtilsLib() {
    set("Steps", varArgFunction { args ->
        CoerceJavaToLua.coerce(
            StepsLock(
                locks = buildList {
                    for (n in 1 until args.narg()) {
                        add(args.arg(n).checkuserdata() as Lock)
                    }
                },
                interval = args.arg(args.narg()).tolong()
            )
        )
    })

    set("Combined", varArgFunction { args ->
        CoerceJavaToLua.coerce(
            CombinedLock(
                locks = buildList {
                    for (n in 1 until args.narg()) {
                        add(args.arg(n).checkuserdata() as Lock)
                    }
                },
                interval = args.arg(args.narg()).tolong()
            )
        )
    })

    set("Any", varArgFunction { args -> CoerceJavaToLua.coerce(AnyLock(args.toList())) })
    set("All", varArgFunction { args -> CoerceJavaToLua.coerce(AllLock(args.toList())) })
}