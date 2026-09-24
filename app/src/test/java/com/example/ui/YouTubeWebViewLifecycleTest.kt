package com.example.ui

import android.app.Activity
import android.webkit.WebSettings
import com.example.ui.components.YouTubeWebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class YouTubeWebViewLifecycleTest {
    @Test fun detachAndDestroyStopPollingWithoutExposingFileAccess() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var callbacks = 0
        val player = YouTubeWebView(activity) { callbacks++ }
        activity.setContentView(player)
        assertFalse(player.settings.allowFileAccess)
        assertFalse(player.settings.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, player.settings.mixedContentMode)
        player.loadUrl("https://www.youtube.com/watch?v=test")
        activity.setContentView(android.widget.FrameLayout(activity))
        player.destroy()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(0, callbacks)
    }
}
