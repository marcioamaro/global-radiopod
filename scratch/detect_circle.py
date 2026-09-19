import cv2
import numpy as np
from PIL import Image

im = cv2.imread('scratch/center_crop_actual.png')
gray = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY)
circles = cv2.HoughCircles(gray, cv2.HOUGH_GRADIENT, 1, 20, param1=50, param2=20, minRadius=15, maxRadius=35)

if circles is not None:
    circles = np.uint16(np.around(circles))
    for i in circles[0, :]:
        print(f"Detected circle in crop: center=({i[0]}, {i[1]}), r={i[2]}")
        # convert to full 512 image coordinates:
        # crop origin was (256 - 40, 337 - 40) = (216, 297)
        full_cx = 216 + i[0]
        full_cy = 297 + i[1]
        print(f"Full image center=({full_cx}, {full_cy}), r={i[2]}")
else:
    print("No circle detected automatically, inspecting colors...")
