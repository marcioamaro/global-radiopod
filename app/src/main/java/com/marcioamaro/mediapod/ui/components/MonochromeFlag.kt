package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Componente vetorial monocromático para bandeiras e insígnias heráldicas
 * de estados brasileiros e países.
 *
 * Desenhado com linhas geométricas nítidas e preenchimento vazado,
 * adaptando-se perfeitamente à cor [tint] do display LCD ou tema ativo.
 */
@Composable
fun MonochromeFlag(
    code: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val cleanCode = code.trim().uppercase().replace("[", "").replace("]", "")

    Box(
        modifier = modifier
            .size(width = size * 1.4f, height = size)
            .clip(RoundedCornerShape(2.dp))
            .border(0.8.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = 1.dp.toPx()

            when (cleanCode) {
                // --- BRASIL & ESTADOS ---
                "BR" -> drawBrazil(tint, strokeWidth, w, h)
                "SP" -> drawSaoPaulo(tint, strokeWidth, w, h)
                "RJ" -> drawRioDeJaneiro(tint, strokeWidth, w, h)
                "MG" -> drawMinasGerais(tint, strokeWidth, w, h)
                "RS" -> drawRioGrandeDoSul(tint, strokeWidth, w, h)
                "PR" -> drawParana(tint, strokeWidth, w, h)
                "SC" -> drawSantaCatarina(tint, strokeWidth, w, h)
                "BA" -> drawBahia(tint, strokeWidth, w, h)
                "PE" -> drawPernambuco(tint, strokeWidth, w, h)
                "CE" -> drawCeara(tint, strokeWidth, w, h)
                "GO" -> drawGoias(tint, strokeWidth, w, h)
                "DF" -> drawDistritoFederal(tint, strokeWidth, w, h)
                "ES" -> drawEspiritoSanto(tint, strokeWidth, w, h)
                "RN" -> drawRioGrandeDoNorte(tint, strokeWidth, w, h)
                "PB" -> drawParaiba(tint, strokeWidth, w, h)
                "MA" -> drawMaranhao(tint, strokeWidth, w, h)
                "MT", "MS" -> drawMatoGrosso(tint, strokeWidth, w, h)
                "PA", "AM", "AP", "AC", "RO", "RR", "TO" -> drawAmazonRegion(tint, strokeWidth, w, h)
                "PI", "AL", "SE" -> drawNordeste(tint, strokeWidth, w, h)

                // --- PAÍSES INTERNACIONAIS ---
                "US" -> drawUnitedStates(tint, strokeWidth, w, h)
                "GB" -> drawUnitedKingdom(tint, strokeWidth, w, h)
                "PT" -> drawPortugal(tint, strokeWidth, w, h)
                "DE", "NL" -> drawHorizontalStripes(tint, strokeWidth, w, h, 3)
                "FR", "IT", "IE" -> drawVerticalStripes(tint, strokeWidth, w, h, 3)
                "ES" -> drawSpain(tint, strokeWidth, w, h)
                "JP" -> drawJapan(tint, strokeWidth, w, h)
                "AR" -> drawArgentina(tint, strokeWidth, w, h)
                "CA" -> drawCanada(tint, strokeWidth, w, h)
                "AU" -> drawAustralia(tint, strokeWidth, w, h)
                "CH" -> drawSwitzerland(tint, strokeWidth, w, h)

                // Padrão Global / Fallback
                else -> drawGlobeIcon(tint, strokeWidth, w, h)
            }
        }
    }
}

// Brasil: Losango central com círculo
private fun DrawScope.drawBrazil(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w / 2f, h * 0.15f)
        lineTo(w * 0.85f, h / 2f)
        lineTo(w / 2f, h * 0.85f)
        lineTo(w * 0.15f, h / 2f)
        close()
    }
    drawPath(path, tint, style = Stroke(strokeWidth))
    drawCircle(tint, radius = h * 0.22f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// São Paulo: Listras horizontais com cantão retangular
private fun DrawScope.drawSaoPaulo(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val step = h / 5f
    for (i in 1..4) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth * 0.75f)
    }
    drawRect(tint, Offset(0f, 0f), Size(w * 0.38f, h * 0.45f), style = Stroke(strokeWidth))
    drawCircle(tint, radius = h * 0.12f, center = Offset(w * 0.19f, h * 0.225f), style = Stroke(strokeWidth * 0.8f))
}

// Rio de Janeiro: Brasão com escudo e âncoras/cruz
private fun DrawScope.drawRioDeJaneiro(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, 0f), Offset(w, h), strokeWidth = strokeWidth * 0.8f)
    drawLine(tint, Offset(0f, h), Offset(w, 0f), strokeWidth = strokeWidth * 0.8f)
    drawCircle(tint, radius = h * 0.24f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Minas Gerais: Triângulo central característico (Libertas Quae Sera Tamen)
private fun DrawScope.drawMinasGerais(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w / 2f, h * 0.2f)
        lineTo(w * 0.8f, h * 0.8f)
        lineTo(w * 0.2f, h * 0.8f)
        close()
    }
    drawPath(path, tint, style = Stroke(strokeWidth * 1.2f))
}

// Rio Grande do Sul: Faixas diagonais características
private fun DrawScope.drawRioGrandeDoSul(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, h * 0.35f), Offset(w * 0.65f, h), strokeWidth = strokeWidth)
    drawLine(tint, Offset(w * 0.35f, 0f), Offset(w, h * 0.65f), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.16f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Paraná: Faixa diagonal com círculo
private fun DrawScope.drawParana(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, h), Offset(w, 0f), strokeWidth = strokeWidth * 1.1f)
    drawCircle(tint, radius = h * 0.24f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Santa Catarina: Faixa central com losango
private fun DrawScope.drawSantaCatarina(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val y1 = h * 0.33f
    val y2 = h * 0.66f
    drawLine(tint, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeWidth)
    drawLine(tint, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.15f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Bahia: Cantão azul com triângulo e listras horizontais
private fun DrawScope.drawBahia(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val step = h / 4f
    for (i in 1..3) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth * 0.75f)
    }
    drawRect(tint, Offset(0f, 0f), Size(w * 0.42f, h * 0.5f), style = Stroke(strokeWidth))
    val triangle = Path().apply {
        moveTo(w * 0.21f, h * 0.1f)
        lineTo(w * 0.35f, h * 0.4f)
        lineTo(w * 0.07f, h * 0.4f)
        close()
    }
    drawPath(triangle, tint, style = Stroke(strokeWidth * 0.8f))
}

// Pernambuco: Arco-íris com sol e cruz
private fun DrawScope.drawPernambuco(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = strokeWidth)
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.2f, h * 0.1f),
        size = Size(w * 0.6f, h * 0.6f),
        style = Stroke(strokeWidth * 0.8f)
    )
    drawLine(tint, Offset(w / 2f, h * 0.6f), Offset(w / 2f, h * 0.9f), strokeWidth = strokeWidth)
    drawLine(tint, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.62f, h * 0.72f), strokeWidth = strokeWidth)
}

// Ceará: Losango com círculo e pássaro/farol
private fun DrawScope.drawCeara(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w / 2f, h * 0.15f)
        lineTo(w * 0.85f, h / 2f)
        lineTo(w / 2f, h * 0.85f)
        lineTo(w * 0.15f, h / 2f)
        close()
    }
    drawPath(path, tint, style = Stroke(strokeWidth))
    drawCircle(tint, radius = h * 0.18f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth * 0.8f))
}

// Goiás: Listras horizontais com cantão estrelado
private fun DrawScope.drawGoias(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val step = h / 6f
    for (i in 1..5) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth * 0.7f)
    }
    drawRect(tint, Offset(0f, 0f), Size(w * 0.38f, h * 0.5f), style = Stroke(strokeWidth))
}

// Distrito Federal: Quatro flechas heráldicas da cruz de Brasília
private fun DrawScope.drawDistritoFederal(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    drawLine(tint, Offset(cx, cy - h * 0.35f), Offset(cx, cy + h * 0.35f), strokeWidth = strokeWidth)
    drawLine(tint, Offset(cx - w * 0.28f, cy), Offset(cx + w * 0.28f, cy), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.12f, center = Offset(cx, cy), style = Stroke(strokeWidth))
}

// Espírito Santo: Três faixas com arco
private fun DrawScope.drawEspiritoSanto(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val y1 = h / 3f
    val y2 = (h * 2f) / 3f
    drawLine(tint, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeWidth)
    drawLine(tint, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeWidth)
    drawArc(
        color = tint,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.25f, h * 0.38f),
        size = Size(w * 0.5f, h * 0.24f),
        style = Stroke(strokeWidth * 0.8f)
    )
}

// Rio Grande do Norte: Faixa horizontal bipartida e brasão
private fun DrawScope.drawRioGrandeDoNorte(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.22f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Paraíba: Faixa vertical preta e vermelha com ponto central
private fun DrawScope.drawParaiba(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val x = w * 0.35f
    drawLine(tint, Offset(x, 0f), Offset(x, h), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.18f, center = Offset(w * 0.68f, h / 2f), style = Stroke(strokeWidth * 0.8f))
}

// Maranhão: Listras com cantão e estrela
private fun DrawScope.drawMaranhao(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val step = h / 6f
    for (i in 1..5) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth * 0.7f)
    }
    drawRect(tint, Offset(0f, 0f), Size(w * 0.32f, h * 0.45f), style = Stroke(strokeWidth))
}

// Mato Grosso e Mato Grosso do Sul: Faixa diagonal ou estrela central
private fun DrawScope.drawMatoGrosso(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, 0f), Offset(w, h), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.22f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Região Amazônica (PA, AM, AP, AC, RO, RR, TO): Linhas nítidas com estrela
private fun DrawScope.drawAmazonRegion(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, h), Offset(w, 0f), strokeWidth = strokeWidth * 0.9f)
    drawCircle(tint, radius = h * 0.18f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth * 1.1f))
}

// Nordeste (PI, AL, SE): Listras com estrela
private fun DrawScope.drawNordeste(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val y1 = h / 3f
    val y2 = (h * 2f) / 3f
    drawLine(tint, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeWidth)
    drawLine(tint, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.16f, center = Offset(w * 0.35f, h / 2f), style = Stroke(strokeWidth))
}

// Estados Unidos: Cantão com listras
private fun DrawScope.drawUnitedStates(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val step = h / 6f
    for (i in 1..5) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth * 0.6f)
    }
    drawRect(tint, Offset(0f, 0f), Size(w * 0.45f, h * 0.55f), style = Stroke(strokeWidth))
}

// Reino Unido: Cruz de São Jorge com aspas de Santo André
private fun DrawScope.drawUnitedKingdom(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawLine(tint, Offset(0f, 0f), Offset(w, h), strokeWidth = strokeWidth * 0.8f)
    drawLine(tint, Offset(0f, h), Offset(w, 0f), strokeWidth = strokeWidth * 0.8f)
    drawLine(tint, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = strokeWidth * 1.2f)
    drawLine(tint, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = strokeWidth * 1.2f)
}

// Portugal: Bipartida com esfera armilar
private fun DrawScope.drawPortugal(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val x = w * 0.4f
    drawLine(tint, Offset(x, 0f), Offset(x, h), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.25f, center = Offset(x, h / 2f), style = Stroke(strokeWidth))
}

// Espanha: Três faixas com brasão
private fun DrawScope.drawSpain(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val y1 = h * 0.25f
    val y2 = h * 0.75f
    drawLine(tint, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeWidth)
    drawLine(tint, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.16f, center = Offset(w * 0.3f, h / 2f), style = Stroke(strokeWidth))
}

// Japão: Círculo solar central nítido
private fun DrawScope.drawJapan(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawCircle(tint, radius = h * 0.28f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth * 1.3f))
}

// Argentina: Três faixas com sol
private fun DrawScope.drawArgentina(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val y1 = h / 3f
    val y2 = (h * 2f) / 3f
    drawLine(tint, Offset(0f, y1), Offset(w, y1), strokeWidth = strokeWidth)
    drawLine(tint, Offset(0f, y2), Offset(w, y2), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.14f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth))
}

// Canadá: Faixas verticais e folha central estilizada
private fun DrawScope.drawCanada(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val x1 = w * 0.25f
    val x2 = w * 0.75f
    drawLine(tint, Offset(x1, 0f), Offset(x1, h), strokeWidth = strokeWidth)
    drawLine(tint, Offset(x2, 0f), Offset(x2, h), strokeWidth = strokeWidth)
    drawCircle(tint, radius = h * 0.2f, center = Offset(w / 2f, h / 2f), style = Stroke(strokeWidth * 0.9f))
}

// Austrália: Cantão britânico e estrelas
private fun DrawScope.drawAustralia(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    drawRect(tint, Offset(0f, 0f), Size(w * 0.45f, h * 0.5f), style = Stroke(strokeWidth))
    drawCircle(tint, radius = h * 0.14f, center = Offset(w * 0.75f, h * 0.65f), style = Stroke(strokeWidth * 0.8f))
}

// Suíça: Cruz grega branca central
private fun DrawScope.drawSwitzerland(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    drawLine(tint, Offset(cx, cy - h * 0.3f), Offset(cx, cy + h * 0.3f), strokeWidth = strokeWidth * 2f, cap = StrokeCap.Square)
    drawLine(tint, Offset(cx - w * 0.22f, cy), Offset(cx + w * 0.22f, cy), strokeWidth = strokeWidth * 2f, cap = StrokeCap.Square)
}

// Listras horizontais genéricas (Alemanha, Holanda, etc.)
private fun DrawScope.drawHorizontalStripes(tint: Color, strokeWidth: Float, w: Float, h: Float, count: Int) {
    val step = h / count.toFloat()
    for (i in 1 until count) {
        drawLine(tint, Offset(0f, i * step), Offset(w, i * step), strokeWidth = strokeWidth)
    }
}

// Listras verticais genéricas (França, Itália, Irlanda, etc.)
private fun DrawScope.drawVerticalStripes(tint: Color, strokeWidth: Float, w: Float, h: Float, count: Int) {
    val step = w / count.toFloat()
    for (i in 1 until count) {
        drawLine(tint, Offset(i * step, 0f), Offset(i * step, h), strokeWidth = strokeWidth)
    }
}

// Ícone de Globo para "Todos os Países" ou fallback
private fun DrawScope.drawGlobeIcon(tint: Color, strokeWidth: Float, w: Float, h: Float) {
    val cx = w / 2f
    val cy = h / 2f
    val r = h * 0.38f
    drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(strokeWidth))
    drawLine(tint, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = strokeWidth * 0.8f)
    drawLine(tint, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = strokeWidth * 0.8f)
    drawOval(
        color = tint,
        topLeft = Offset(cx - r * 0.55f, cy - r),
        size = Size(r * 1.1f, r * 2f),
        style = Stroke(strokeWidth * 0.8f)
    )
}
