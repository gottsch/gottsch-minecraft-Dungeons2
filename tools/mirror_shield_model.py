# Regenerates the Mirror Shield back texture and element models from textures/item/mirror_shield.png.
# Run from src/main/resources/assets/dungeons2:  python ../../../../../tools/mirror_shield_model.py preview.png
import json, sys
from PIL import Image
front = Image.open('textures/item/mirror_shield.png').convert('RGBA')
W = H = 16
op = lambda x, y: 0 <= x < W and 0 <= y < H and front.getpixel((x, y))[3] > 0

# --- back texture: same silhouette, frame kept, panel plain --------------------------------
def edge_dist(x, y):
    for d in range(1, 4):
        if any(not op(x + dx, y + dy) for dx in range(-d, d + 1) for dy in range(-d, d + 1)):
            return d
    return 4
PANEL, SEAM = (24, 8, 15, 255), (40, 14, 22, 255)
STRAP, STRAP_HI = (118, 47, 44, 255), (147, 72, 56, 255)
back = Image.new('RGBA', (W, H), (0, 0, 0, 0))
for y in range(H):
    for x in range(W):
        if not op(x, y):
            continue
        if edge_dist(x, y) <= 2:
            # Mirror the frame pixel: the back is seen from behind, so column x is front column x.
            back.putpixel((x, y), front.getpixel((x, y)))
        else:
            back.putpixel((x, y), SEAM if x in (5, 10) else PANEL)
# the handle's strap, which the handle element samples
for y in range(4, 12):
    for x in (7, 8):
        back.putpixel((x, y), STRAP_HI if x == 7 else STRAP)
back.save('textures/item/mirror_shield_back.png')
back.resize((256, 256), Image.NEAREST).save(sys.argv[1])

# --- plate elements: one per run of rows with identical opaque span -------------------------
spans = []
for y in range(H):
    xs = [x for x in range(W) if op(x, y)]
    spans.append((min(xs), max(xs)) if xs else None)
runs, y = [], 0
while y < H:
    if spans[y] is None:
        y += 1; continue
    y1 = y
    while y1 + 1 < H and spans[y1 + 1] == spans[y]:
        y1 += 1
    runs.append((spans[y], y, y1)); y = y1 + 1

elements = []
for (c0, c1), r0, r1 in runs:
    x0, x1 = c0 - 8, c1 + 1 - 8          # texture column -> model x, centred on 0
    ya, yb = 8 - (r1 + 1), 8 - r0         # texture row (down) -> model y (up), centred on 0
    elements.append({
        "from": [x0, ya, 1], "to": [x1, yb, 2],
        "faces": {
            "south": {"uv": [c0, r0, c1 + 1, r1 + 1], "texture": "#front"},
            "north": {"uv": [c1 + 1, r0, c0, r1 + 1], "texture": "#back"},
            "west":  {"uv": [c0, r0, c0 + 1, r1 + 1], "texture": "#front"},
            "east":  {"uv": [c1, r0, c1 + 1, r1 + 1], "texture": "#front"},
            "up":    {"uv": [c0, r0, c1 + 1, r0 + 1], "texture": "#front"},
            "down":  {"uv": [c0, r1, c1 + 1, r1 + 1], "texture": "#front"},
        }})
# Handle behind the plate, where vanilla's is: x -1..1, y -3..3, z -5..1.
strap = {"uv": [7, 5, 9, 11], "texture": "#back"}
elements.append({"from": [-1, -3, -5], "to": [1, 3, 1],
                 "faces": {f: strap for f in ("north", "south", "east", "west", "up", "down")}})

textures = {"front": "dungeons2:item/mirror_shield", "back": "dungeons2:item/mirror_shield_back",
            "particle": "dungeons2:item/mirror_shield"}
# Vanilla shield.json / shield_blocking.json display, verbatim: the geometry above sits where
# vanilla's ShieldModel does after its BEWLR flip, so vanilla's hand poses fit it unchanged.
held = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
blocking = {
    "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}
base = {"gui_light": "front", "textures": textures, "elements": elements}
json.dump({**base, "display": held,
           "overrides": [{"predicate": {"blocking": 1}, "model": "dungeons2:item/mirror_shield_blocking"}]},
          open('models/item/mirror_shield.json', 'w'), indent=2)
json.dump({**base, "display": blocking}, open('models/item/mirror_shield_blocking.json', 'w'), indent=2)
print(len(elements), "elements")
