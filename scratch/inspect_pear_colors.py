from PIL import Image

im = Image.open(r'app/src/main/res/drawable/ic_pear_logo.png')
# check unique colors and alpha
colors = im.getcolors(maxcolors=1000)
print(f"Num colors: {len(colors) if colors else 'many'}")
# check center pixel
print("Center pixel:", im.getpixel((im.width//2, im.height//2)))
