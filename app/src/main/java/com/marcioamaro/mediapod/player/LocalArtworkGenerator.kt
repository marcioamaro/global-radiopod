package com.marcioamaro.mediapod.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Gerador de artwork local via Canvas.
 *
 * Para Android Auto, o head unit exige que KEY_ALBUM_ART_URI aponte para um Uri
 * acessível localmente — URIs de rede causam timeout no painel veicular.
 *
 * Esta classe gera bitmaps em memória com as iniciais e cor associada à estação,
 * os persiste no cache interno do app, e retorna um Uri `file://` estável.
 *
 * ## Design
 * - Fundo: cor sólida derivada deterministicamente do nome da estação (sempre consistente)
 * - Texto: iniciais em branco, centralizadas, com fonte bold
 * - Tamanho: 512×512px (compatível com Auto, notificação e Cast)
 * - Cache: `cacheDir/artwork/<hash>.png` — compartilhado entre sessões
 *
 * ## Thread Safety
 * [getOrCreate] pode ser chamado da Main thread ou IO thread.
 * A escrita de arquivo é rápida (~1ms) e pode ser feita em qualquer thread.
 */
object LocalArtworkGenerator {

    private const val SIZE_PX = 512
    private const val CACHE_DIR = "artwork"

    // Paleta curada de cores vibrantes para backgrounds
    private val BRAND_COLORS = listOf(
        0xFF1565C0.toInt(), // Deep Blue
        0xFF2E7D32.toInt(), // Deep Green
        0xFFAD1457.toInt(), // Deep Pink
        0xFF6A1B9A.toInt(), // Deep Purple
        0xFFE65100.toInt(), // Deep Orange
        0xFF00695C.toInt(), // Teal
        0xFF283593.toInt(), // Indigo
        0xFF558B2F.toInt(), // Light Green Dark
        0xFF4E342E.toInt(), // Brown
        0xFF00838F.toInt(), // Cyan Dark
        0xFFD81B60.toInt(), // Pink
        0xFF1976D2.toInt(), // Blue
    )

    /**
     * Retorna o Uri do artwork para [stationName].
     * Se um arquivo de cache já existir, retorna-o imediatamente (zero geração).
     * Caso contrário, gera o bitmap, salva e retorna o Uri.
     *
     * @param context Contexto da aplicação
     * @param stationName Nome da estação (ex: "Rádio Globo")
     * @return Uri `file://` do artwork, ou null em caso de falha de I/O
     */
    fun getOrCreate(context: Context, stationName: String): Uri? {
        return getDefaultRadioArtwork(context)
    }

    /**
     * Retorna o Uri do artwork padrão com o logotipo oficial retrô de rádio (ic_radio_retro).
     * Usa o schema android.resource:// para acesso direto e instantâneo no Android Auto e notificações,
     * garantindo renderização nítida em todas as resoluções sem restrições de permissão de arquivo.
     */
    fun getDefaultRadioArtwork(context: Context): Uri? {
        return try {
            Uri.parse("android.resource://${context.packageName}/${com.marcioamaro.mediapod.R.drawable.ic_radio_generic}")
        } catch (e: Exception) {
            android.util.Log.w("LocalArtworkGenerator", "Falha ao gerar artwork padrão de rádio", e)
            null
        }
    }

    private fun generateDefaultRadioBitmap(context: Context): Bitmap {
        val decoded = try {
            BitmapFactory.decodeResource(context.resources, com.marcioamaro.mediapod.R.drawable.ic_radio_generic)
        } catch (_: Exception) { null }
        if (decoded != null) return decoded

        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.marcioamaro.mediapod.R.drawable.ic_radio_generic)
            drawable?.let {
                val iconPadding = (SIZE_PX * 0.10f).toInt()
                it.setBounds(iconPadding, iconPadding, SIZE_PX - iconPadding, SIZE_PX - iconPadding)
                androidx.core.graphics.drawable.DrawableCompat.setTint(it, Color.WHITE)
                it.draw(canvas)
            }
        } catch (e: Exception) {
            android.util.Log.w("LocalArtworkGenerator", "Falha ao desenhar ic_radio_generic no bitmap", e)
        }

        return bitmap
    }

    /**
     * Limpa todos os artworks em cache.
     * Útil em rotinas de manutenção de espaço em disco.
     */
    fun clearCache(context: Context) {
        try {
            val dir = File(context.cacheDir, CACHE_DIR)
            dir.deleteRecursively()
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private fun getCacheFile(context: Context, stationName: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        val hash = stationName.trim().lowercase().hashCode().and(0x7FFFFFFF).toString(16)
        return File(dir, "$hash.png")
    }

    private fun generateAndSave(stationName: String, outputFile: File) {
        val bitmap = generate(stationName)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()
    }

    /**
     * Gera o bitmap em memória.
     * Retorna um Bitmap não reciclado — o chamador deve reciclar após uso ou
     * deixar o GC tratar (arquivos em disco são a fonte primária de cache).
     */
    fun generate(stationName: String): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── 1. Fundo colorido ────────────────────────────────────────────────
        val bgColor = pickColor(stationName)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat(), bgPaint)

        // ── 2. Círculo levemente mais claro no centro ────────────────────────
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lighten(bgColor, 0.15f)
            style = Paint.Style.FILL
        }
        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        canvas.drawCircle(cx, cy, SIZE_PX * 0.42f, circlePaint)

        // ── 3. Iniciais em branco ─────────────────────────────────────────────
        val initials = extractInitials(stationName)
        val fontSize = if (initials.length == 1) SIZE_PX * 0.52f else SIZE_PX * 0.38f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        // Centraliza verticalmente usando o bounds do texto
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        val textY = cy + (textBounds.height() / 2f)
        canvas.drawText(initials, cx, textY, textPaint)

        return bitmap
    }

    /**
     * Extrai até 2 iniciais do nome da estação.
     * Ex: "Rádio Globo" → "RG", "CBN" → "CB", "98 FM" → "9F"
     */
    private fun extractInitials(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9À-ÿ\\s]"), " ")
            .trim()
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }

    /**
     * Seleciona uma cor da paleta [BRAND_COLORS] deterministicamente a partir do nome.
     * O mesmo nome sempre produz a mesma cor.
     */
    private fun pickColor(stationName: String): Int {
        val index = abs(stationName.trim().lowercase().hashCode()) % BRAND_COLORS.size
        return BRAND_COLORS[index]
    }

    /**
     * Clareia uma cor ARGB pelo fator [amount] (0.0 = sem mudança, 1.0 = branco).
     */
    private fun lighten(color: Int, amount: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val f = amount.coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            (r + ((255 - r) * f)).toInt().coerceIn(0, 255),
            (g + ((255 - g) * f)).toInt().coerceIn(0, 255),
            (b + ((255 - b) * f)).toInt().coerceIn(0, 255)
        )
    }
}
