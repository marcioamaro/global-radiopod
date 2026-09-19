import cv2
import numpy as np
from PIL import Image

im = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')
alpha = np.array(im)[:, :, 3]

# Threshold
_, thresh = cv2.threshold(alpha, 127, 255, cv2.THRESH_BINARY)
# Find external contours
contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_TC89_KCOS)

# Sort contours from largest to smallest
contours = sorted(contours, key=cv2.contourArea, reverse=True)
print(f"Total contours: {len(contours)}")

# Pear body is contour 0
# Leaf is contour 1
# Stem is contour 2

# Overall bounding box across all contours:
all_pts = np.vstack(contours)
bx, by, bw, bh = cv2.boundingRect(all_pts)
print(f"Overall bbox: x={bx}, y={by}, w={bw}, h={bh}")

# We want to place this inside center button at cx=54.0, cy=71.0
# Target height: 8.4 dp (fits comfortably in circle of radius 7.5, diameter 15)
target_h = 8.4
scale = target_h / bh
target_w = bw * scale

# Target center
tx0 = 54.0 - target_w / 2.0
ty0 = 71.0 - target_h / 2.0

paths = []
for i, c in enumerate(contours):
    # smooth polygon
    epsilon = 0.5
    approx = cv2.approxPolyDP(c, epsilon, True)
    pts = []
    for p in approx:
        px, py = p[0]
        vx = tx0 + (px - bx) * scale
        vy = ty0 + (py - by) * scale
        pts.append((vx, vy))
    
    # generate SVG path
    d = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    for x, y in pts[1:]:
        d += f" L{x:.2f},{y:.2f}"
    d += " Z"
    paths.append((i, cv2.contourArea(c), d))

# Combine all paths into a single pathData or separate paths
for i, area, d in paths:
    print(f"Contour {i} (area {area:.1f}):")
    print(d[:100] + "...")
