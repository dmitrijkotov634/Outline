package com.wavecat.outline.utils

import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.DebugLib

class CustomDebugLib : DebugLib() {
    var interrupted = false

    override fun onInstruction(pc: Int, v: Varargs, top: Int) {
        if (interrupted)
            throw ScriptInterruptException("interrupted")

        super.onInstruction(pc, v, top)
    }

    class ScriptInterruptException(s: String) : RuntimeException(s)
}