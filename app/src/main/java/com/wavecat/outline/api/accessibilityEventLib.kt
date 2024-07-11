package com.wavecat.outline.api

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import com.wavecat.outline.api.locks.EventTypeFilter
import com.wavecat.outline.api.locks.NodeLock
import com.wavecat.outline.api.locks.PackageNameLock
import com.wavecat.outline.api.locks.Query
import com.wavecat.outline.api.locks.findAllNodes
import com.wavecat.outline.api.locks.findNode
import com.wavecat.outline.utils.oneArgFunction
import com.wavecat.outline.utils.threeArgFunction
import com.wavecat.outline.utils.toBooleanOrNull
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.zeroArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua

private fun eventTypeFunction(eventType: Int) = zeroArgFunction {
    CoerceJavaToLua.coerce(EventTypeFilter(eventType))
}

private fun performActionFunction(action: Int) = oneArgFunction { nodeInfo ->
    (nodeInfo.checkuserdata() as AccessibilityNodeInfo).performAction(action)
    LuaValue.NIL
}

private fun buildNodeQuery(table: LuaValue) = Query(
    viewIdResourceName = table.get("resourceName").optjstring(null),
    className = table.get("className").optjstring(null),
    text = table.get("text").optjstring(null),
    isFocusable = table.get("isFocusable").toBooleanOrNull(),
    isFocused = table.get("isFocused").toBooleanOrNull(),
    containsText = table.get("containsText").optjstring(null),
    contentDescription = table.get("contentDescription").optjstring(null),
    hintText = table.get("hintText").optjstring(null),
)

fun Globals.installAccessibilityEventLib() {
    for (i in AccessibilityEvent::class.java.declaredFields)
        if (i.name.startsWith("TYPE_"))
            set(i.name.substring(5), i.getInt(null))

    set("Node", oneArgFunction { arg -> CoerceJavaToLua.coerce(NodeLock(buildNodeQuery(arg))) })

    set("findRoot", oneArgFunction { event ->
        var root: AccessibilityNodeInfo? = (event.checkuserdata() as AccessibilityEvent).source

        while (root?.parent != null)
            root = root.parent

        CoerceJavaToLua.coerce(root)
    })

    set("findNode", twoArgFunction { nodeInfoArg, query ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        CoerceJavaToLua.coerce(nodeInfo.findNode(buildNodeQuery(query)))
    })

    set("findAllNodes", twoArgFunction { nodeInfoArg, query ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        val results = mutableListOf<AccessibilityNodeInfo>()
        nodeInfo.findAllNodes(buildNodeQuery(query), results)
        CoerceJavaToLua.coerce(results)
    })

    set("iterNodes", threeArgFunction { nodeInfoArg, query, callback ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        val results = mutableListOf<AccessibilityNodeInfo>()
        nodeInfo.findAllNodes(buildNodeQuery(query), results)

        results.forEach {
            callback.call(CoerceJavaToLua.coerce(it))
        }

        LuaValue.NIL
    })

    set("Text", oneArgFunction { arg ->
        CoerceJavaToLua.coerce(NodeLock(Query(text = arg.checkjstring())))
    })

    set("ContainsText", oneArgFunction { arg ->
        CoerceJavaToLua.coerce(NodeLock(Query(containsText = arg.checkjstring())))
    })

    set("PackageName", oneArgFunction { arg ->
        CoerceJavaToLua.coerce(PackageNameLock(arg.checkjstring()))
    })

    set("EventType", oneArgFunction { arg ->
        CoerceJavaToLua.coerce(EventTypeFilter(arg.checkint()))
    })

    set("WindowContentChanged", eventTypeFunction(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
    set("ViewClicked", eventTypeFunction(AccessibilityEvent.TYPE_VIEW_CLICKED))
    set("ViewLongClicked", eventTypeFunction(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED))
    set("ViewFocused", eventTypeFunction(AccessibilityEvent.TYPE_VIEW_FOCUSED))
    set("ViewTextChanged", eventTypeFunction(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
    set("ViewSelected", eventTypeFunction(AccessibilityEvent.TYPE_VIEW_SELECTED))

    set("AccessibilityNodeInfo", CoerceJavaToLua.coerce(AccessibilityNodeInfo::class.java))

    set("click", performActionFunction(AccessibilityNodeInfo.ACTION_CLICK))
    set("longClick", performActionFunction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
    set("cut", performActionFunction(AccessibilityNodeInfo.ACTION_CUT))
    set("paste", performActionFunction(AccessibilityNodeInfo.ACTION_PASTE))
    set("focus", performActionFunction(AccessibilityNodeInfo.ACTION_FOCUS))
    set("clearFocus", performActionFunction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS))
    set("clearSelection", performActionFunction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION))
    set("copy", performActionFunction(AccessibilityNodeInfo.ACTION_COPY))
    set("select", performActionFunction(AccessibilityNodeInfo.ACTION_SELECT))
    set("scrollBackward", performActionFunction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
    set("scrollForward", performActionFunction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))

    set("setText", twoArgFunction { nodeInfoArg, textArg ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        nodeInfo.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, bundleOf(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE to textArg.checkjstring()
            )
        )

        LuaValue.NIL
    })

    set("nextHtmlElement", twoArgFunction { nodeInfoArg, textArg ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        nodeInfo.performAction(
            AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT, bundleOf(
                AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING to textArg.checkjstring()
            )
        )

        LuaValue.NIL
    })

    set("previousHtmlElement", twoArgFunction { nodeInfoArg, textArg ->
        val nodeInfo = nodeInfoArg.checkuserdata() as AccessibilityNodeInfo
        nodeInfo.performAction(
            AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT, bundleOf(
                AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING to textArg.checkjstring()
            )
        )

        LuaValue.NIL
    })
}
