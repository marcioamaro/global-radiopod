package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.DataUsagePolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DataUsagePolicyTest {
    @Test fun preferencesPersistAndInvalidBitrateCannotBeSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policy = DataUsagePolicy(context)
        policy.remoteArtwork = false
        policy.preferredBitrate = 64000
        assertFalse(DataUsagePolicy(context).remoteArtwork)
        assertEquals(64000, DataUsagePolicy(context).preferredBitrate)
        assertThrows(IllegalArgumentException::class.java) { policy.preferredBitrate = -1 }
    }
}
