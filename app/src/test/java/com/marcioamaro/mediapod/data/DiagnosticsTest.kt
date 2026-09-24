package com.marcioamaro.mediapod.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.util.Diagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DiagnosticsTest {
    @Test fun reportWhitelistsFieldsAndBoundsHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit()
            .putString("events", """[{"event":"PLAYBACK_FAILED","time":1,"url":"https://private/?token=secret"}]""").commit()
        assertFalse(Diagnostics.report(context).contains("secret"))
        repeat(70) { Diagnostics.record(context, Diagnostics.Event.DOWNLOAD_FAILED) }
        assertEquals(50, JSONObject(Diagnostics.report(context)).getJSONArray("events").length())
        Diagnostics.clear(context)
        assertEquals(0, JSONObject(Diagnostics.report(context)).getJSONArray("events").length())
    }
}
