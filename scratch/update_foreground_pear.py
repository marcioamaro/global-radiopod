import cv2
import numpy as np
from PIL import Image
import os

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

pear_xml_paths = "\n".join([f'        <path\n            android:pathData="{p}"\n            android:fillColor="#334155" />' for p in paths])

with open('app/src/main/res/drawable/ic_launcher_foreground.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace pear logo section
marker_start = "<!-- Prominent Bitten Pear Logo (Centered inside the button) -->"
marker_end = "</group>"

start_idx = content.find(marker_start)
end_idx = content.find(marker_end, start_idx)

new_content = content[:start_idx] + marker_start + "\n" + pear_xml_paths + "\n    " + content[end_idx:]

with open('app/src/main/res/drawable/ic_launcher_foreground.xml', 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Updated ic_launcher_foreground.xml successfully!")
