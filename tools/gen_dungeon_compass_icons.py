"""
Builds the Dungeon Compass's 32 needle frames and item models from vanilla's compass, so it reads as
the Dungeon Map's companion in a hotbar:
    python tools/gen_dungeon_compass_icons.py

The shape and needle are vanilla's, pixel for pixel; only the iron casing changes, to an aged bronze
in the Dungeon Map's browns (MAP_TINT in DungeonMaps, BROWNS in gen_dungeon_map_icon.py). Each of
vanilla's greys maps to a bronze of the same rank, lightest to darkest, so the shading reads exactly
as it does on the vanilla compass. The red needle is kept.

The models copy vanilla's compass.json overrides, angle for angle, pointed at D2's own frames.

Reads from the Minecraft client jar ForgeGradle caches; writes
src/main/resources/assets/dungeons2/textures/item/dungeon_compass_NN.png and
src/main/resources/assets/dungeons2/models/item/dungeon_compass[_NN].json. Overwritten every run.
"""
import io
import json
import os
import re
import zipfile

from PIL import Image

JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
TEXTURES = "src/main/resources/assets/dungeons2/textures/item"
MODELS = "src/main/resources/assets/dungeons2/models/item"

# vanilla's greys, lightest first -> aged bronze, lightest first
BRONZES = {
    (255, 255, 255): (250, 226, 160),
    (216, 216, 216): (226, 190, 118),
    (168, 168, 168): (196, 150, 82),
    (130, 130, 130): (160, 116, 58),
    (100, 100, 100): (132, 92, 46),
    (94, 94, 94): (120, 84, 44),
    (79, 77, 77): (98, 68, 38),
    (53, 53, 53): (72, 50, 30),
    (47, 47, 47): (62, 43, 26),
    (24, 23, 23): (34, 24, 16),
}


def is_grey(rgb):
    return max(rgb) - min(rgb) <= 4


def frame(src):
    out = Image.new("RGBA", src.size)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            if not a:
                out.putpixel((x, y), (0, 0, 0, 0))
            elif is_grey((r, g, b)):
                if (r, g, b) not in BRONZES:
                    raise SystemExit(f"unmapped vanilla grey {(r, g, b)}")
                out.putpixel((x, y), BRONZES[(r, g, b)] + (a,))
            else:
                out.putpixel((x, y), (r, g, b, a))  # the needle
    return out


def write_json(path, obj):
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    with zipfile.ZipFile(JAR) as jar:
        for n in range(32):
            name = f"compass_{n:02d}"
            src = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/item/{name}.png"))).convert("RGBA")
            frame(src).save(f"{TEXTURES}/dungeon_{name}.png")
            if n == 16:
                continue  # frame 16 is the base model's own texture; no override names a model for it
            write_json(f"{MODELS}/dungeon_{name}.json", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"dungeons2:item/dungeon_{name}"},
            })
        vanilla = json.loads(jar.read("assets/minecraft/models/item/compass.json"))

    def ours(ref):
        m = re.fullmatch(r"(?:minecraft:)?item/compass(_\d\d)?", ref)
        if not m:
            raise SystemExit(f"unexpected vanilla model ref {ref}")
        return f"dungeons2:item/dungeon_compass{m.group(1) or ''}"

    write_json(f"{MODELS}/dungeon_compass.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": ours(vanilla["textures"]["layer0"])},
        "overrides": [{"predicate": o["predicate"], "model": ours(o["model"])} for o in vanilla["overrides"]],
    })
    print("wrote 32 frames and", len(vanilla["overrides"]), "overrides")


if __name__ == "__main__":
    main()
