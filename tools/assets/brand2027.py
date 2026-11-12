"""Builds the Cricket Manager Pro 2027(TM) brand assets from the original AI portrait + loading artwork.
Text is rendered here (crisp) with the bundled OFL Barlow Condensed font; nothing is AI-lettered."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import sys, os
SRC = sys.argv[1]; RES = 'app/src/main/res'
BOLD = RES + '/font/barlow_condensed_bold.ttf'; SEMI = RES + '/font/barlow_condensed_semibold.ttf'
GOLD = (233, 185, 73); GOLD_L = (255, 231, 166); WHITE = (244, 246, 252); NAVY = (6, 11, 26)

def gold_text(base, xy, text, font, anchor='mm'):
    # vertical gold gradient fill + dark outline + soft shadow
    w, h = base.size
    mask = Image.new('L', base.size, 0); ImageDraw.Draw(mask).text(xy, text, font=font, fill=255, anchor=anchor)
    sh = Image.new('L', base.size, 0); ImageDraw.Draw(sh).text((xy[0], xy[1] + max(2, font.size // 22)), text, font=font, fill=200, anchor=anchor)
    sh = sh.filter(ImageFilter.GaussianBlur(max(2, font.size // 18)))
    base.paste(Image.new('RGB', base.size, (0, 0, 0)), (0, 0), sh)
    grad = Image.new('RGB', (1, h)); bb = mask.getbbox() or (0, 0, w, h)
    for y in range(h):
        t = min(1, max(0, (y - bb[1]) / max(1, bb[3] - bb[1])))
        c = [int(GOLD_L[i] + (GOLD[i] - GOLD_L[i]) * min(1, t * 1.6)) if t < 0.6 else int(GOLD[i] + ((184, 134, 43)[i] - GOLD[i]) * (t - 0.6) / 0.4) for i in range(3)]
        grad.putpixel((0, y), tuple(c))
    base.paste(grad.resize(base.size), (0, 0), mask)

def lockup(img, cx, top, width):
    """CRICKET MANAGER PRO / 2027 with a small superscript TM at the upper right."""
    d = ImageDraw.Draw(img)
    f1 = ImageFont.truetype(BOLD, 10); s = 10
    while d.textlength('CRICKET MANAGER PRO', font=ImageFont.truetype(BOLD, s + 2)) < width: s += 2
    f1 = ImageFont.truetype(BOLD, s)
    d.text((cx + 2, top + 3), 'CRICKET MANAGER PRO', font=f1, fill=(0, 0, 0), anchor='mt')
    d.text((cx, top), 'CRICKET MANAGER PRO', font=f1, fill=WHITE, anchor='mt')
    f2 = ImageFont.truetype(BOLD, int(s * 1.55)); y2 = top + int(s * 0.95)
    gold_text(img, (cx, y2), '2027', f2, anchor='mt')
    tw = d.textlength('2027', font=f2); ft = ImageFont.truetype(SEMI, max(10, int(s * 0.42)))
    d.text((cx + tw / 2 + s * 0.08, y2 + s * 0.18), '™', font=ft, fill=WHITE)
    return y2 + int(s * 1.55)

src = Image.open(SRC + '/coach_portrait.png').convert('RGB').resize((1024, 1024), Image.LANCZOS)
# darken bottom for the title
grad = Image.new('L', (1, 1024))
for y in range(1024): grad.putpixel((0, y), int(255 * min(1, max(0, (y - 560) / 330)) ** 1.3))
src.paste(Image.new('RGB', (1024, 1024), NAVY), (0, 0), grad.resize((1024, 1024)))
logo = src.copy(); lockup(logo, 512, 742, 860)
# thin gold frame
ImageDraw.Draw(logo).rounded_rectangle((10, 10, 1013, 1013), radius=56, outline=GOLD, width=6)
m = Image.new('L', (1024, 1024), 0); ImageDraw.Draw(m).rounded_rectangle((6, 6, 1017, 1017), radius=60, fill=255)
out = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0)); out.paste(logo, (0, 0), m)
out.resize((720, 720), Image.LANCZOS).save(RES + '/drawable-nodpi/logo_cmp.webp', 'WEBP', quality=90)
out.save(SRC + '/logo_2027_full.png')
# portrait-only mark (no text) for compact headers
mark = Image.open(SRC + '/coach_portrait.png').convert('RGB').crop((212, 40, 812, 640)).resize((256, 256), Image.LANCZOS)
mk = Image.new('L', (256, 256), 0); ImageDraw.Draw(mk).ellipse((0, 0, 255, 255), fill=255)
mo = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); mo.paste(mark, (0, 0), mk)
ImageDraw.Draw(mo).ellipse((2, 2, 253, 253), outline=GOLD + (255,), width=5)
mo.save(RES + '/drawable-nodpi/brand_mark.webp', 'WEBP', quality=90)
# launcher icons: portrait crop + "2027" ribbon
icon = Image.open(SRC + '/coach_portrait.png').convert('RGB').crop((152, 20, 872, 740)).resize((432, 432), Image.LANCZOS)
gi = Image.new('L', (1, 432))
for y in range(432): gi.putpixel((0, y), int(230 * min(1, max(0, (y - 250) / 120))))
icon.paste(Image.new('RGB', (432, 432), NAVY), (0, 0), gi.resize((432, 432)))
fi = ImageFont.truetype(BOLD, 92); gold_text(icon, (216, 300), '2027', fi, anchor='mt')
sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for k, px in sizes.items():
    d = f'{RES}/mipmap-{k}'
    icon.resize((px, px), Image.LANCZOS).save(d + '/ic_launcher.webp', 'WEBP', quality=92)
    rm = Image.new('L', (px, px), 0); ImageDraw.Draw(rm).ellipse((0, 0, px - 1, px - 1), fill=255)
    r = Image.new('RGBA', (px, px), (0, 0, 0, 0)); r.paste(icon.resize((px, px), Image.LANCZOS), (0, 0), rm); r.save(d + '/ic_launcher_round.webp', 'WEBP', quality=92)
    fg = int(px * 108 / 48)  # adaptive foreground canvas (108dp), artwork in the 66dp safe zone
    canvas = Image.new('RGBA', (fg, fg), NAVY + (255,)); inner = int(fg * 0.72)
    canvas.paste(icon.resize((inner, inner), Image.LANCZOS), ((fg - inner) // 2, (fg - inner) // 2))
    canvas.save(d + '/ic_launcher_foreground.webp', 'WEBP', quality=92)
# loading artwork (1600x900, crop-safe: subject right, empty left)
hero = Image.open(SRC + '/loading_hero.jpg').convert('RGB')
hero = hero.resize((1600, int(1600 * hero.height / hero.width)), Image.LANCZOS)
hero.save(RES + '/drawable-nodpi/loading_hero.jpg', quality=86)
print('ok', hero.size)
