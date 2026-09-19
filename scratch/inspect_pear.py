from PIL import Image
import os

img_path = r'app/src/main/res/drawable/ic_pear_logo.png'
im = Image.open(img_path)
print("Format:", im.format, "Size:", im.size, "Mode:", im.mode)
# Find bounding box of non-zero alpha
bbox = im.getbbox()
print("BBox:", bbox)
