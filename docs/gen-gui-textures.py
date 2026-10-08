# -*- coding: utf-8 -*-
"""生成 GUI 占位纹理（外观与程序化绘制一致，供美术在此基础上重绘）。

⚠️⚠️ 防覆盖保险：已存在且【内容不同】的文件不会被覆盖，必须显式加 --force。
    加这个保险是因为 2026-10-07 直接重跑本脚本，把作者手绘的贴图冲掉了。
    日常使用：先看输出是 WROTE 还是 SKIP。

槽位坐标必须与 *Menu 的 addSlot 完全一致：
    AscensionTableMenu  背包 (89 + col*18, 164 + row*18)   热键栏 (89 + col*18, 218)
    LibraryMenu         背包 (29 + col*18, 164 + row*18)   热键栏 (29 + col*18, 218)
背景凹陷 = 槽位坐标 - 1（凹陷 18x18 包住 16x16 的槽位）。
"""
from PIL import Image, ImageDraw
import io
import os
import sys

FORCE = "--force" in sys.argv
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "ultraenchantment", "textures", "gui")
os.makedirs(OUT, exist_ok=True)

PANEL, BEVEL_LIGHT, BEVEL_DARK = (198,198,198,255), (255,255,255,255), (85,85,85,255)
INSET_BG, INSET_EDGE = (139,139,139,255), (55,55,55,255)
HOVER, OFF_BG = (216,216,216,255), (158,158,158,255)
CHIP_SEL, CHIP_LOCK = (111,168,111,255), (110,110,110,255)
OFF_LIGHT, OFF_DARK = (184,184,184,255), (110,110,110,255)


def save(img, name):
    """写入前比对：相同则跳过，不同则拒绝（除非 --force）。"""
    path = os.path.join(OUT, name)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    data = buf.getvalue()
    if os.path.exists(path):
        with open(path, "rb") as f:
            old = f.read()
        if old == data:
            print("  =     %-26s 未变" % name)
            return
        if not FORCE:
            print("  SKIP  %-26s 【已存在且内容不同】不覆盖（要覆盖加 --force）" % name)
            return
    with open(path, "wb") as f:
        f.write(data)
    print("  WROTE %-26s %s" % (name, img.size))


def panel(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x+w-1, y+h-1], fill=PANEL)
    d.line([x, y, x+w-1, y], fill=BEVEL_LIGHT)
    d.line([x, y, x, y+h-1], fill=BEVEL_LIGHT)
    d.line([x+w-1, y, x+w-1, y+h-1], fill=BEVEL_DARK)
    d.line([x, y+h-1, x+w-1, y+h-1], fill=BEVEL_DARK)


def inset(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x+w-1, y+h-1], fill=INSET_BG)
    d.line([x, y, x+w-1, y], fill=INSET_EDGE)
    d.line([x, y, x, y+h-1], fill=INSET_EDGE)
    d.line([x+w-1, y, x+w-1, y+h-1], fill=BEVEL_LIGHT)
    d.line([x, y+h-1, x+w-1, y+h-1], fill=BEVEL_LIGHT)


def slot(img, x, y):
    inset(img, x - 1, y - 1, 18, 18)


def inv_slots(img, left, top, hotbar_y):
    for c in range(9):
        for r in range(3):
            slot(img, left + c*18, top + r*18)
        slot(img, left + c*18, hotbar_y)


def button(img, x, y, w, h, body, light, dark):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x+w-1, y+h-1], fill=body)
    d.line([x, y, x+w-1, y], fill=light)
    d.line([x, y, x, y+h-1], fill=light)
    d.line([x+w-1, y, x+w-1, y+h-1], fill=dark)
    d.line([x, y+h-1, x+w-1, y+h-1], fill=dark)


def three_states(img, x, y, w, h, step):
    for i, body in enumerate([PANEL, HOVER, OFF_BG]):
        button(img, x, y + i*step, w, h, body,
               BEVEL_LIGHT if i < 2 else OFF_LIGHT, BEVEL_DARK if i < 2 else OFF_DARK)


img = Image.new("RGBA", (340, 236), (0,0,0,0))
panel(img, 0, 0, 340, 236)
slot(img, 10, 18)
inv_slots(img, 89, 164, 218)
inset(img, 10, 70, 320, 88)
save(img, "ascension_table.png")

img = Image.new("RGBA", (260, 236), (0,0,0,0))
panel(img, 0, 0, 260, 236)
slot(img, 10, 14); slot(img, 34, 14)
STRIP_W = (62 - 10 - 2) + 4 * (46 + 2)
inset(img, 10, 40, STRIP_W, 13); inset(img, 10, 55, STRIP_W, 13)
d = ImageDraw.Draw(img)
d.line([10, 70, 249, 70], fill=BEVEL_DARK); d.line([10, 71, 249, 71], fill=BEVEL_LIGHT)
inset(img, 10, 83, 240, 60)
inv_slots(img, 29, 164, 218)
save(img, "enchantment_library.png")

img = Image.new("RGBA", (260, 204), (0,0,0,0))
panel(img, 0, 0, 260, 204)
inset(img, 10, 36, 240, 160)
save(img, "codex.png")

W = Image.new("RGBA", (256, 256), (0,0,0,0))
three_states(W, 0, 0, 100, 16, 18)
three_states(W, 0, 56, 30, 14, 16)
for i, body in enumerate([PANEL, HOVER, OFF_BG]):
    button(W, i*16, 104, 14, 14, body, BEVEL_LIGHT if i < 2 else OFF_LIGHT, BEVEL_DARK if i < 2 else OFF_DARK)
for i, body in enumerate([PANEL, HOVER, OFF_BG]):
    button(W, i*14, 120, 12, 12, body, BEVEL_LIGHT if i < 2 else OFF_LIGHT, BEVEL_DARK if i < 2 else OFF_DARK)
for i, (body, li, dk) in enumerate([(CHIP_LOCK,(138,138,138,255),(74,74,74,255)),
                                    (PANEL,BEVEL_LIGHT,BEVEL_DARK),
                                    (CHIP_SEL,BEVEL_LIGHT,BEVEL_DARK),
                                    (HOVER,BEVEL_LIGHT,BEVEL_DARK)]):
    button(W, i*28, 136, 26, 16, body, li, dk)
d = ImageDraw.Draw(W)
def arrow(x, y, up, color):
    for i in range(5):
        half = i if up else 4 - i
        d.line([x+4-half//2, y+i, x+5+half//2, y+i], fill=color)
arrow(0,154,True,(64,64,64,255)); arrow(0,161,True,(160,160,160,255))
arrow(0,168,False,(64,64,64,255)); arrow(0,175,False,(160,160,160,255))
save(W, "widgets.png")
