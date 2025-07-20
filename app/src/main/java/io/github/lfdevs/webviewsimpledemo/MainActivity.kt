package io.github.lfdevs.webviewsimpledemo

import android.app.Activity
import android.os.Bundle
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat

class MainActivity : ComponentActivity() {
    internal lateinit var webView: WebView
    internal lateinit var myWebChromeClient: MyWebChromeClient
    internal var isDarkMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.insetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(R.layout.activity_main)
        isDarkMode = isSystemInDarkTheme()
        webView = findViewById(R.id.webView)
        initWebView()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initWebView() {
        // 初始化 WebChromeClient
        myWebChromeClient = MyWebChromeClient(this)
        webView.webChromeClient = myWebChromeClient

        // 配置 WebViewSettings
        webView.settings.apply {
            javaScriptEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // 禁用长按菜单
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }

        // 处理跳转外部应用的请求
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith("https://") || url.startsWith("http://") || url.startsWith("about://") -> {
                        view.loadUrl(url)
                        true
                    }

                    else -> {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        true
                    }
                }
            }
        }

        // 配置下载监听
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            try {
                val uri = Uri.parse(url)
                if (uri == null) {
                    throw IllegalArgumentException("Invalid URL: $url")
                }
                val rawFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val safeFileName = rawFileName.replace("[\\\\/<>*?|\":]+".toRegex(), "_")
                val request = DownloadManager.Request(uri).apply {
                    setMimeType(mimeType.takeIf { it.isNotBlank() } ?: "*/*")
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(safeFileName)
                    setDescription("正在下载 $safeFileName")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName)
                }
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = downloadManager.enqueue(request)
                val openDownloadIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(openDownloadIntent)
            } catch (e: Exception) {
                AlertDialog.Builder(this@MainActivity).run {
                    setTitle("下载失败")
                    setMessage("无法下载文件：${e.message}")
                    setPositiveButton("确定", null)
                    show()
                }
            }
        }

        // 应用深色模式设置
        applyDarkModeSettings(webView, isDarkMode)

        // 加载初始页面
        webView.loadUrl(getString(R.string.webview_url))

        // 处理返回键逻辑
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

    }

    // 检测系统是否处于深色模式
    private fun isSystemInDarkTheme(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    // 处理文件选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            val result = if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { arrayOf(it) } ?: arrayOf()
            } else {
                null
            }
            myWebChromeClient.filePathCallback?.onReceiveValue(result)
            myWebChromeClient.filePathCallback = null
        }
    }
}

// 自定义的 WebChromeClient
class MyWebChromeClient(private val activity: Activity) : WebChromeClient() {
    var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onShowFileChooser(
        webView: WebView?,
        callback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        filePathCallback = callback
        val intent = fileChooserParams.createIntent()
        activity.startActivityForResult(intent, 100)
        return true
    }
}

// 深色模式适配
fun applyDarkModeSettings(webView: WebView, isDarkMode: Boolean) {
    webView.settings.forceDark =
        if (isDarkMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
}
