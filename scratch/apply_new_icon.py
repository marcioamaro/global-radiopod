import os
import numpy as np
import cv2
from PIL import Image

src_path = r'C:\Users\marci\.gemini\antigravity-ide\brain\e192f394-da1f-411a-b2e0-516d7eec67d7\.user_uploaded\media_1789759924362.jpg'
print('Loading source image:', src_path)

img = Image.open(src_path).convert('RGBA')
arr = np.array(img)
gray = cv2.cvtColor(arr[:, :, :3], cv2.COLOR_RGB2GRAY)

# Step 1: Detect outer black background connected to image edges
black_mask = (gray <= 12).astype(np.uint8)
num_labels, labels = cv2.connectedComponents(black_mask)
border_labels = set(np.unique(np.concatenate([labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]])))
border_labels.discard(0)

is_background = np.isin(labels, list(border_labels))

alpha = np.where(is_background, 0, 255).astype(np.uint8)
# Subtle Gaussian blur for smooth anti-aliased edge
alpha_float = cv2.GaussianBlur(alpha.astype(np.float32), (3, 3), 0.8)
alpha_final = np.clip(alpha_float, 0, 255).astype(np.uint8)
arr[:, :, 3] = alpha_final

# Step 2: Center squircle in 1024x1024 frame
h, w = arr.shape[:2]
M = np.float32([[1, 0, 2], [0, 1, 10]])
centered = cv2.warpAffine(arr, M, (w, h), flags=cv2.INTER_LANCZOS4, borderMode=cv2.BORDER_CONSTANT, borderValue=(0,0,0,0))
master_img = Image.fromarray(centered)

# Master 512x512 Play Store icon
playstore_512 = master_img.resize((512, 512), Image.Resampling.LANCZOS)
playstore_paths = [
    'playstore-icon.png',
    'PlayStore/icon-512x512.png',
    'app/src/main/res/drawable/playstore_icon.png'
]
for p in playstore_paths:
    os.makedirs(os.path.dirname(p) if os.path.dirname(p) else '.', exist_ok=True)
    playstore_512.save(p, 'PNG')
    print(f'Saved 512x512: {p}')

# Legacy Mipmap icons (transparent corners as requested)
mipmap_sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

for density, size in mipmap_sizes.items():
    icon_resized = master_img.resize((size, size), Image.Resampling.LANCZOS)
    dir_path = f'app/src/main/res/mipmap-{density}'
    os.makedirs(dir_path, exist_ok=True)
    icon_resized.save(os.path.join(dir_path, 'ic_launcher.png'), 'PNG')
    icon_resized.save(os.path.join(dir_path, 'ic_launcher_round.png'), 'PNG')
    print(f'Saved mipmap-{density} ({size}x{size})')

# Adaptive Foregrounds (108dp canvas: mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432)
# Inside 108dp canvas, the icon occupies the centered ~88dp safe zone
adaptive_sizes = {
    'mdpi': (108, 90),
    'hdpi': (162, 134),
    'xhdpi': (216, 180),
    'xxhdpi': (324, 270),
    'xxxhdpi': (432, 360)
}

for density, (canvas_size, icon_size) in adaptive_sizes.items():
    canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
    icon_scaled = master_img.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
    offset = ((canvas_size - icon_size) // 2, (canvas_size - icon_size) // 2)
    canvas.paste(icon_scaled, offset, icon_scaled)
    
    dir_path = f'app/src/main/res/drawable-{density}'
    os.makedirs(dir_path, exist_ok=True)
    target = os.path.join(dir_path, 'ic_launcher_foreground.png')
    canvas.save(target, 'PNG')
    print(f'Saved adaptive foreground drawable-{density} ({canvas_size}x{canvas_size})')

print('All icons updated successfully!')
