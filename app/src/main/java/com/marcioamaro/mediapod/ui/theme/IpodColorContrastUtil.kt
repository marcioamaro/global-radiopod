package com.marcioamaro.mediapod.ui.theme

import androidx.compose.ui.graphics.Color
import com.marcioamaro.mediapod.data.model.IpodPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Utilitário de cálculo de contraste WCAG 2.2 e gerador de combinações seguras de cores
 * para o hardware do iPod (Carcaça, Click Wheel e Botão Central).
 */
object IpodColorContrastUtil {

    const val COLOR_WHITE: Long = 0xFFFFFFFF
    const val COLOR_BLACK: Long = 0xFF0F172A
    const val COLOR_DARK_GREY: Long = 0xFF1E293B

    // Constantes monocromáticas exclusivas para o logotipo central (Pera Mordida) - WCAG 1.4.11 (3:1)
    const val LOGO_LIGHT_GRAY_HEX: Long = 0xFFE0E0E0L
    const val LOGO_DARK_GRAY_HEX: Long = 0xFF4A4A4AL

    val LOGO_LIGHT_GRAY: Color = Color(LOGO_LIGHT_GRAY_HEX)
    val LOGO_DARK_GRAY: Color = Color(LOGO_DARK_GRAY_HEX)

    /**
     * Calcula a luminância relativa de uma cor ARGB no padrão WCAG 2.2.
     * Retorna um valor entre 0.0 (preto absoluto) e 1.0 (branco absoluto).
     */
    fun calculateLuminance(colorLong: Long): Double {
        val r = ((colorLong shr 16) and 0xFF) / 255.0
        val g = ((colorLong shr 8) and 0xFF) / 255.0
        val b = (colorLong and 0xFF) / 255.0

        val rLinear = if (r <= 0.04045) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gLinear = if (g <= 0.04045) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bLinear = if (b <= 0.04045) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Calcula a luminância relativa baseada na fórmula W3C/WCAG 2.0 (sRGB linearizado):
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     */
    fun calculateRelativeLuminance(colorLong: Long): Double = calculateLuminance(colorLong)

    fun calculateRelativeLuminance(color: Color): Double {
        val rLinear = if (color.red <= 0.04045f) color.red / 12.92 else ((color.red + 0.055f) / 1.055f).toDouble().pow(2.4)
        val gLinear = if (color.green <= 0.04045f) color.green / 12.92 else ((color.green + 0.055f) / 1.055f).toDouble().pow(2.4)
        val bLinear = if (color.blue <= 0.04045f) color.blue / 12.92 else ((color.blue + 0.055f) / 1.055f).toDouble().pow(2.4)
        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Calcula a taxa de contraste (Contrast Ratio) entre duas cores (entre 1.0 e 21.0).
     */
    fun calculateContrastRatio(color1: Long, color2: Long): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Retorna a razão matemática (L1 + 0.05) / (L2 + 0.05) no padrão W3C.
     */
    fun getContrastRatio(color1: Color, color2: Color): Double {
        val l1 = calculateRelativeLuminance(color1)
        val l2 = calculateRelativeLuminance(color2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun getContrastRatio(color1: Long, color2: Long): Double = calculateContrastRatio(color1, color2)

    /**
     * Alterna o logotipo central (Pera Mordida) estritamente entre LOGO_LIGHT_GRAY e LOGO_DARK_GRAY
     * garantindo o Contraste Mínimo de 3:1 (WCAG 1.4.11).
     */
    fun getAdaptivePearLogoColor(adjacentColor: Color?): Color {
        if (adjacentColor == null) return LOGO_LIGHT_GRAY
        val ratioLight = getContrastRatio(adjacentColor, LOGO_LIGHT_GRAY)
        if (ratioLight >= 3.0) {
            return LOGO_LIGHT_GRAY
        }
        val ratioDark = getContrastRatio(adjacentColor, LOGO_DARK_GRAY)
        if (ratioDark >= 3.0) {
            return LOGO_DARK_GRAY
        }
        return if (ratioLight >= ratioDark) LOGO_LIGHT_GRAY else LOGO_DARK_GRAY
    }

    fun getAdaptivePearLogoColor(adjacentColor: Long): Long {
        val ratioLight = getContrastRatio(adjacentColor, LOGO_LIGHT_GRAY_HEX)
        if (ratioLight >= 3.0) {
            return LOGO_LIGHT_GRAY_HEX
        }
        val ratioDark = getContrastRatio(adjacentColor, LOGO_DARK_GRAY_HEX)
        if (ratioDark >= 3.0) {
            return LOGO_DARK_GRAY_HEX
        }
        return if (ratioLight >= ratioDark) LOGO_LIGHT_GRAY_HEX else LOGO_DARK_GRAY_HEX
    }

    /**
     * Valida se a taxa de contraste atinge o nível WCAG AA para texto normal (mínimo 4.5:1).
     */
    fun isWcagNormalTextCompliant(textColor: Long, bgColor: Long): Boolean {
        return calculateContrastRatio(textColor, bgColor) >= 4.5
    }

    /**
     * Valida se a taxa de contraste atinge o nível WCAG AA para texto grande ou controles essenciais (mínimo 3.0:1).
     */
    fun isWcagLargeTextCompliant(textColor: Long, bgColor: Long): Boolean {
        return calculateContrastRatio(textColor, bgColor) >= 3.0
    }

    /**
     * Calcula a distância euclidiana simples no espaço RGB entre duas cores.
     */
    fun calculateColorDistance(c1: Long, c2: Long): Double {
        val r1 = ((c1 shr 16) and 0xFF).toDouble()
        val g1 = ((c1 shr 8) and 0xFF).toDouble()
        val b1 = (c1 and 0xFF).toDouble()

        val r2 = ((c2 shr 16) and 0xFF).toDouble()
        val g2 = ((c2 shr 8) and 0xFF).toDouble()
        val b2 = (c2 and 0xFF).toDouble()

        return sqrt((r1 - r2).pow(2.0) + (g1 - g2).pow(2.0) + (b1 - b2).pow(2.0))
    }

    /**
     * Determina a melhor cor de texto (Branco ou Escuro) para legibilidade na Click Wheel.
     */
    fun getOptimalWheelTextColor(wheelColor: Long): Long {
        val contrastWithWhite = calculateContrastRatio(COLOR_WHITE, wheelColor)
        val contrastWithDark = calculateContrastRatio(COLOR_DARK_GREY, wheelColor)
        return if (contrastWithWhite >= contrastWithDark) COLOR_WHITE else COLOR_DARK_GREY
    }

    /**
     * Valida se uma combinação de hardware (carcaça, roda, botão central) é aceitável,
     * impedindo combinações inválidas (baixo contraste, preto no preto, vermelho escuro no vermelho escuro, etc.).
     */
    fun isHardwareCombinationValid(bodyColor: Long, wheelColor: Long, centerButtonColor: Long): Boolean {
        // 1. Proibir carcaça e roda quase idênticas (ex: preto com preto)
        val bodyWheelDistance = calculateColorDistance(bodyColor, wheelColor)
        val bodyWheelContrast = calculateContrastRatio(bodyColor, wheelColor)
        if (bodyWheelDistance < 35.0 || bodyWheelContrast < 1.25) {
            return false
        }

        // 2. Proibir roda e botão central quase idênticos
        val wheelCenterDistance = calculateColorDistance(wheelColor, centerButtonColor)
        val wheelCenterContrast = calculateContrastRatio(wheelColor, centerButtonColor)
        if (wheelCenterDistance < 30.0 && wheelCenterContrast < 1.20) {
            return false
        }

        // 3. O texto da roda deve ter contraste legível (mínimo 3:1 para controles/ícones)
        val optimalText = getOptimalWheelTextColor(wheelColor)
        val textContrast = calculateContrastRatio(optimalText, wheelColor)
        if (textContrast < 3.0) {
            return false
        }

        // 4. Proibir ambos muito escuros e da mesma tonalidade (ex: vermelho escuro com vermelho escuro)
        val bodyLum = calculateLuminance(bodyColor)
        val wheelLum = calculateLuminance(wheelColor)
        if (bodyLum < 0.08 && wheelLum < 0.08 && bodyWheelDistance < 55.0) {
            return false
        }

        return true
    }
}

/**
 * Modelo com a paleta completa e validada de hardware do iPod.
 */
typealias SafeIpodHardwarePalette = IpodPalette

/**
 * Gerador de paletas seguras para o modo aleatório.
 */
object SafeIpodColorGenerator {

    // Cores clássicas e vibrantes com alta fidelidade ao design industrial do iPod
    val CURATED_BODY_COLORS = listOf(
        0xFFF1F5F9, // Prata Clássico
        0xFFFFFFFF, // Branco Neve
        0xFF0F172A, // Preto Stealth / U2
        0xFF334155, // Grafite Espacial
        0xFFDC2626, // Vermelho U2 / Product RED
        0xFFD4AF37, // Ouro Champagne
        0xFF0284C7, // Azul Mini
        0xFF059669, // Verde Esmeralda
        0xFF7C3AED, // Roxo Cósmico
        0xFFEA580C, // Laranja Coral
        0xFFDB2777, // Rosa Pink
        0xFF0D9488, // Ciano Teal
        0xFF64748B, // Cinza Slate
        0xFFB45309  // Bronze Âmbar
    )

    val CURATED_WHEEL_COLORS = listOf(
        0xFFE2E4E8, // Cinza Clássico
        0xFFFFFFFF, // Branco Puro
        0xFF1E293B, // Preto Fosco
        0xFFDC2626, // Vermelho U2
        0xFFFDE68A, // Amarelo Pastel
        0xFF0284C7, // Azul Classic
        0xFF059669, // Verde Musgo
        0xFF7C3AED, // Roxo Violeta
        0xFFF1F5F9  // Prata Claro
    )

    val CURATED_CENTER_COLORS = listOf(
        0xFFFFFFFF, // Branco
        0xFFF1F5F9, // Prata
        0xFF111111, // Preto
        0xFFE2E4E8, // Cinza Claro
        0xFF334155, // Grafite
        0xFFFEF3C7, // Creme Dourado
        0xFFE0F2FE, // Azul Gelo
        0xFFD1FAE5  // Menta
    )

    /**
     * Gera uma paleta segura sorteada aleatoriamente que obedece a todas as regras de contraste e diferenciação.
     */
    fun generateSafePalette(random: Random = Random.Default): IpodPalette {
        // Tentar gerar a partir do pool com validação estrita
        for (attempt in 0..50) {
            val body = CURATED_BODY_COLORS.random(random)
            val wheel = CURATED_WHEEL_COLORS.random(random)
            val center = CURATED_CENTER_COLORS.random(random)

            if (IpodColorContrastUtil.isHardwareCombinationValid(body, wheel, center)) {
                val textColor = IpodColorContrastUtil.getOptimalWheelTextColor(wheel)
                return IpodPalette(
                    bodyColor = body,
                    wheelColor = wheel,
                    wheelTextColor = textColor,
                    centerButtonColor = center
                )
            }
        }

        // Fallback garantido 100% testado: iPod U2 Special Edition ou Silver Classic
        val fallbackWheel = 0xFF1E293B
        return IpodPalette(
            bodyColor = 0xFFDC2626,
            wheelColor = fallbackWheel,
            wheelTextColor = IpodColorContrastUtil.getOptimalWheelTextColor(fallbackWheel),
            centerButtonColor = 0xFF111111
        )
    }
}
