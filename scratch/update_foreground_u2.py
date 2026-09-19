import cv2
import numpy as np
from PIL import Image

# Extract exact vector contours from ic_pear_logo.png (the one used in "Sobre")
im = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')
alpha = np.array(im)[:, :, 3]

_, thresh = cv2.threshold(alpha, 127, 255, cv2.THRESH_BINARY)
contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_TC89_KCOS)
contours = sorted(contours, key=cv2.contourArea, reverse=True)

all_pts = np.vstack(contours)
bx, by, bw, bh = cv2.boundingRect(all_pts)

target_h = 8.4
scale = target_h / bh
target_w = bw * scale

tx0 = 54.0 - target_w / 2.0
ty0 = 71.0 - target_h / 2.0

paths = []
for c in contours:
    approx = cv2.approxPolyDP(c, 0.4, True)
    pts = []
    for p in approx:
        px, py = p[0]
        vx = tx0 + (px - bx) * scale
        vy = ty0 + (py - by) * scale
        pts.append((vx, vy))
    d = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    for x, y in pts[1:]:
        d += f" L{x:.2f},{y:.2f}"
    d += " Z"
    paths.append(d)

pear_xml_paths = "\n".join([f'        <path\n            android:pathData="{p}"\n            android:fillColor="#1E293B" />' for p in paths])

foreground_xml = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Scaled group so dark obsidian background is visible on all sides -->
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="0.68"
        android:scaleY="0.68">

        <!-- Deep Drop Shadow under iPod for 3D depth -->
        <path
            android:pathData="M27,27 C27,21 31,19 36,19 L72,19 C77,19 81,21 81,27 L81,85 C81,90 77,92 72,92 L36,92 C31,92 27,90 27,85 Z"
            android:fillColor="#040609" />

        <!-- iPod Outer Shell (Matte Obsidian Black Body - Stealth U2 Edition) -->
        <path
            android:pathData="M28,25 C28,20 32,18 37,18 L71,18 C76,18 80,20 80,25 L80,83 C80,88 76,90 71,90 L37,90 C32,90 28,88 28,83 Z"
            android:fillColor="#151B24"
            android:strokeWidth="1.2"
            android:strokeColor="#2A3546" />

        <!-- Subtle Top Bevel Shade for 3D Depth -->
        <path
            android:pathData="M29,25 C29,21 33,19 37,19 L71,19 C75,19 79,21 79,25 L79,27 L29,27 Z"
            android:fillColor="#222B3A" />

        <!-- Screen Bezel (Jet Black Glass Border) -->
        <path
            android:pathData="M33,22 L75,22 C76.1,22 77,22.9 77,24 L77,50 C77,51.1 76.1,52 75,52 L33,52 C31.9,52 31,51.1 31,50 L31,24 C31,22.9 31.9,22 33,22 Z"
            android:fillColor="#090C10" />

        <!-- OLED Screen (Deep Pitch Black) -->
        <path
            android:pathData="M34,23 L74,23 C74.6,23 75,23.4 75,24 L75,49 C75,49.6 74.6,50 74,50 L34,50 C33.4,50 33,49.6 33,49 L33,24 C33,23.4 33.4,23 34,23 Z"
            android:fillColor="#05070A" />

        <!-- OLED Screen Header Bar -->
        <path
            android:pathData="M33,23 L75,23 L75,28 L33,28 Z"
            android:fillColor="#0E131A" />

        <!-- Glowing Amber Play Indicator & Battery Icon -->
        <path
            android:pathData="M36,24.5 L39,26 L36,27.5 Z"
            android:fillColor="#F59E0B" />
        <path
            android:pathData="M69,24.5 L73,24.5 L73,27.5 L69,27.5 Z"
            android:fillColor="#F59E0B" />

        <!-- Glowing Amber Waveform / Radio Frequency -->
        <path
            android:pathData="M36,41 L39,36 L43,44 L47,33 L51,45 L55,37 L59,42 L63,35 L67,41 L71,36"
            android:strokeWidth="1.8"
            android:strokeColor="#FB923C"
            android:strokeLineCap="round"
            android:strokeLineJoin="round" />

        <!-- iPod Click Wheel (U2 Special Edition - Crimson Metallic Red Disc) -->
        <path
            android:pathData="M54,71m-16,0a16,16 0 1,0 32,0a16,16 0 1,0 -32,0"
            android:fillColor="#B91C1C"
            android:strokeWidth="1.0"
            android:strokeColor="#991B1B" />

        <!-- Center SELECT Button (Brushed Silver Chrome Disc) -->
        <path
            android:pathData="M54,71m-7.5,0a7.5,7.5 0 1,0 15,0a7.5,7.5 0 1,0 -15,0"
            android:fillColor="#E2E8F0"
            android:strokeWidth="0.8"
            android:strokeColor="#CBD5E1" />

        <!-- Logotipo da Pêra Oficial do Sobre (ic_pear_logo.png com mordidas dos dois lados) -->
{pear_xml_paths}
    </group>

</vector>
'''

with open('app/src/main/res/drawable/ic_launcher_foreground.xml', 'w', encoding='utf-8') as f:
    f.write(foreground_xml)

print("ic_launcher_foreground.xml atualizado com Stealth Dark U2 Edition e Pêra Oficial do Sobre!")
