from PIL import Image
import numpy as np

im = Image.open('playstore-icon.png').convert('RGBA')
arr = np.array(im)

# Look at the iPod in 512x512
# The center button is a small white/gray circle in the bottom half
# In viewport 108x108 with scale 0.68, center is (54, 71)
# In 512x512:
# 54/108 * 512 = 256
# 71/108 * 512 = 336.5
# Let's inspect a 60x60 crop around (256, 337)
print("Image size:", im.size)
crop = im.crop((256 - 40, 337 - 40, 256 + 40, 337 + 40))
crop.save('scratch/center_crop_actual.png')
print("Saved scratch/center_crop_actual.png")
