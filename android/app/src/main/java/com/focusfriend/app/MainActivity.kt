package com.focusfriend.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader

/** Hosts the Focus Friend page and connects it to Android's Do Not Disturb. */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A fresh start means any earlier session's page is gone, so its Do Not Disturb must end too.
        DndController.restore(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        setContentView(web)
        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // Serves the bundled page from a secure https origin, so storage (settings, your own
        // sounds and pictures) works and is kept between launches.
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            // Only the app's own page loads inside the app; any other link opens in the browser.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: ActivityNotFoundException) { }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFile.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }
        web.addJavascriptInterface(FocusBridge(this), "FFAndroid")
        web.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/focus-friend.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.ffBack ? window.ffBack() : false") { handled ->
                    // Nothing left to close: go to the background, so a running session keeps going.
                    if (handled != "true") moveTaskToBack(true)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        // Coming back from Android's settings, or after the session ended in the background.
        web.evaluateJavascript("window.ffNativeResume && window.ffNativeResume()", null)
    }

    override fun onDestroy() {
        if (isFinishing) DndController.restore(this)
        web.destroy()
        super.onDestroy()
    }

    fun openDndAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: ActivityNotFoundException) { }
        }
    }

    fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
