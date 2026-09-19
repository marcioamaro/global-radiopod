import re

with open("sitemap_radio1.xml", "r", encoding="utf-8") as f:
    content = f.read()

urls = re.findall(r'<loc>\s*(https?://www\.radios\.com\.br/aovivo/[^\s<]+)\s*</loc>', content)
print("Total URLs:", len(urls))
print("First 10:", urls[:10])
print("URLs 500-510:", urls[500:510])
print("URLs 990-1000:", urls[990:1000])
print("URLs 1200-1210:", urls[1200:1210])
print("URLs 1400-1410:", urls[1400:1410])
