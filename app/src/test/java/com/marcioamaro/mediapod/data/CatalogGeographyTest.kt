package com.marcioamaro.mediapod.data

import com.marcioamaro.mediapod.data.repository.CuratedData
import org.junit.Assert.*
import org.junit.Test

class CatalogGeographyTest {
    @Test fun unknownCountryDoesNotShowBrazilianCities() {
        assertTrue(CuratedData.getCitiesForCountry("XX").isEmpty())
        assertTrue(CuratedData.getCitiesForCountry("BR").contains("São Paulo"))
        assertFalse(CuratedData.getCitiesForCountry("PT").contains("São Paulo"))
    }
}
