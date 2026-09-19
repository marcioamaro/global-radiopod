import cv2
import numpy as np
from PIL import Image

im = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')
alpha = np.array(im)[:, :, 3]

# Threshold alpha
_, thresh = cv2.threshold(alpha, 127, 255, cv2.THRESH_BINARY)
contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
print("Number of contours:", len(contours))

for i, c in enumerate(contours):
    area = cv2.contourArea(c)
    x, y, w, h = cv2.boundingRect(c)
    print(f"Contour {i}: area={area}, bbox=({x}, {y}, {w}, {h})")
