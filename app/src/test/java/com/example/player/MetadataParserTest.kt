package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataParserTest {

    @Test
    fun testEmptyOrBlankMetadataReturnsSemInformacoes() {
        val (metadata, validTitle) = MetadataParser.parseIcyForRadio(
            rawStreamTitle = "",
            stationName = "Alpha FM"
        )
        assertEquals("[sem informações]", metadata.artist)
        assertFalse(metadata.hasTrackInfo)
        assertEquals(null, validTitle)

        val (metadataNull, _) = MetadataParser.parseIcyForRadio(
            rawStreamTitle = null,
            stationName = "Alpha FM"
        )
        assertEquals("[sem informações]", metadataNull.artist)
        assertFalse(metadataNull.hasTrackInfo)
    }

    @Test
    fun testStationSelfReferenceReturnsSemInformacoes() {
        val (metadata, _) = MetadataParser.parseIcyForRadio(
            rawStreamTitle = "Alpha FM",
            stationName = "Alpha FM"
        )
        assertEquals("[sem informações]", metadata.artist)
        assertFalse(metadata.hasTrackInfo)
    }

    @Test
    fun testInitialRadioMetadataReturnsSemInformacoes() {
        val metadata = MetadataParser.buildInitialRadioMetadata(
            stationName = "Jovem Pan",
            artworkUri = null
        )
        assertEquals("Jovem Pan", metadata.title)
        assertEquals("[sem informações]", metadata.artist)
        assertFalse(metadata.hasTrackInfo)
    }

    @Test
    fun testValidSongTitleReturnsTrackInfo() {
        val (metadata, validTitle) = MetadataParser.parseIcyForRadio(
            rawStreamTitle = "Coldplay - Yellow",
            stationName = "Antena 1"
        )
        assertEquals("COLDPLAY - YELLOW", metadata.artist)
        assertTrue(metadata.hasTrackInfo)
        assertEquals("COLDPLAY - YELLOW", validTitle)
    }
}
