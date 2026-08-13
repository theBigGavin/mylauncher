#!/usr/bin/env python3
"""Zune/Metro 风 Android Launcher 效果图 v3
- 多彩"果味"壁纸 + 反白前景
- 图标模拟真实 App 图标的单色化转换(彩色原图 -> 白色)
- 主屏(带长按菜单)、全量应用选择器、横屏 三视图
"""
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------- fonts ----------
def ttc_faces(path):
    faces, i = [], 0
    while i <= 40:
        try:
            f = ImageFont.truetype(path, 40, index=i)
        except Exception:
            break
        faces.append((f.getname()[1] or f.getname()[0], i))
        i += 1
    return faces

def pick(path, size, *prefs):
    for pref in prefs:
        for name, idx in ttc_faces(path):
            if pref.lower() in name.lower():
                return ImageFont.truetype(path, size, index=idx)
    return ImageFont.truetype(path, size, index=0)

HELV    = "/System/Library/Fonts/HelveticaNeue.ttc"
HEITI_L = "/System/Library/Fonts/STHeiti Light.ttc"
HEITI_M = "/System/Library/Fonts/STHeiti Medium.ttc"

F_TIME    = pick(HELV, 190, "UltraLight", "Thin", "Light")
F_TIME_SM = pick(HELV, 140, "UltraLight", "Thin", "Light")
F_DATE    = pick(HEITI_L, 46)
F_DATE_SM = pick(HEITI_L, 38)
F_APP     = pick(HEITI_M, 64)          # 超粗 App 名称
F_APP_SM  = pick(HEITI_M, 52)
F_PICK    = pick(HEITI_M, 46)
F_TITLE   = pick(HEITI_M, 58)
F_MENU    = pick(HEITI_L, 36)
F_STATUS  = pick(HELV, 28, "Medium", "Regular")
F_LABEL   = pick(HEITI_L, 32)
F_NOTE    = pick(HEITI_L, 28)
F_SUB     = pick(HEITI_L, 26)

WHITE   = (255, 255, 255)
WHITE70 = (255, 255, 255, 178)
WHITE10 = (255, 255, 255, 26)
BG      = (24, 24, 24)
FRAME   = (50, 50, 50)
LABEL_C = (150, 150, 150)
NOTE_C  = (110, 110, 110)
INK     = (30, 30, 34)

APPS = [
    ("phone", "电话"), ("message", "信息"), ("browser", "浏览器"), ("camera", "相机"),
    ("photos", "相册"), ("music", "音乐"), ("mail", "邮件"), ("maps", "地图"),
    ("calendar", "日历"), ("notes", "便签"), ("clock", "时钟"), ("settings", "设置"),
]
PICKER_APPS = [
    ("phone", "电话"), ("message", "微信"), ("music", "抖音"), ("browser", "浏览器"),
    ("camera", "相机"), ("photos", "相册"), ("mail", "邮件"), ("maps", "地图"),
    ("calendar", "日历"), ("notes", "便签"), ("clock", "时钟"), ("settings", "设置"),
    ("message", "信息"), ("music", "音乐"), ("browser", "微博"),
]
MAX_APPS = 20

# ---------- 高清锐利壁纸:撞色斜带 + 锐利线条(无模糊,逐像素硬边) ----------
def mesh_wallpaper(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)

    # 撞色斜带:62° 陡角,逐行扫描填充,边缘零抗锯齿、绝对锐利
    theta = math.radians(62)
    ct, st = math.cos(theta), math.sin(theta)
    corners = [0.0, w * ct, h * st, w * ct + h * st]
    tmin, tmax = min(corners), max(corners)
    # (带宽占比, 颜色) —— 深色打底,撞色细带做锐利点缀
    bands = [
        (0.200, ( 18,  14,  44)),   # 深靛
        (0.035, (255,  61,   0)),   # 橙红
        (0.130, ( 26,  18,  58)),   # 暗紫
        (0.016, (  0, 229, 200)),   # 青(细)
        (0.240, ( 14,  11,  36)),   # 深
        (0.055, (255,   0, 140)),   # 品红
        (0.100, ( 22,  16,  50)),   # 暗
        (0.012, (204, 255,   0)),   # 荧光绿(极细)
        (0.180, ( 16,  12,  40)),   # 深靛
        (0.028, (255,  94,  58)),   # 橙
    ]
    period = sum(b[0] for b in bands) * h
    segs = []
    t = math.floor(tmin / period) * period
    while t < tmax + period:
        for frac, color in bands:
            segs.append((t, t + frac * h, color))
            t += frac * h
    for y in range(h):
        t0 = y * st
        # 找 x=0 所在色带
        ci = next(i for i, (a, b, _) in enumerate(segs) if a <= t0 < b)
        x_cur = 0
        color_cur = segs[ci][2]
        # 该行的所有跨界点
        j = ci + 1
        while j < len(segs):
            c = segs[j][0]
            x = (c - y * st) / ct
            if x > w:
                break
            if 0 < x <= w:
                d.rectangle([x_cur, y, int(x), y], fill=color_cur)
                x_cur = int(x)
                color_cur = segs[j][2]
            j += 1
        if x_cur < w:
            d.rectangle([x_cur, y, w, y], fill=color_cur)

    # 锐利细线:反方向穿过,制造冲突感
    for k, (frac_y, color, lw) in enumerate([
        (0.16, (204, 255,   0), 5),
        (0.19, (255, 255, 255), 2),
        (0.78, (  0, 229, 200), 4),
    ]):
        y0 = frac_y * h
        slope = math.tan(math.radians(-24))
        d.line([(-20, y0), (w + 20, y0 + (w + 40) * slope)], fill=color, width=lw)

    # 硬边圆环,部分出画
    rcx, rcy, rr = 0.86 * w, 0.10 * h, 0.30 * min(w, h)
    d.ellipse([rcx - rr, rcy - rr, rcx + rr, rcy + rr], outline=(255, 214, 0), width=max(6, h // 200))
    return img.convert("RGBA")

# ---------- 白色线性 glyph ----------
def glyph(kind, size, color=WHITE):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = max(4, size // 13)
    c = size / 2
    u = size / 80.0
    def P(x, y): return (x * u, y * u)
    if kind == "phone":
        d.arc([P(14, 14), P(66, 66)], 110, 290, fill=color, width=int(w * 1.6))
        r = 7 * u
        for ang in (115, 285):
            x = c + 26 * u * math.cos(math.radians(ang))
            y = c + 26 * u * math.sin(math.radians(ang))
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)
    elif kind == "message":
        d.rounded_rectangle([P(12, 18), P(68, 56)], radius=8 * u, outline=color, width=w)
        d.polygon([P(24, 55), P(24, 70), P(38, 55)], fill=color)
    elif kind == "browser":
        d.ellipse([P(12, 12), P(68, 68)], outline=color, width=w)
        d.ellipse([P(28, 12), P(52, 68)], outline=color, width=w)
        d.line([P(12, 40), P(68, 40)], fill=color, width=w)
    elif kind == "camera":
        d.rounded_rectangle([P(10, 24), P(70, 62)], radius=6 * u, outline=color, width=w)
        d.polygon([P(28, 24), P(34, 14), P(46, 14), P(52, 24)], outline=color, width=w)
        d.ellipse([P(30, 32), P(50, 52)], outline=color, width=w)
    elif kind == "photos":
        d.rectangle([P(12, 14), P(68, 66)], outline=color, width=w)
        d.ellipse([P(48, 22), P(58, 32)], outline=color, width=max(2, w - 1))
        d.line([P(14, 58), P(32, 40), P(44, 52), P(54, 42), P(68, 56)], fill=color, width=w, joint="curve")
    elif kind == "music":
        d.line([P(30, 20), P(30, 54)], fill=color, width=w)
        d.line([P(58, 14), P(58, 48)], fill=color, width=w)
        d.line([P(30, 20), P(58, 14)], fill=color, width=int(w * 1.4))
        d.ellipse([P(16, 50), P(32, 62)], fill=color)
        d.ellipse([P(44, 44), P(60, 56)], fill=color)
    elif kind == "mail":
        d.rectangle([P(10, 20), P(70, 60)], outline=color, width=w)
        d.line([P(10, 22), P(40, 44), P(70, 22)], fill=color, width=w, joint="curve")
    elif kind == "maps":
        d.polygon([P(40, 72), P(24, 42), P(56, 42)], fill=color)
        d.ellipse([P(22, 10), P(58, 46)], fill=color)
        d.ellipse([P(32, 20), P(48, 36)], fill=(0, 0, 0, 0))
    elif kind == "calendar":
        d.rectangle([P(12, 18), P(68, 68)], outline=color, width=w)
        d.line([P(12, 32), P(68, 32)], fill=color, width=w)
        d.line([P(26, 10), P(26, 24)], fill=color, width=w)
        d.line([P(54, 10), P(54, 24)], fill=color, width=w)
        for gx in (24, 40, 56):
            for gy in (44, 56):
                x, y = P(gx, gy)
                d.ellipse([x - 3 * u, y - 3 * u, x + 3 * u, y + 3 * u], fill=color)
    elif kind == "notes":
        d.rectangle([P(16, 12), P(64, 68)], outline=color, width=w)
        for yy in (28, 40, 52):
            d.line([P(26, yy), P(54, yy)], fill=color, width=max(2, w - 1))
    elif kind == "clock":
        d.ellipse([P(12, 12), P(68, 68)], outline=color, width=w)
        d.line([P(40, 40), P(40, 22)], fill=color, width=w)
        d.line([P(40, 40), P(54, 48)], fill=color, width=w)
    elif kind == "settings":
        for i in range(8):
            a = math.radians(i * 45)
            d.line([c + 16 * u * math.cos(a), c + 16 * u * math.sin(a),
                    c + 28 * u * math.cos(a), c + 28 * u * math.sin(a)], fill=color, width=int(w * 1.3))
        d.ellipse([P(26, 26), P(54, 54)], outline=color, width=w)
    return img

# ---------- 模拟真实 App 图标 -> 单色化 ----------
ICON_COLORS = {
    "phone":    (( 52, 199,  89), ( 28, 160,  70)),
    "message":  (( 90, 200, 250), ( 10, 132, 255)),
    "browser":  ((100, 210, 255), ( 64,  80, 230)),
    "camera":   ((120, 120, 128), ( 48,  48,  56)),
    "photos":   ((255, 179,  64), (255,  94,  98)),
    "music":    ((255, 105, 140), (235,  51,  73)),
    "mail":     (( 85, 205, 255), ( 30, 110, 245)),
    "maps":     (( 48, 219,  91), ( 10, 160, 120)),
    "calendar": ((255, 120, 110), (225,  60,  57)),
    "notes":    ((255, 214,  10), (255, 159,  10)),
    "clock":    (( 58,  58,  66), ( 12,  12,  18)),
    "settings": ((160, 160, 170), ( 90,  90, 100)),
}

def fake_app_icon(kind, size):
    """彩色圆角方形'真实'图标 + 白色 glyph。"""
    c0, c1 = ICON_COLORS[kind]
    grad = Image.new("RGB", (1, size))
    px = grad.load()
    for y in range(size):
        t = y / max(1, size - 1)
        px[0, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    tile = grad.resize((size, size)).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.24, fill=255)
    tile.putalpha(mask)
    g = glyph(kind, int(size * 0.66))
    tile.paste(g, ((size - g.width) // 2, (size - g.height) // 2), g)
    return tile

def to_mono_white(rgba_icon):
    """单色化:亮度 -> 透明度,颜色纯白(真机上对 App 原图标做同样转换)。
    高次幂曲线压低彩色底的亮度,只保留白色 glyph 部分。"""
    lum = rgba_icon.convert("L").point(lambda v: min(255, int((v / 255) ** 2.1 * 255 * 1.2)))
    alpha = rgba_icon.getchannel("A")
    from PIL import ImageChops
    lum = ImageChops.multiply(lum, alpha)
    out = Image.new("RGBA", rgba_icon.size, WHITE + (0,))
    out.putalpha(lum)
    return out

MONO = {}   # (kind, size) 缓存单色图标
def mono_icon(kind, size):
    key = (kind, size)
    if key not in MONO:
        MONO[key] = to_mono_white(fake_app_icon(kind, size))
    return MONO[key]

# ---------- 工具 ----------
def text_w(d, s, f):
    b = d.textbbox((0, 0), s, font=f)
    return b[2] - b[0], b

def draw_statusbar(d, sw, x_right, y):
    t = "5G"
    tw, _ = text_w(d, t, F_STATUS)
    d.text((x_right - tw - 44, y), t, font=F_STATUS, fill=WHITE)
    bx, by = x_right - 36, y + 2
    d.rounded_rectangle([bx, by, bx + 30, by + 16], radius=4, outline=WHITE, width=2)
    d.rectangle([bx + 32, by + 5, bx + 36, by + 11], fill=WHITE)
    d.rectangle([bx + 3, by + 3, bx + 24, by + 13], fill=WHITE)

def draw_clock_centered(d, sw, y):
    t = "10:24"
    tw, tb = text_w(d, t, F_TIME)
    d.text(((sw - tw) / 2 - tb[0], y), t, font=F_TIME, fill=WHITE)
    s = "星期四 · 8月13日"
    swd, sb = text_w(d, s, F_DATE)
    dy = y + tb[3] + 26
    d.text(((sw - swd) / 2 - sb[0], dy), s, font=F_DATE, fill=WHITE70)
    return dy + 56

def draw_clock_bottomleft(d, y_bottom):
    t = "10:24"
    _, tb = text_w(d, t, F_TIME_SM)
    d.text((56, y_bottom - tb[3] - 72), t, font=F_TIME_SM, fill=WHITE)
    d.text((60, y_bottom - 52), "星期四 · 8月13日", font=F_DATE_SM, fill=WHITE70)

def draw_row_left(d, screen, x, y, kind, name, tile, font):
    ic = mono_icon(kind, tile)
    screen.paste(ic, (x, y), ic)
    tb = d.textbbox((0, 0), name, font=font)
    d.text((x + tile + 34, y + (tile - (tb[3] - tb[1])) / 2 - tb[1]), name, font=font, fill=WHITE)

def draw_row_split_right(d, screen, icon_x, name_right, y, kind, name, tile, font):
    """横屏:图标固定一列(垂线对齐),名称右对齐一列。"""
    ic = mono_icon(kind, tile)
    screen.paste(ic, (icon_x, y), ic)
    tw, tb = text_w(d, name, font)
    d.text((name_right - tw - tb[0], y + (tile - (tb[3] - tb[1])) / 2 - tb[1]), name, font=font, fill=WHITE)

def phone(canvas, ox, oy, w, h):
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle([ox, oy, ox + w, oy + h], radius=64, fill=FRAME)
    m = 10
    screen = mesh_wallpaper(w - 2 * m, h - 2 * m)
    canvas.paste(screen, (ox + m, oy + m))
    return screen, ox + m, oy + m

def caption(d, cx, y, s):
    lw, lbb = text_w(d, s, F_LABEL)
    d.text((cx - lw / 2 - lbb[0], y), s, font=F_LABEL, fill=LABEL_C)

# ---------- canvas ----------
W, H = 2020, 3120
canvas = Image.new("RGBA", (W, H), BG + (255,))
d = ImageDraw.Draw(canvas)

PW, PH = 840, 1760
PAD_TOP = 160

# ============ 1. 竖屏主屏(带长按菜单) ============
P1X, P1Y = 80, PAD_TOP
caption(d, P1X + PW / 2, P1Y - 74, "主屏 · 长按弹出菜单")
p1, p1x, p1y = phone(canvas, P1X, P1Y, PW, PH)
p1d = ImageDraw.Draw(p1)
sw = PW - 20
draw_statusbar(p1d, sw, sw - 56, 34)
y = draw_clock_centered(p1d, sw, 116) + 34
ROW_P, TILE_P = 104, 76
row_y = []
for kind, name in APPS:
    row_y.append(y)
    draw_row_left(p1d, p1, 64, y, kind, name, TILE_P, F_APP)
    y += ROW_P
hint = f"— {len(APPS)} / {MAX_APPS} —"
hw, hb = text_w(p1d, hint, F_SUB)
p1d.text(((sw - hw) / 2 - hb[0], PH - 20 - 64), hint, font=F_SUB, fill=WHITE70)

# 长按菜单(覆盖在第 3 行附近)
menu_items = ["替换应用", "修改名称", "移除"]
mw, mh = 300, 64 * len(menu_items) + 28
mx, my = 330, row_y[2] - 10
shadow = Image.new("RGBA", p1.size, (0, 0, 0, 0))
ImageDraw.Draw(shadow).rounded_rectangle([mx + 8, my + 12, mx + mw + 8, my + mh + 12], radius=28, fill=(0, 0, 0, 90))
p1.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(14)))
p1d.rounded_rectangle([mx, my, mx + mw, my + mh], radius=28, fill=(250, 250, 252, 255))
for i, it in enumerate(menu_items):
    p1d.text((mx + 40, my + 18 + i * 64), it, font=F_MENU, fill=INK)
    if i:
        p1d.line([mx + 28, my + 14 + i * 64, mx + mw - 28, my + 14 + i * 64], fill=(0, 0, 0, 26), width=2)
canvas.paste(p1, (p1x, p1y))

# ============ 2. 全量应用选择器 ============
P2X, P2Y = 80 + PW + 120, PAD_TOP
caption(d, P2X + PW / 2, P2Y - 74, "替换应用 · 全量列表(同款样式)")
p2, p2x, p2y = phone(canvas, P2X, P2Y, PW, PH)
p2d = ImageDraw.Draw(p2)
draw_statusbar(p2d, sw, sw - 56, 34)
t = "选择应用"
tw, tb = text_w(p2d, t, F_TITLE)
p2d.text(((sw - tw) / 2 - tb[0], 110), t, font=F_TITLE, fill=WHITE)
sub = "共 96 个应用 · 点选即替换"
swd2, sb2 = text_w(p2d, sub, F_SUB)
p2d.text(((sw - swd2) / 2 - sb2[0], 196), sub, font=F_SUB, fill=WHITE70)
ROW_PK, TILE_PK = 100, 64
yy = 268
sel = 1  # 高亮"微信"
for i, (kind, name) in enumerate(PICKER_APPS):
    if i == sel:
        hl = Image.new("RGBA", p2.size, (0, 0, 0, 0))
        ImageDraw.Draw(hl).rounded_rectangle([30, yy - 14, sw - 30, yy + ROW_PK - 18], radius=22, fill=WHITE10)
        p2.alpha_composite(hl)
    draw_row_left(p2d, p2, 60, yy, kind, name, TILE_PK, F_PICK)
    yy += ROW_PK
    if yy > PH - 20 - 90:
        break
more = "↓ 继续滑动"
mw2, mb2 = text_w(p2d, more, F_SUB)
p2d.text(((sw - mw2) / 2 - mb2[0], PH - 20 - 64), more, font=F_SUB, fill=WHITE70)
canvas.paste(p2, (p2x, p2y))

# ============ 3. 横屏 ============
LW, LH = 1760, 840
L3X, L3Y = (W - LW) // 2, PAD_TOP + PH + 150
caption(d, W / 2, L3Y - 74, "横屏 · 图标一列垂线对齐 / 名称一列右对齐")
p3, p3x, p3y = phone(canvas, L3X, L3Y, LW, LH)
p3d = ImageDraw.Draw(p3)
lsw = LW - 20
draw_statusbar(p3d, lsw, lsw - 56, 26)
draw_clock_bottomleft(p3d, LH - 20 - 44)
ROW_L, TILE_L = 96, 56
name_right = lsw - 72
icon_x = name_right - 340          # 图标列固定 x(与名称列分开,保持垂线对齐)
visible = (LH - 20 - 150) // ROW_L
for i, (kind, name) in enumerate(APPS[:visible]):
    draw_row_split_right(p3d, p3, icon_x, name_right, 84 + i * ROW_L, kind, name, TILE_L, F_APP_SM)
rest = len(APPS) - visible
hint = f"↓ 还有 {rest} 个 · 共 {len(APPS)} / {MAX_APPS}"
hw3, hb3 = text_w(p3d, hint, F_SUB)
p3d.text((name_right - hw3 - hb3[0], 84 + visible * ROW_L + 18), hint, font=F_SUB, fill=WHITE70)
canvas.paste(p3, (p3x, p3y))

# ---------- footer ----------
note = "· 长按:替换 / 改名 / 移除 · 图标可隐藏 · 图标与字号大小可调 · 图标来自 App 原图单色化 · 最多 20 个 ·"
nw, nb = text_w(d, note, F_NOTE)
d.text(((W - nw) / 2 - nb[0], H - 88), note, font=F_NOTE, fill=NOTE_C)

canvas.convert("RGB").save("mockup.png", optimize=True)
print("saved mockup.png", canvas.size)
