package com.wavecat.outline.api

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.wavecat.outline.WebViewActivity
import com.wavecat.outline.api.locks.InternalAnnouncementLock
import com.wavecat.outline.api.locks.PackageNameLock
import com.wavecat.outline.api.locks.Query
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.oneArgFunction
import com.wavecat.outline.utils.runOnUiThread
import com.wavecat.outline.utils.toIntOrNull
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.zeroArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.jse.CoerceJavaToLua


fun Globals.installAutomationLib(context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    set("openInChrome", oneArgFunction { url ->
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url.checkjstring()))

        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setPackage("com.android.chrome")

        try {
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            i.setPackage(null)
            context.startActivity(i)
        }

        NIL
    })

    set("openInWebView", oneArgFunction { url ->
        val i = Intent(context, WebViewActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.putExtra("url", url.checkjstring())
        context.startActivity(i)
        CoerceJavaToLua.coerce(InternalAnnouncementLock(WebViewActivity.READY_ANNOUNCEMENT))
    })

    set("webViewSetSize", twoArgFunction { width, height ->
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SET_WEBVIEW_SIZE
                putExtra("width", width.checkint())
                putExtra("height", height.toIntOrNull())
            }
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("webViewClick", twoArgFunction { x, y ->
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SEND_CLICK
                putExtra("x", x.checkint().toFloat())
                putExtra("y", y.checkint().toFloat())
            }
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("runDebugLogger", oneArgFunction { packageName ->
        coroutineYield(CoerceJavaToLua.coerce(object : Lock() {
            override fun tryUnlock(accessibilityEvent: AccessibilityEvent): Boolean {
                if (accessibilityEvent.packageName != packageName.optjstring(context.packageName))
                    return false

                if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    accessibilityEvent.source?.apply {
                        val query = Query(
                            viewIdResourceName = viewIdResourceName,
                            className = className,
                            text = text,
                            isFocusable = isFocusable,
                            isFocused = isFocused,
                            contentDescription = contentDescription,
                            hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) hintText else ""
                        )

                        runOnUiThread {
                            Toast.makeText(context, query.toString(), Toast.LENGTH_SHORT).show()

                            clipboardManager.setPrimaryClip(
                                ClipData.newHtmlText(
                                    "outline",
                                    query.toString(),
                                    query.toString()
                                )
                            )
                        }
                    }
                }

                return false
            }
        }))

        NIL
    })

    set("WebViewReady", zeroArgFunction {
        CoerceJavaToLua.coerce(InternalAnnouncementLock(WebViewActivity.READY_ANNOUNCEMENT))
    })

    set("PageLoaded", zeroArgFunction {
        CoerceJavaToLua.coerce(InternalAnnouncementLock(WebViewActivity.PAGE_LOADED_ANNOUNCEMENT))
    })

    set("IsChrome", zeroArgFunction {
        CoerceJavaToLua.coerce(PackageNameLock("com.android.chrome"))
    })

    set("clip", oneArgFunction { text ->
        clipboardManager.setPrimaryClip(
            ClipData.newHtmlText(
                "outline",
                text.checkjstring(),
                text.checkjstring()
            )
        )

        NIL
    })
}