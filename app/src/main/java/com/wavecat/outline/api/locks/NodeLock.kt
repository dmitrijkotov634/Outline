package com.wavecat.outline.api.locks

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wavecat.outline.api.locks.utils.Lock

fun AccessibilityNodeInfo.findAllNodes(query: Query, results: MutableList<AccessibilityNodeInfo>) {
    repeat(childCount) { index ->
        val node = getChild(index) ?: return@repeat

        if (node.childCount != 0)
            node.findAllNodes(query, results)

        if (!node.matchesQuery(query))
            return@repeat

        results.add(node)
    }
}

fun AccessibilityNodeInfo.findNode(query: Query): AccessibilityNodeInfo? {
    repeat(childCount) { index ->
        val node = getChild(index) ?: return@repeat

        if (node.childCount != 0) {
            val result = node.findNode(query)

            if (result != null)
                return result
        }

        if (!node.matchesQuery(query))
            return@repeat

        return node
    }

    return null
}

fun AccessibilityNodeInfo.matchesQuery(query: Query): Boolean {
    if (query.contentDescription != null && contentDescription != query.contentDescription) return false
    if (query.viewIdResourceName != null && viewIdResourceName != query.viewIdResourceName) return false
    if (query.className != null && className != query.className) return false
    if (query.isFocused != null && isFocused != query.isFocused) return false
    if (query.isFocusable != null && isFocusable != query.isFocusable) return false
    if (query.text != null && text != query.text) return false
    if (query.containsText != null && (text == null || !text.contains(query.containsText))) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (query.hintText != null && hintText != query.hintText
        ) return false
    }

    return true
}

class NodeLock(private val query: Query) : Lock() {
    override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean =
        accessibilityEvent.source?.findNode(query) != null
}

data class Query(
    val className: CharSequence? = null,
    val viewIdResourceName: String? = null,
    val contentDescription: CharSequence? = null,
    val isFocused: Boolean? = null,
    val isFocusable: Boolean? = null,
    val text: CharSequence? = null,
    val hintText: CharSequence? = null,
    val containsText: String? = null,
)