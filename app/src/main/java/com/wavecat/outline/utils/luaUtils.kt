package com.wavecat.outline.utils

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaNumber
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

fun LuaValue.toIntOrNull(): Int? = if (isint()) toint() else null

fun LuaValue.toBooleanOrNull(): Boolean? = if (isboolean()) toboolean() else null

fun Globals.coroutineResume(coroutine: LuaValue): Varargs = get(COROUTINE)
    .get(RESUME)
    .invoke(coroutine)

fun Globals.coroutineYield(value: LuaValue): Varargs = get(COROUTINE)
    .get(YIELD)
    .call(value)

fun Globals.coroutineCreate(value: LuaValue): LuaValue = get(COROUTINE)
    .get(CREATE)
    .call(value)


fun Globals.coroutineStatus(value: LuaValue): String = get(COROUTINE)
    .get(STATUS)
    .call(value)
    .checkjstring()

fun zeroArgFunction(body: () -> LuaValue) = object : ZeroArgFunction() {
    override fun call(): LuaValue = body()
}

fun oneArgFunction(body: (arg: LuaValue) -> LuaValue) = object : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue = body(arg)
}

fun twoArgFunction(body: (arg1: LuaValue, arg2: LuaValue) -> LuaValue) = object : TwoArgFunction() {
    override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue = body(arg1, arg2)
}

fun threeArgFunction(body: (arg1: LuaValue, arg2: LuaValue, arg3: LuaValue) -> LuaValue) =
    object : ThreeArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue =
            body(arg1, arg2, arg3)
    }

fun varArgFunction(body: (args: Varargs) -> Varargs): LuaValue = object : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs = body(args)
}

fun <T> Varargs.toList() = buildList {
    for (n in 1..narg()) {
        @Suppress("UNCHECKED_CAST")
        add(arg(n).checkuserdata() as T)
    }
}

fun LuaValue.tojstringuserdata() = when (this) {
    is LuaUserdata -> touserdata().toString()
    is LuaNumber -> tolong().toString()
    else -> tojstring()
}

const val COROUTINE = "coroutine"
const val YIELD = "yield"
const val RESUME = "resume"
const val CREATE = "create"
const val STATUS = "status"