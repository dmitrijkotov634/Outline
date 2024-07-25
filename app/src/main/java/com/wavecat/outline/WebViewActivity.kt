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
import android.webkit.WebStorage
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

            if (intent?.action != SEND_COMMAND_ACTION)
                return

            when (intent.getStringExtra(COMMAND_NAME_ARG)) {
                WEBVIEW_CLICK_COMMAND -> {
                    val x = intent.getFloatExtra(X_ARG, -1f)
                    val y = intent.getFloatExtra(Y_ARG, -1f)

                    if (x != -1f && y != -1f)
                        mainActivity?.webviewSendClick(x, y)
                }

                SET_WEBVIEW_SIZE_COMMAND -> {
                    val width = intent.getIntExtra(WIDTH_ARG, -1)
                    val height = intent.getIntExtra(HEIGHT_ARG, -1)

                    if (width != -1)
                        mainActivity?.setWebViewSize(width, height.takeIf { it != -1 })
                }

                CLEAR_WEBVIEW_DATA_COMMAND -> {
                    mainActivity?.clearWebViewData()
                }

                OPEN_URL_COMMAND -> {
                    intent.getStringExtra(URL_ARG)?.let {
                        mainActivity?.openUrl(it)
                        mainActivity?.binding?.debugText?.let { it1 ->
                            it1.text = it
                        }
                    }
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
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
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
            addAction(SEND_COMMAND_ACTION)
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

    fun clearWebViewData() {
        WebStorage.getInstance().deleteAllData()

        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        binding.webview.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            clearMatches()
            clearSslPreferences()
        }

        deleteDatabase("webview.db")
        deleteDatabase("webviewCache.db")

        cacheDir.deleteRecursively()
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

    fun openUrl(url: String) {
        binding.webview.loadUrl(url)
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
        const val SEND_COMMAND_ACTION = "com.wavecat.outline.SEND_COMMAND"

        const val COMMAND_NAME_ARG = "command_name"
        const val URL_ARG = "url"
        const val WIDTH_ARG = "width"
        const val HEIGHT_ARG = "height"
        const val X_ARG = "x"
        const val Y_ARG = "y"

        const val WEBVIEW_CLICK_COMMAND = "WEBVIEW_CLICK"
        const val SET_WEBVIEW_SIZE_COMMAND = "SET_WEBVIEW_SIZE"
        const val CLEAR_WEBVIEW_DATA_COMMAND = "CLEAR_WEBVIEW_DATA"
        const val OPEN_URL_COMMAND = "OPEN_URL"

        const val PAGE_LOADED_ANNOUNCEMENT = "PAGE_LOADED"
        const val READY_ANNOUNCEMENT = "READY"
    }
}