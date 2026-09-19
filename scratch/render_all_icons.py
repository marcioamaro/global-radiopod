from PIL import Image, ImageDraw
import numpy as np

# Load base 512x512 icon
base_icon = Image.open('playstore-icon.png').convert('RGBA')

# Load the pear logo from ClickWheel
pear = Image.open('app/src/main/res/drawable/ic_pear_logo.png').convert('RGBA')

# The button center is at (256, 335) with radius ~30
# Let's first clean the button surface inside radius 28 with pure white #FFFFFF
# to remove any old distorted logo
draw = ImageDraw.Draw(base_icon)
cx, cy = 256, 335
r = 29
# Smooth clean disc
draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255), outline=(203, 213, 225, 255), width=2)

# Now resize the pear logo:
# Target height: 35px
target_h = 36
aspect = pear.width / pear.height
target_w = int(target_h * aspect)
pear_resized = pear.resize((target_w, target_h), Image.Resampling.LANCZOS)

# Tint the pear to slate gray #334155 (matching ClickWheel & vector icon)
# ic_pear_logo is white with alpha channel
pear_arr = np.array(pear_resized)
# pear_arr has shape (H, W, 4)
tinted = np.zeros_like(pear_arr)
tinted[:, :, 0] = 51   # R
tinted[:, :, 1] = 65   # G
tinted[:, :, 2] = 85   # B
tinted[:, :, 3] = pear_arr[:, :, 3] # Keep exact alpha

pear_tinted = Image.fromarray(tinted, mode='RGBA')

# Paste centered at (cx, cy)
px = cx - target_w // 2
py = cy - target_h // 2
base_icon.paste(pear_tinted, (px, py), mask=pear_tinted)

# Save to 512x512 locations
base_icon.save('playstore-icon.png', 'PNG')
base_icon.save('PlayStore/icon-512x512.png', 'PNG')
base_icon.save('app/src/main/res/drawable/playstore_icon.png', 'PNG')
print("Saved 512x512 icons!")

# Now regenerate all mipmaps
densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

for folder, size in densities.items():
    resized = base_icon.resize((size, size), Image.Resampling.LANCZOS)
    # ic_launcher.png (square / squircle)
    resized.save(f'app/src/main/res/{folder}/ic_launcher.png', 'PNG')
    
    # ic_launcher_round.png (circular clip)
    mask = Image.new('L', (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([0, 0, size, size], fill=255)
    
    round_icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_icon.paste(resized, (0, 0), mask=mask)
    round_icon.save(f'app/src/main/res/{folder}/ic_launcher_round.png', 'PNG')
    print(f"Generated {folder} ({size}x{size})")

print("All icon formats successfully updated with the official ClickWheel pear!")
