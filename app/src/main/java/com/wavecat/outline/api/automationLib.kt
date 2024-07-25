package com.wavecat.outline.api

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wavecat.outline.WebViewActivity
import com.wavecat.outline.api.locks.InternalAnnouncement
import com.wavecat.outline.api.locks.filter.PackageNameLock
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

    set("openWebView", oneArgFunction { url ->
        val i = Intent(context, WebViewActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (url.isstring())
            i.putExtra("url", url.checkjstring())

        context.startActivity(i)
        CoerceJavaToLua.coerce(InternalAnnouncement(WebViewActivity.READY_ANNOUNCEMENT))
    })

    set("openUrl", oneArgFunction { url ->
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SEND_COMMAND_ACTION
                putExtra(WebViewActivity.COMMAND_NAME_ARG, WebViewActivity.OPEN_URL_COMMAND)
                putExtra(WebViewActivity.URL_ARG, url.checkjstring())
            }
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("clearWebViewData", zeroArgFunction {
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SEND_COMMAND_ACTION
                putExtra(
                    WebViewActivity.COMMAND_NAME_ARG,
                    WebViewActivity.CLEAR_WEBVIEW_DATA_COMMAND
                )
            }

            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("setWebViewSize", twoArgFunction { width, height ->
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SEND_COMMAND_ACTION
                putExtra(WebViewActivity.COMMAND_NAME_ARG, WebViewActivity.SET_WEBVIEW_SIZE_COMMAND)
                putExtra(WebViewActivity.WIDTH_ARG, width.checkint())
                putExtra(WebViewActivity.HEIGHT_ARG, height.toIntOrNull())
            }
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("webViewClick", twoArgFunction { x, y ->
        runOnUiThread {
            val intent = Intent().apply {
                action = WebViewActivity.SEND_COMMAND_ACTION
                putExtra(WebViewActivity.COMMAND_NAME_ARG, WebViewActivity.WEBVIEW_CLICK_COMMAND)
                putExtra(WebViewActivity.X_ARG, x.checkint().toFloat())
                putExtra(WebViewActivity.Y_ARG, y.checkint().toFloat())
            }
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcast(intent)
        }

        NIL
    })

    set("WebViewReady", zeroArgFunction {
        CoerceJavaToLua.coerce(InternalAnnouncement(WebViewActivity.READY_ANNOUNCEMENT))
    })

    set("PageLoaded", zeroArgFunction {
        CoerceJavaToLua.coerce(InternalAnnouncement(WebViewActivity.PAGE_LOADED_ANNOUNCEMENT))
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