# -*- coding: utf-8 -*-
"""시연 영상용 바탕·타이틀·자막 띠 PNG 를 만듭니다 (Pillow, 맑은 고딕).

    python video_cards.py cards.json

cards.json:
  { "dir": "C:\\...\\cards", "size": [1920, 1200], "bar": 120, "accent": [76, 217, 100],
    "titles":   [ { "name": "title", "title": "EV Companion", "subtitle": "…", "lines": ["…", "…"] } ],
    "captions": [ { "name": "cap01", "text": "…", "sub": "…" } ] }
결과: <dir>/bg.png, <dir>/<name>.png  ("accent" 는 선택: 왼쪽 강조 막대 색)
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONT_BOLD = r"C:\Windows\Fonts\malgunbd.ttf"
FONT_REG = r"C:\Windows\Fonts\malgun.ttf"
BG = (11, 15, 20)
BAR = (16, 21, 27)
ACCENT = (76, 217, 100)
WHITE = (240, 244, 248)
GRAY = (150, 162, 174)


def font(path, size):
    return ImageFont.truetype(path, size)


def fit(draw, text, path, size, max_w, min_size=22):
    while size > min_size and draw.textlength(text, font=font(path, size)) > max_w:
        size -= 2
    return font(path, size)


def caption(spec, out, w, h):
    im = Image.new("RGB", (w, h), BAR)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, 2], fill=(38, 48, 60))
    d.rectangle([40, 28, 48, h - 28], fill=ACCENT)
    text, sub = spec["text"], spec.get("sub", "")
    if sub:
        f1 = fit(d, text, FONT_BOLD, 38, w - 140)
        f2 = fit(d, sub, FONT_REG, 26, w - 140)
        d.text((72, 18), text, font=f1, fill=WHITE)
        d.text((72, 70), sub, font=f2, fill=GRAY)
    else:
        f1 = fit(d, text, FONT_BOLD, 42, w - 140)
        box = d.textbbox((0, 0), text, font=f1)
        d.text((72, (h - (box[3] - box[1])) // 2 - box[1]), text, font=f1, fill=WHITE)
    im.save(out)


def title(spec, out, w, h):
    im = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(im)
    f_title = fit(d, spec["title"], FONT_BOLD, 96, w - 240)
    f_sub = fit(d, spec.get("subtitle", ""), FONT_REG, 44, w - 240)
    lines = spec.get("lines", [])
    y = h // 2 - 150 - 26 * len(lines)
    d.rectangle([120, y + 8, 132, y + 110], fill=ACCENT)
    d.text((160, y), spec["title"], font=f_title, fill=WHITE)
    y += 140
    if spec.get("subtitle"):
        d.text((160, y), spec["subtitle"], font=f_sub, fill=(200, 210, 220))
        y += 90
    for line in lines:
        f = fit(d, line, FONT_REG, 34, w - 320)
        d.text((160, y), line, font=f, fill=GRAY)
        y += 54
    im.save(out)


def main():
    global ACCENT
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    if spec.get("accent"):
        ACCENT = tuple(spec["accent"])
    out_dir = spec["dir"]
    os.makedirs(out_dir, exist_ok=True)
    w, h = spec.get("size", [1920, 1200])
    bar = spec.get("bar", 120)
    Image.new("RGB", (w, h), BG).save(os.path.join(out_dir, "bg.png"))
    for t in spec.get("titles", []):
        title(t, os.path.join(out_dir, t["name"] + ".png"), w, h)
    for c in spec.get("captions", []):
        caption(c, os.path.join(out_dir, c["name"] + ".png"), w, bar)
    print("cards: %d titles, %d captions -> %s" % (len(spec.get("titles", [])), len(spec.get("captions", [])), out_dir))


if __name__ == "__main__":
    main()
