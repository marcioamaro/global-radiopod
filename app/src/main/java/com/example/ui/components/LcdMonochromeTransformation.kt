package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil.size.Size
import coil.transform.Transformation

/**
 * Coil Transformation que converte logotipos coloridos de emissoras em arte pixelada
 * monocromática estilo LCD retrô, idêntica aos displays físicos de rádio (matriz de pontos).
 *
 * Utiliza ponderação de luminância Rec. 709 e algoritmo de pontilhamento Bayer 4x4 adaptativo,
 * mapeando os pixels para as cores ativas de luz de fundo e cristal líquido do tema iPod.
 */
class LcdMonochromeTransformation(
    private val darkColor: Color,
    private val lightColor: Color? = null,
    private val dither: Boolean = true,
    private val targetResolution: Int = 128
) : Transformation {

    private val darkArgb: Int = darkColor.toArgb()
    private val lightArgb: Int = lightColor?.toArgb() ?: android.graphics.Color.TRANSPARENT

    override val cacheKey: String = "lcd_mono_${darkArgb}_${lightArgb}_${dither}_${targetResolution}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height

        // Determina resolução alvo para o grid de pontos do LCD (padrão 128x128 para nitidez ideal no badge de 68dp)
        val scale = if (width > targetResolution || height > targetResolution) {
            val maxDim = maxOf(width, height)
            targetResolution.toFloat() / maxDim.toFloat()
        } else {
            1.0f
        }

        val targetW = maxOf(16, (width * scale).toInt())
        val targetH = maxOf(16, (height * scale).toInt())

        val scaledBitmap = if (targetW != width || targetH != height) {
            Bitmap.createScaledBitmap(input, targetW, targetH, true)
        } else {
            input
        }

        val w = scaledBitmap.width
        val h = scaledBitmap.height
        val pixels = IntArray(w * h)
        scaledBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Matriz de dithering ordenado Bayer 4x4 normalizada (-0.5 a +0.5)
        val bayerMatrix = floatArrayOf(
            0f / 16f - 0.5f,  8f / 16f - 0.5f,  2f / 16f - 0.5f, 10f / 16f - 0.5f,
            12f / 16f - 0.5f,  4f / 16f - 0.5f, 14f / 16f - 0.5f,  6f / 16f - 0.5f,
            3f / 16f - 0.5f, 11f / 16f - 0.5f,  1f / 16f - 0.5f,  9f / 16f - 0.5f,
            15f / 16f - 0.5f,  7f / 16f - 0.5f, 13f / 16f - 0.5f,  5f / 16f - 0.5f
        )

        for (y in 0 until h) {
            val rowOffset = y * w
            val bayerRow = (y % 4) * 4
            for (x in 0 until w) {
                val idx = rowOffset + x
                val pixel = pixels[idx]
                val alpha = (pixel ushr 24) and 0xFF

                if (alpha < 40) {
                    pixels[idx] = lightArgb
                    continue
                }

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                // Luminância ponderada perceptual Rec. 709
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255.0f
                val bayerOffset = if (dither) bayerMatrix[bayerRow + (x % 4)] * 0.28f else 0f
                val isDark = (lum + bayerOffset) < 0.62f

                pixels[idx] = if (isDark) darkArgb else lightArgb
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        if (scaledBitmap !== input) {
            scaledBitmap.recycle()
        }

        return resultBitmap
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LcdMonochromeTransformation) return false
        return darkArgb == other.darkArgb &&
                lightArgb == other.lightArgb &&
                dither == other.dither &&
                targetResolution == other.targetResolution
    }

    override fun hashCode(): Int {
        var result = darkArgb
        result = 31 * result + lightArgb
        result = 31 * result + dither.hashCode()
        result = 31 * result + targetResolution
        return result
    }
}
