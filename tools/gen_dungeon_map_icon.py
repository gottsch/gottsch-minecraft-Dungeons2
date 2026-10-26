"""
Builds the Blank Dungeon Map's item icon from vanilla's empty map, so the two are told apart in a
hotbar at a glance:
    python tools/gen_dungeon_map_icon.py

The shape is vanilla's, pixel for pixel; only the parchment changes, from vanilla's pale green-cream
to an older, browner sheet - the colour of the Dungeon Map's own markings (MAP_TINT in DungeonMaps).
Each of vanilla's six shades maps to a brown of the same rank, lightest to darkest, so the shading
reads exactly as it does on the vanilla map.

Reads the texture from the Minecraft client jar ForgeGradle caches; writes
src/main/resources/assets/dungeons2/textures/item/blank_dungeon_map.png. Overwritten every run.
"""
import io
import os
import zipfile

from PIL import Image

JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = "src/main/resources/assets/dungeons2/textures/item/blank_dungeon_map.png"

# vanilla's shades, lightest first -> aged parchment, lightest first
BROWNS = [(236, 214, 170), (214, 186, 138), (196, 164, 116), (160, 124, 80), (128, 94, 58), (98, 70, 42)]


def main():
    with zipfile.ZipFile(JAR) as jar:
        src = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/map.png"))).convert("RGBA")
    shades = sorted({p[:3] for p in src.getdata() if p[3]}, key=lambda c: -sum(c))
    if len(shades) != len(BROWNS):
        raise SystemExit(f"vanilla map.png has {len(shades)} shades, expected {len(BROWNS)}")
    remap = dict(zip(shades, BROWNS))
    out = Image.new("RGBA", src.size)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            out.putpixel((x, y), remap[(r, g, b)] + (a,) if a else (0, 0, 0, 0))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
