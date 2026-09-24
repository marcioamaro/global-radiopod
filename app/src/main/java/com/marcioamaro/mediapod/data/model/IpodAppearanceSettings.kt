package com.marcioamaro.mediapod.data.model

/**
 * Paleta de cores do hardware físico do iPod (Carcaça, Click Wheel, Texto da Roda e Botão Central).
 */
data class IpodPalette(
    val bodyColor: Long = 0xFFF1F5F9,
    val wheelColor: Long = 0xFFE2E4E8,
    val wheelTextColor: Long = 0xFF475569,
    val centerButtonColor: Long = 0xFFFFFFFF
)

typealias ResolvedIpodColors = IpodPalette

/**
 * Configurações de aparência do iPod com suporte a modo aleatório seguro por sessão
 * e bloqueio derivado exclusivo.
 */
data class IpodAppearanceSettings(
    val randomHardwareColorsEnabled: Boolean = false,
    val manualPalette: IpodPalette = IpodPalette(),
    val activePalette: IpodPalette = IpodPalette(),
    val lastValidPalette: IpodPalette = IpodPalette(),
    val lastGenerationId: Long = 0L,
    val schemaVersion: Int = 1
) {
    /**
     * Regra Obrigatória e Exclusiva de Bloqueio Derivado:
     * A edição manual só é permitida quando o modo aleatório estiver desativado.
     * Não existe flag ou toggle de bloqueio independente.
     */
    val isManualColorEditingEnabled: Boolean
        get() = !randomHardwareColorsEnabled

    val resolvedIpodColors: ResolvedIpodColors
        get() = activePalette
}

