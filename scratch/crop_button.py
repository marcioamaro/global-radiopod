from PIL import Image

im = Image.open('playstore-icon.png')
print("playstore-icon size:", im.size)
# Let's check what's in the center (where the button / pear is)
# If the iPod center button is around (256, 336) in 512x512
crop = im.crop((200, 280, 312, 400))
crop.save('scratch/center_button_crop.png')
print("Saved center button crop to scratch/center_button_crop.png")
