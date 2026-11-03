package com.hvac.eyecare

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val BG = 0xFFF2EFE3.toInt()
        private const val CARD = 0xFFFFFDF6.toInt()
        private const val INK = 0xFF26332C.toInt()
        private const val MUTED = 0xFF5F6F68.toInt()
        private const val ACCENT = 0xFF0D8F7A.toInt()
        private const val ACCENT_DARK = 0xFF0B6F5F.toInt()
        private const val REQ_LOCATION = 7101
        private const val APP_URL = "https://appassets.androidplatform.net/index.html"
        private const val ORIGINAL_URL =
            "https://advisory.cdn.bcebos.com/online/2026-11-03/547b4/hvac-eyecare.html"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loading: LinearLayout
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorMessage: TextView
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply { setBackgroundColor(BG) }

        webView = WebView(this).apply {
            setBackgroundColor(BG)
            alpha = 0f
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setGeolocationEnabled(true)
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                textZoom = 100
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loading = buildLoading()
        root.addView(
            loading,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        errorPanel = buildErrorPanel()
        errorPanel.visibility = View.GONE
        val errorParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        }
        root.addView(errorPanel, errorParams)

        setContentView(root)
        configureWindow()
        applySystemBarInsets()
        installWebClients()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            revealPage()
        } else {
            loadApp()
        }
    }

    private fun configureWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) Color.parseColor("#0B6F5F") else Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            try {
                window.decorView.windowInsetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } catch (_: Exception) {
                // Some OEM skins expose the controller only after the first layout pass.
            }
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            root.setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
                insets
            }
        }
        root.requestApplyInsets()
    }

    private fun installWebClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "https" && uri.host == "appassets.androidplatform.net") {
                    return false
                }
                if (scheme == "about" || scheme == "data" ||
                    scheme == "blob" || scheme == "javascript"
                ) {
                    return false
                }
                return openExternally(uri)
            }

            override fun onPageFinished(view: WebView, url: String) {
                revealPage()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showError("页面载入失败，请检查网络连接后重试。")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showError("页面返回异常（HTTP ${errorResponse.statusCode}），请稍后重试。")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                geoCallback = callback
                geoOrigin = origin
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                    geoCallback = null
                    geoOrigin = null
                } else {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        REQ_LOCATION
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun loadApp() {
        webView.visibility = View.VISIBLE
        webView.alpha = 0f
        errorPanel.visibility = View.GONE
        loading.visibility = View.VISIBLE
        loading.alpha = 1f
        try {
            val html = assets.open("index.html")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            webView.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            showError("内置页面读取失败：${e.message ?: "未知错误"}")
        }
    }

    private fun revealPage() {
        loading.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction { loading.visibility = View.GONE }
            .start()
        webView.visibility = View.VISIBLE
        webView.animate().alpha(1f).setDuration(280).start()
    }

    private fun showError(message: String) {
        loading.visibility = View.GONE
        webView.visibility = View.INVISIBLE
        errorMessage.text = message
        errorPanel.visibility = View.VISIBLE
    }

    private fun openExternally(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun buildLoading(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            addView(
                ProgressBar(this@MainActivity).apply {
                    indeterminateTintList = ColorStateList.valueOf(ACCENT)
                },
                LinearLayout.LayoutParams(dp(32), dp(32))
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "正在载入诊断台"
                    textSize = 13f
                    setTextColor(MUTED)
                    letterSpacing = 0.12f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(14) }
            )
        }
    }

    private fun buildErrorPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(22))
            background = rounded(CARD, 18, 0x29546058)

            addView(
                TextView(this@MainActivity).apply {
                    text = "页面没有载入成功"
                    textSize = 17f
                    setTextColor(INK)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            )

            errorMessage = TextView(this@MainActivity).apply {
                text = "请检查网络连接后重试。"
                textSize = 13.5f
                setTextColor(MUTED)
                setLineSpacing(dp(4).toFloat(), 1f)
            }
            addView(
                errorMessage,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            )

            val actions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val retry = actionButton("重新载入", true).apply {
                setOnClickListener { loadApp() }
            }
            val browser = actionButton("用浏览器打开", false).apply {
                setOnClickListener { openExternally(Uri.parse(ORIGINAL_URL)) }
            }
            actions.addView(retry)
            actions.addView(
                browser,
                LinearLayout.LayoutParams(0, dp(44), 1f)
            )
            addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(18) }
            )
        }
    }

    private fun actionButton(label: String, primary: Boolean): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(if (primary) ACCENT_DARK else MUTED)
            background = rounded(
                if (primary) 0x1F0D8F7A else 0x0F545F58,
                11,
                if (primary) 0x4D0D8F7A else 0x2E545F58
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(10)
            }
        }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (stroke != null) {
                setStroke(dp(1), stroke)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            val granted = hasLocationPermission()
            val callback = geoCallback
            val origin = geoOrigin ?: APP_URL
            geoCallback = null
            geoOrigin = null
            callback?.invoke(origin, granted, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    @Deprecated("Handled for devices without predictive back support")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}