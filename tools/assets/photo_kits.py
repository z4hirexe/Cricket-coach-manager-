"""Stumpstagram photo moments: split each original AI photo (players in chroma-magenta kit) into
 - ph_<k>.jpg      : the photo with the kit converted to light grey luminance (keeps folds / shading)
 - ph_<k>_kit.webp : alpha mask of the kit (the app multiplies the team colour through it)"""
import sys, numpy as np
from PIL import Image, ImageFilter
SRC = sys.argv[1]; OUT = 'app/src/main/res/drawable-nodpi'
for k in ['century', 'fifty', 'hattrick', 'fivefor', 'win', 'trophy', 'signing']:
    im = Image.open(f'{SRC}/ph_{k}.jpg').convert('RGB').resize((720, 720), Image.LANCZOS)
    hsv = np.asarray(im.convert('HSV')).astype(np.float32)
    h, s, v = hsv[..., 0] * 360 / 255, hsv[..., 1] / 255, hsv[..., 2] / 255
    # magenta / pink band
    m = ((h > 285) | (h < 8)) & (s > 0.30) & (v > 0.18)
    m &= ~((h < 8) & (s > 0.75))       # keep red balls / bat stickers out
    mask = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MedianFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    a = np.asarray(mask).astype(np.float32) / 255
    rgb = np.asarray(im).astype(np.float32)
    lum = rgb[..., 0] * 0.30 + rgb[..., 1] * 0.45 + rgb[..., 2] * 0.25
    grey = np.clip(lum * 1.3 + 70, 0, 255)
    out = rgb * (1 - a[..., None]) + grey[..., None] * a[..., None]
    Image.fromarray(out.astype(np.uint8)).save(f'{OUT}/ph_{k}.jpg', quality=84)
    rgba = np.zeros((720, 720, 4), np.uint8); rgba[..., :3] = 255; rgba[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(f'{OUT}/ph_{k}_kit.webp', 'WEBP', lossless=True)
    print(k, f'{m.mean()*100:.1f}% kit')
