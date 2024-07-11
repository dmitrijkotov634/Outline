package com.wavecat.outline

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import com.wavecat.outline.databinding.ActivityWebviewBinding


class WebViewActivity : AppCompatActivity() {
    private val binding: ActivityWebviewBinding by lazy {
        ActivityWebviewBinding.inflate(
            layoutInflater
        )
    }

    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }
    private val commandReceiver = CommandReceiver()

    class CommandReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mainActivity = context as? WebViewActivity

            when (intent?.action) {
                SEND_CLICK -> {
                    val x = intent.getFloatExtra("x", -1f)
                    val y = intent.getFloatExtra("y", -1f)

                    if (x != -1f && y != -1f)
                        mainActivity?.webviewSendClick(x, y)
                }

                SET_WEBVIEW_SIZE -> {
                    val width = intent.getIntExtra("width", -1)
                    val height = intent.getIntExtra("height", -1)

                    if (width != -1)
                        mainActivity?.setWebViewSize(width, height.takeIf { it != -1 })
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val settings: WebSettings = binding.webview.getSettings()

        settings.apply {
            javaScriptEnabled = true
            allowContentAccess = true
            domStorageEnabled = true
        }

        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webview, true)

        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager

        binding.webview.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    if (manager.isEnabled) {
                        manager.sendAccessibilityEvent(createAccessibilityEvent().apply {
                            setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED)
                            setSource(binding.webview)
                            setPackageName(packageName)
                            text.add(PAGE_LOADED_ANNOUNCEMENT)
                        })
                    }
                }

                super.onProgressChanged(view, newProgress)
            }
        }

        binding.webview.setOnTouchListener { _, event ->
            if (event != null) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val x = event.x
                    val y = event.y

                    binding.debugText.text = "$x, $y"
                }
            }

            return@setOnTouchListener false
        }

        val filter = IntentFilter().apply {
            addAction(SEND_CLICK)
            addAction(SET_WEBVIEW_SIZE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(commandReceiver, filter)
        }

        intent.getStringExtra("url")?.let {
            binding.webview.loadUrl(it)
        }

        binding.webview.postDelayed(
            {
                if (manager.isEnabled) {
                    manager.sendAccessibilityEvent(createAccessibilityEvent().apply {
                        setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED)
                        setSource(binding.webview)
                        setPackageName(packageName)
                        text.add(READY_ANNOUNCEMENT)
                    })
                }
            },
            100
        )
    }

    fun setWebViewSize(newWidth: Int, newHeight: Int?) {
        binding.webview.updateLayoutParams {
            width = newWidth

            if (newHeight != null)
                height = newHeight
        }
    }

    fun webviewSendClick(x: Float, y: Float) {
        val downTime = System.currentTimeMillis()
        val eventTime = System.currentTimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )

        val upEvent = MotionEvent.obtain(
            downTime,
            eventTime + 100,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )

        binding.webview.dispatchTouchEvent(downEvent)
        binding.webview.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    private fun createAccessibilityEvent() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AccessibilityEvent()
    } else {
        @Suppress("DEPRECATION")
        AccessibilityEvent.obtain()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
    }

    companion object {
        const val SEND_CLICK = "com.wavecat.outline.SEND_CLICK"
        const val SET_WEBVIEW_SIZE = "com.wavecat.outline.SET_WEBVIEW_SIZE"

        const val PAGE_LOADED_ANNOUNCEMENT = "pageLoaded"
        const val READY_ANNOUNCEMENT = "ready"
    }
}