"""
Makes all the Android app icons from design/icon.jpg.

Run from the project folder:   python design/make_icons.py
Needs Pillow:                   pip install pillow

What it makes:
  - adaptive icon layers (Android 8+): the picture as the foreground, a navy background
  - a single-colour "themed icon" (Android 13+) from the bus outline
  - the classic square and round icons for every screen density
  - design/play_store_icon_512.png for Google Play (with the text)
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "icon.jpg"
RES = ROOT / "app" / "src" / "main" / "res"

# Pixel sizes per screen density. An adaptive icon layer is 108dp; a classic icon 48dp.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

source = Image.open(SOURCE).convert("RGB")
size = source.width  # 1024

# The home-screen icon: everything above the "Kastoria Buses" text (too small to read
# on a phone, and the app name is written under the icon anyway). A square that keeps
# the whole bus and its wheels, centred on the bus.
crop_size = int(size * 0.86)                  # 880 px of 1024
left = (size - crop_size) // 2
artwork = source.crop((left, 0, left + crop_size, crop_size))

# Background colour for the adaptive icon: the average colour along the picture's edge,
# so if a launcher moves the layers a little, the gap blends in.
edge = Image.new("L", artwork.size, 0)
ImageDraw.Draw(edge).rectangle([0, 0, artwork.width - 1, artwork.height - 1], outline=255, width=6)
r, g, b = [int(sum(c) / len(c)) for c in zip(*[p for p, m in zip(artwork.getdata(), edge.getdata()) if m])]
background_hex = f"#{r:02X}{g:02X}{b:02X}"


def foreground_layer(px: int) -> Image.Image:
    """108dp layer: the artwork fills the 72dp part that is visible on every phone."""
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    inner = round(px * 72 / 108)
    art = artwork.resize((inner, inner), Image.LANCZOS).convert("RGBA")
    layer.paste(art, ((px - inner) // 2, (px - inner) // 2))
    return layer


# Themed icon: the bus body is pure black in the picture; keep only that as a white
# shape (Android colours it to match the wallpaper). Window and lights stay as holes.
bus_mask = artwork.convert("L").point(lambda v: 255 if v < 22 else 0)
# "Opening" (shrink, then grow back): thin lines like the birds' outlines and the
# drawn town disappear, the thick bus body stays.
bus_mask = bus_mask.filter(ImageFilter.MinFilter(11)).filter(ImageFilter.MaxFilter(11))
bus_box = bus_mask.getbbox()


def monochrome_layer(px: int) -> Image.Image:
    """108dp layer with the bus silhouette inside the 66dp safe zone."""
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    shape = bus_mask.crop(bus_box)
    fit = px * 56 / 108                                      # a bit of room inside the 66dp circle
    scale = fit / max(shape.size)
    shape = shape.resize((round(shape.width * scale), round(shape.height * scale)), Image.LANCZOS)
    white = Image.new("RGBA", shape.size, (255, 255, 255, 255))
    layer.paste(white, ((px - shape.width) // 2, (px - shape.height) // 2), shape)
    return layer


def classic_icon(px: int, round_mask: bool) -> Image.Image:
    """Old-style icon (shown by some launchers and tools): rounded square or circle."""
    big = px * 4                                             # draw big, then shrink: smooth edges
    art = artwork.resize((big, big), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(mask)
    if round_mask:
        draw.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=big // 6, fill=255)
    art.putalpha(ImageChops.multiply(art.getchannel("A"), mask))
    return art.resize((px, px), Image.LANCZOS)


for name, factor in DENSITIES.items():
    folder = RES / f"mipmap-{name}"
    folder.mkdir(exist_ok=True)
    layer_px = round(108 * factor)
    icon_px = round(48 * factor)
    foreground_layer(layer_px).save(folder / "ic_launcher_foreground.png", optimize=True)
    monochrome_layer(layer_px).save(folder / "ic_launcher_monochrome.png", optimize=True)
    classic_icon(icon_px, round_mask=False).save(folder / "ic_launcher.webp", quality=95)
    classic_icon(icon_px, round_mask=True).save(folder / "ic_launcher_round.webp", quality=95)

# The adaptive icon description and its background colour.
adaptive = """<?xml version="1.0" encoding="utf-8"?>
<!-- Made by design/make_icons.py from design/icon.jpg -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    (RES / "mipmap-anydpi" / name).write_text(adaptive, encoding="utf-8")

(RES / "values" / "ic_launcher_background.xml").write_text(f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Made by design/make_icons.py: the average edge colour of design/icon.jpg -->
<resources>
    <color name="ic_launcher_background">{background_hex}</color>
</resources>
""", encoding="utf-8")

# Google Play wants a 512 x 512 PNG; there the icon is big, so keep the whole picture.
source.resize((512, 512), Image.LANCZOS).save(ROOT / "design" / "play_store_icon_512.png", optimize=True)

print("background colour:", background_hex, "| bus outline box:", bus_box)
