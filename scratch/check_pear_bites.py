import cv2
import numpy as np
from PIL import Image

im = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')
alpha = np.array(im)[:, :, 3]

# Check the width profile of the pear body along the Y axis
# Find horizontal slice widths
h, w = alpha.shape
print(f"Image dimensions: {w}x{h}")

# Let's inspect left edge and right edge of the pear body between Y=200 and Y=450
print("Sample of horizontal edges (Y: left_x, right_x, width):")
for y in range(220, 420, 20):
    row = np.where(alpha[y, :] > 128)[0]
    if len(row) > 0:
        print(f"Y={y}: left={row[0]}, right={row[-1]}, width={row[-1] - row[0]}")
