package com.example.ui.components

import android.content.Context
import android.webkit.WebView
import android.webkit.WebSettings
import com.example.util.YouTubeNavigationPolicy

/** Progress is read from the trusted main page; no Java object is exposed to web frames. */
class YouTubeWebView(context: Context, private val onPosition: (Int) -> Unit) : WebView(context) {
    private var disposed = false
    private val progress = object : Runnable {
        override fun run() {
            if (disposed || !isAttachedToWindow) return
            if (YouTubeNavigationPolicy.isPlayerOrigin(url)) {
                val requestedPage = url
                evaluateJavascript("(function(){var v=document.querySelector('video');return v?Math.floor(v.currentTime):-1;})()") { value ->
                    if (!disposed && isAttachedToWindow && url == requestedPage && YouTubeNavigationPolicy.isPlayerOrigin(url)) {
                        value.toIntOrNull()?.takeIf { it in 0..604800 }?.let(onPosition)
                    }
                }
            }
            postDelayed(this, 1000)
        }
    }

    init {
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(progress)
        if (!disposed) post(progress)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(progress)
        super.onDetachedFromWindow()
    }

    override fun destroy() {
        disposed = true
        removeCallbacks(progress)
        super.destroy()
    }
}
