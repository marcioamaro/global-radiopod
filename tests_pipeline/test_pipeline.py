import unittest
import os
import json
from radio_catalog_pipeline import (
    remove_accents,
    normalize_station_name,
    normalize_url,
    normalize_text_for_matching
)

class TestRadioCatalogPipeline(unittest.TestCase):

    def test_remove_accents(self):
        self.assertEqual(remove_accents("São Paulo"), "Sao Paulo")
        self.assertEqual(remove_accents("Rádio"), "Radio")
        self.assertEqual(remove_accents("Belém"), "Belem")

    def test_normalize_station_name(self):
        self.assertEqual(normalize_station_name("Rádio Clube FM"), "clube")
        self.assertEqual(normalize_station_name("Antena 1 FM"), "antena 1")
        self.assertEqual(normalize_station_name("Web Rádio Fraternidade"), "fraternidade")

    def test_normalize_url(self):
        url1 = "http://stream.example.com:80/live.mp3#t=10"
        self.assertEqual(normalize_url(url1), "http://stream.example.com/live.mp3")

        url2 = "https://STREAM.EXAMPLE.COM:443/audio?token=xyz"
        self.assertEqual(normalize_url(url2), "https://stream.example.com/audio?token=xyz")

    def test_city_normalization_and_distinct_cities(self):
        araras_key = normalize_text_for_matching("Araras")
        limeira_key = normalize_text_for_matching("Limeira")
        sp_key = normalize_text_for_matching("São Paulo")

        self.assertEqual(araras_key, "araras")
        self.assertEqual(limeira_key, "limeira")
        self.assertEqual(sp_key, "sao paulo")

        # Emissoras com o mesmo nome em cidades diferentes não geram a mesma chave composta
        radio_name = normalize_station_name("Rádio Clube FM")
        key_araras = f"{radio_name}::{araras_key}::SP"
        key_limeira = f"{radio_name}::{limeira_key}::SP"

        self.assertNotEqual(key_araras, key_limeira)

    def test_escape_kt_string(self):
        from radio_catalog_pipeline import escape_kt_string
        raw = 'Rádio "Top" & $100 \\ test\nnewline'
        escaped = escape_kt_string(raw)
        self.assertNotIn('\n', escaped)
        self.assertIn('\\"', escaped)
        self.assertIn('\\$', escaped)

    def test_clean_surrogates(self):
        from radio_catalog_pipeline import clean_surrogates
        sample_dict = {"name": "Rádio Araras", "tags": ["pop", "sertanejo"]}
        cleaned = clean_surrogates(sample_dict)
        self.assertEqual(cleaned["name"], "Rádio Araras")
        self.assertEqual(cleaned["tags"], ["pop", "sertanejo"])

if __name__ == "__main__":
    unittest.main()
