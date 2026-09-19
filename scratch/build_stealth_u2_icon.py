#!/usr/bin/env python3
"""
build_stealth_u2_icon.py — Implementa a versão Stealth Dark / U2 Edition do ícone
com a exata pera mordida dos dois lados (ic_pear_logo.png) no centro do Click Wheel.
"""

from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import cv2
import os

# --- 1. GERAÇÃO DO ÍCONE MASTER 512x512 EM ALTA RESOLUÇÃO ---

SIZE = 512
im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(im)

# A. Background Squircle / Rounded rect com gradiente Dark Obsidian
bg_radius = 114 # Padrão Play Store squircle radius
# Background escuro premium (#0C1017 para #18202C)
for y in range(SIZE):
    # sutil gradiente vertical
    t = y / SIZE
    r = int(12 * (1 - t) + 20 * t)
    g = int(16 * (1 - t) + 26 * t)
    b = int(24 * (1 - t) + 38 * t)
    draw.line([(0, y), (SIZE, y)], fill=(r, g, b, 255))

# Sutil anel concêntrico de iluminação no fundo
draw.ellipse([SIZE//2 - 230, SIZE//2 - 230, SIZE//2 + 230, SIZE//2 + 230], outline=(35, 46, 64, 140), width=3)
draw.ellipse([SIZE//2 - 215, SIZE//2 - 215, SIZE//2 + 215, SIZE//2 + 215], outline=(25, 34, 48, 120), width=2)

# B. Sombra suave sob o iPod
pod_x0, pod_y0, pod_x1, pod_y1 = 110, 52, 402, 460
shadow_mask = Image.new('L', (SIZE, SIZE), 0)
shadow_draw = ImageDraw.Draw(shadow_mask)
shadow_draw.rounded_rectangle([pod_x0 - 4, pod_y0 + 10, pod_x1 + 4, pod_y1 + 18], radius=44, fill=220)
shadow_blur = shadow_mask.filter(ImageFilter.GaussianBlur(14))
shadow_layer = Image.new('RGBA', (SIZE, SIZE), (2, 4, 8, 200))
im.paste(shadow_layer, (0, 0), mask=shadow_blur)

# C. Corpo do iPod: Matte Obsidian Black (#1A212B com chanfro)
# Base chanfrada (borda externa 3D)
draw.rounded_rectangle([pod_x0, pod_y0, pod_x1, pod_y1], radius=42, fill=(38, 48, 64, 255))
# Face frontal matte obsidian
draw.rounded_rectangle([pod_x0 + 2, pod_y0 + 2, pod_x1 - 2, pod_y1 - 2], radius=40, fill=(21, 27, 36, 255))

# D. Tela LCD / OLED de Rádio (Topo)
screen_x0, screen_y0, screen_x1, screen_y1 = 138, 80, 374, 226
# Bezel externo da tela (vidro escuro com reflexo chanfrado)
draw.rounded_rectangle([screen_x0 - 3, screen_y0 - 3, screen_x1 + 3, screen_y1 + 3], radius=16, fill=(11, 14, 19, 255))
# Display OLED deep black
draw.rounded_rectangle([screen_x0, screen_y0, screen_x1, screen_y1], radius=14, fill=(8, 10, 14, 255))

# Header bar da tela (sutil linha de status)
draw.rounded_rectangle([screen_x0 + 4, screen_y0 + 4, screen_x1 - 4, screen_y0 + 24], radius=6, fill=(15, 20, 27, 255))
# Indicador Play clássico e bateria
draw.polygon([(screen_x0 + 16, screen_y0 + 9), (screen_x0 + 26, screen_y0 + 14), (screen_x0 + 16, screen_y0 + 19)], fill=(245, 158, 11, 230))
draw.rectangle([screen_x1 - 32, screen_y0 + 10, screen_x1 - 16, screen_y0 + 18], fill=(245, 158, 11, 230))
draw.rectangle([screen_x1 - 15, screen_y0 + 12, screen_x1 - 13, screen_y0 + 16], fill=(245, 158, 11, 230))

# Onda de Rádio / Waveform em Neon Âmbar Retro (#F59E0B com glow)
wave_points = [
    (156, 166), (170, 145), (184, 185), (198, 130), (212, 195),
    (226, 120), (240, 205), (256, 110), (272, 205), (286, 120),
    (300, 195), (314, 130), (328, 185), (342, 145), (356, 166)
]
# Glow do waveform
wave_glow = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
wave_glow_draw = ImageDraw.Draw(wave_glow)
wave_glow_draw.line(wave_points, fill=(251, 146, 60, 180), width=6, joint="curve")
wave_glow = wave_glow.filter(ImageFilter.GaussianBlur(4))
im.paste(wave_glow, (0, 0), mask=wave_glow)
# Linha principal nítida do waveform
draw.line(wave_points, fill=(254, 215, 170, 255), width=3, joint="curve")

# Frequência de Rádio Digital "107.7 FM" ou "10:09 AM"
# Desenha sutil texto de dial no rodapé da tela
draw.text((220, 196), "107.7 FM", fill=(245, 158, 11, 240))

# E. Click Wheel — Vermelho Carmesim Profundo U2 Special Edition
wheel_cx = 256
wheel_cy = 340
wheel_r = 86

# Anel externo do Click Wheel chanfrado
draw.ellipse([wheel_cx - wheel_r - 2, wheel_cy - wheel_r - 2, wheel_cx + wheel_r + 2, wheel_cy + wheel_r + 2], fill=(120, 15, 15, 255))
# Corpo do Click Wheel vermelho carmesim vibrante (#DC2626 com micro-textura)
draw.ellipse([wheel_cx - wheel_r, wheel_cy - wheel_r, wheel_cx + wheel_r, wheel_cy + wheel_r], fill=(185, 28, 28, 255))

# Textura concêntrica sutil no Click Wheel vermelho
for cr in range(wheel_r - 4, 38, -6):
    draw.ellipse([wheel_cx - cr, wheel_cy - cr, wheel_cx + cr, wheel_cy + cr], outline=(220, 38, 38, 70), width=1)

# Rótulos no Click Wheel: MENU, ▶ ❚❚, ❚◀◀, ▶▶❚ (Branco/Prata)
# Top MENU
draw.text((wheel_cx - 18, wheel_cy - wheel_r + 10), "MENU", fill=(255, 255, 255, 230))
# Bottom Play/Pause
draw.text((wheel_cx - 14, wheel_cy + wheel_r - 24), "▶ ❚❚", fill=(255, 255, 255, 230))
# Left Prev
draw.text((wheel_cx - wheel_r + 12, wheel_cy - 7), "❚◀◀", fill=(255, 255, 255, 230))
# Right Next
draw.text((wheel_cx + wheel_r - 30, wheel_cy - 7), "▶▶❚", fill=(255, 255, 255, 230))

# F. Botão Central SELECT — Disco de Metal Prateado / Cromo
center_r = 34
draw.ellipse([wheel_cx - center_r - 1, wheel_cy - center_r - 1, wheel_cx + center_r + 1, wheel_cy + center_r + 1], fill=(148, 163, 184, 255))
# Gradiente sutil prateado escovado
draw.ellipse([wheel_cx - center_r, wheel_cy - center_r, wheel_cx + center_r, wheel_cy + center_r], fill=(226, 232, 240, 255))
draw.ellipse([wheel_cx - center_r + 2, wheel_cy - center_r + 2, wheel_cx + center_r - 2, wheel_cy + center_r - 2], outline=(241, 245, 249, 180), width=1)

# G. Logotipo da Pêra Oficial do Sobre (ic_pear_logo.png) mordida dos dois lados
pear_img = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')

# Dimensionamento exato da pêra para o disco central
target_pear_h = 42
aspect = pear_img.width / pear_img.height
target_pear_w = int(target_pear_h * aspect)

pear_resized = pear_img.resize((target_pear_w, target_pear_h), Image.Resampling.LANCZOS)
pear_arr = np.array(pear_resized)

# Tintar a pêra em Grafite Escuro Metálico (#1E293B) com sutil chanfro
tinted_pear = np.zeros_like(pear_arr)
tinted_pear[:, :, 0] = 30  # R
tinted_pear[:, :, 1] = 41  # G
tinted_pear[:, :, 2] = 59  # B
tinted_pear[:, :, 3] = pear_arr[:, :, 3]

pear_final = Image.fromarray(tinted_pear, mode='RGBA')

px = wheel_cx - target_pear_w // 2
py = wheel_cy - target_pear_h // 2
im.paste(pear_final, (px, py), mask=pear_final)

# Salva o ícone master em 512x512
im.save('playstore-icon.png', 'PNG')
im.save('PlayStore/icon-512x512.png', 'PNG')
im.save('app/src/main/res/drawable/playstore_icon.png', 'PNG')
print("Ícones 512x512 Stealth Dark / U2 Edition salvos com sucesso!")

# --- 2. GERAÇÃO DE TODOS OS MIPMAPS (48, 72, 96, 144, 192) ---
densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

for folder, size in densities.items():
    # Square squircle
    res = im.resize((size, size), Image.Resampling.LANCZOS)
    res.save(f'app/src/main/res/{folder}/ic_launcher.png', 'PNG')
    
    # Circular mask
    mask = Image.new('L', (size, size), 0)
    m_draw = ImageDraw.Draw(mask)
    m_draw.ellipse([0, 0, size, size], fill=255)
    
    round_im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_im.paste(res, (0, 0), mask=mask)
    round_im.save(f'app/src/main/res/{folder}/ic_launcher_round.png', 'PNG')
    print(f"Mipmap gerado: {folder} ({size}x{size})")

print("Todos os ícones rasterizados atualizados para Stealth Dark U2 Edition!")
