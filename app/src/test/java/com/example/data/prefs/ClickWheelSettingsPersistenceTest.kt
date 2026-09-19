package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickWheelSettingsPersistenceTest {

    private lateinit var context: Context
    private lateinit var repository: ClickWheelPreferencesRepository

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            repository = ClickWheelPreferencesRepository(context.clickWheelDataStore)

            // Reset to clean default state before each test
            context.clickWheelDataStore.edit { it.clear() }
        }
    }

    @Test
    fun `mode preference should persist and restore`() {
        runBlocking {
            // Initial state should be PROGRESSIVE by default
            val initial = repository.clickWheelPreferences.first()
            assertEquals(ClickWheelMode.PROGRESSIVE, initial.mode)

            // Save FIXED mode
            repository.updateClickWheelMode(ClickWheelMode.FIXED)

            // Read and validate persistence
            val restored = repository.clickWheelPreferences.first()
            assertEquals(ClickWheelMode.FIXED, restored.mode)
        }
    }

    @Test
    fun `fixed speed preference should persist and restore`() {
        runBlocking {
            // Initial state should be STANDARD by default
            val initial = repository.clickWheelPreferences.first()
            assertEquals(FixedSpeed.STANDARD, initial.fixedSpeed)

            // Save FAST speed
            repository.updateFixedSpeed(FixedSpeed.FAST)

            // Read and validate persistence
            val restored = repository.clickWheelPreferences.first()
            assertEquals(FixedSpeed.FAST, restored.fixedSpeed)

            // Save SLOW speed
            repository.updateFixedSpeed(FixedSpeed.SLOW)
            val restoredSlow = repository.clickWheelPreferences.first()
            assertEquals(FixedSpeed.SLOW, restoredSlow.fixedSpeed)
        }
    }

    @Test
    fun `invalid persisted value should fallback to PROGRESSIVE`() {
        runBlocking {
            // Corrupt MODE_KEY with an invalid enum string
            context.clickWheelDataStore.edit { prefs ->
                prefs[ClickWheelPreferencesRepository.MODE_KEY] = "UNKNOWN_CORRUPTED_MODE"
                prefs[ClickWheelPreferencesRepository.SPEED_KEY] = "INVALID_SPEED_VALUE"
            }

            // Must safely fallback to safe defaults: PROGRESSIVE and STANDARD
            val restored = repository.clickWheelPreferences.first()
            assertEquals(ClickWheelMode.PROGRESSIVE, restored.mode)
            assertEquals(FixedSpeed.STANDARD, restored.fixedSpeed)
        }
    }
}
