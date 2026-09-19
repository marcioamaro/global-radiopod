import cv2
import numpy as np
from PIL import Image

im = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')
alpha = np.array(im)[:, :, 3]

# Get bounding box of all non-zero pixels
bbox = im.getbbox() # (7, 8, 339, 502)
bx0, by0, bx1, by1 = bbox
bw = bx1 - bx0
bh = by1 - by0
print(f"Bbox: {bbox}, w={bw}, h={bh}")

# We want the pear to fit centered at (cx=54.0, cy=71.0)
# Target height: 8.5 units. Aspect ratio: bw / bh = 332 / 494 = 0.672
# Target width: 8.5 * 0.672 = 5.71 units
target_h = 8.5
target_w = target_h * (bw / bh)
target_cx = 54.0
target_cy = 71.0

# Target bounding box in viewport:
tx0 = target_cx - target_w / 2.0
ty0 = target_cy - target_h / 2.0

# Scale factor: target_h / bh
scale = target_h / bh

# Generate SVG path for each contour
_, thresh = cv2.threshold(alpha, 127, 255, cv2.THRESH_BINARY)
contours, hierarchy = cv2.findContours(thresh, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_TC89_KCOS)

paths = []
for i, c in enumerate(contours):
    # approximate polygon with epsilon for smooth compact vector
    epsilon = 0.8
    approx = cv2.approxPolyDP(c, epsilon, True)
    
    # Convert points to viewport coordinates
    pts = []
    for pt in approx:
        px, py = pt[0]
        # transform from image (px, py) to viewport (vx, vy)
        vx = tx0 + (px - bx0) * scale
        vy = ty0 + (py - by0) * scale
        pts.append((vx, vy))
    
    if len(pts) < 3:
        continue
    
    # build path data
    d = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    for x, y in pts[1:]:
        d += f" L{x:.2f},{y:.2f}"
    d += " Z"
    paths.append(d)

print(f"Generated {len(paths)} paths.")
for i, p in enumerate(paths):
    print(f"Path {i} len={len(p)}: {p[:60]}...")
