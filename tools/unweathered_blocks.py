#!/usr/bin/env python3
"""Which blocks in a structure template have no weathering rule?

    python tools/unweathered_blocks.py <template.nbt|dir> [...] [-p <processor_list.json> ...]

    # every shipped template, against every shipped processor list (both defaults)
    python tools/unweathered_blocks.py

    # one prefab against the boss list
    python tools/unweathered_blocks.py src/main/resources/data/dungeons2/structures/rooms/classic/hall_9x9_1.nbt \\
        -p src/main/resources/data/dungeons2/worldgen/processor_list/classic_boss_weathering_large.json

WHY THIS EXISTS
---------------
A template's palette and the processor list that ages it are authored in different files by
different kinds of decision, and nothing connects them. Author a room out of a block the list has
never heard of and it simply arrives in game looking brand new next to walls that have crumbled --
no error, no warning, and it reads as "the weathering is broken" rather than "that block was never
in the table".

The failure is one-directional and that is what makes it worth a tool: the JSON side is guarded
(an unknown block id in a rule is a load error), but the NBT side is not, because a palette is just
a list of blocks nobody has to justify.

WHAT COUNTS AS "HAS A RULE"
---------------------------
A block is covered if any processor in the list could act on it:

  * `dungeons2:surface_aging` -- named as a rule's `block`, at any `surface`.
  * `minecraft:rule` -- named in an `input_predicate`'s `block`, or in its `blocks` list.
  * `dungeons2:decoration` / `*_sweep` -- named in any of their block palettes. These do not
    TRANSFORM the block, they grow things on it or clean things off it, so a block that only
    appears here is reported separately rather than counted as aged.

Being an OUTPUT is deliberately not coverage. `mossy_cobblestone` appearing as some rule's
`output_blocks` says the list can produce it, not that it ages when authored -- and a template
built from pre-mossed blocks that never decay further is exactly the case worth seeing.

WHAT IT CANNOT TELL YOU
-----------------------
Whether a block SHOULD age. Structure voids, jigsaws, air, light blocks and the markers this mod
reads are all legitimately unaged, so they are filtered by default (`--all` keeps them). Beyond
that it is a list to read, not a list to fix: a chest is meant to look new.
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict

try:
    import nbtlib
except ImportError:
    sys.exit("nbtlib is required: python -m pip install nbtlib")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "dungeons2")
DEFAULT_TEMPLATES = os.path.join(DATA, "structures")
#: EVERY shipped list, not just the room one. A block is only really uncovered if NOTHING ages it:
#: the mud band, the entrance and the three boss rooms each carry their own table, so checking one
#: list against every template reports a mud brick as unaged because it is aged somewhere else.
#: Narrow it with -p when the question is "does THIS list cover THIS prefab".
PROCESSOR_LIST_DIR = os.path.join(DATA, "worldgen", "processor_list")
DEFAULT_LISTS = sorted(
    os.path.join(PROCESSOR_LIST_DIR, name)
    for name in (os.listdir(PROCESSOR_LIST_DIR) if os.path.isdir(PROCESSOR_LIST_DIR) else [])
    if name.endswith(".json"))

#: Never expected to weather. Matched exactly, or as a prefix when the entry ends in ':'.
EXEMPT = {
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
    "minecraft:structure_void", "minecraft:structure_block", "minecraft:jigsaw",
    "minecraft:barrier", "minecraft:light", "minecraft:water", "minecraft:lava",
    "minecraft:chest", "minecraft:trapped_chest", "minecraft:spawner",
    "minecraft:bedrock",
}
#: Marker blocks this mod reads and replaces during generation -- gone before weathering matters.
EXEMPT_PREFIXES = ("dungeons2:",)


def strip_comments(text):
    """The shipped processor lists carry `//` comments, which json.loads rejects."""
    return re.sub(r"^\s*//.*$", "", text, flags=re.M)


def load_list(path):
    with open(path, encoding="utf-8") as handle:
        return json.loads(strip_comments(handle.read()))


def rule_blocks(processors):
    """Blocks a list can TRANSFORM, and blocks it merely decorates, as two sets."""
    aged, decorated = set(), set()
    for processor in processors:
        kind = processor.get("processor_type", "")
        if kind == "dungeons2:surface_aging":
            for rule in processor.get("rules", []):
                if "block" in rule:
                    aged.add(rule["block"])
        elif kind == "minecraft:rule":
            for rule in processor.get("rules", []):
                predicate = rule.get("input_predicate", {})
                if "block" in predicate:
                    aged.add(predicate["block"])
                for block in predicate.get("blocks", []):
                    aged.add(block)
        elif kind.startswith("dungeons2:") and (
                "decoration" in kind or kind.endswith("_sweep")):
            for value in processor.values():
                if isinstance(value, dict):
                    for entry in value.get("blocks", []):
                        if not isinstance(entry, dict):
                            decorated.add(entry)          # a bare id
                        elif "block" in entry:
                            decorated.add(entry["block"])  # weighted {block, weight}
                        # else: a weighted {entity, weight}. #54 grows the Shrieker and the
                        # Violet Fungus as MOBS, so the palette names an entity and there is no
                        # block to be covered or uncovered.
    return aged, decorated


def palette_of(path):
    """Every block id in a structure template's palette(s), with a count of placed blocks."""
    root = nbtlib.load(path)
    # `palettes` (plural) is vanilla's variant form -- one palette per random variation. Reading
    # only `palette` there would silently miss every block unique to variants 2..n.
    palettes = root["palettes"] if "palettes" in root else [root["palette"]]
    counts = defaultdict(int)
    per_state = []
    for palette in palettes:
        names = [str(entry["Name"]) for entry in palette]
        per_state.append(names)
    for block in root.get("blocks", []):
        index = int(block["state"])
        for names in per_state:
            if index < len(names):
                counts[names[index]] += 1
                break
    for names in per_state:
        for name in names:
            counts.setdefault(name, 0)
    return counts


def exempt(block, keep_all):
    if keep_all:
        return False
    return block in EXEMPT or block.startswith(EXEMPT_PREFIXES)


def gather(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            for root, _dirs, names in os.walk(path):
                files.extend(os.path.join(root, n) for n in sorted(names) if n.endswith(".nbt"))
        elif path.endswith(".nbt"):
            files.append(path)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("templates", nargs="*", default=[DEFAULT_TEMPLATES],
                        help="`.nbt` files or directories to walk (default: every shipped structure)")
    parser.add_argument("-p", "--processor-list", action="append", dest="lists",
                        help="processor list JSON to check against (repeatable; "
                             "default: every list in worldgen/processor_list)")
    parser.add_argument("--all", action="store_true",
                        help="include air, structure voids, jigsaws and dungeons2: markers")
    parser.add_argument("--by-file", action="store_true",
                        help="list which template each uncovered block came from")
    args = parser.parse_args()

    lists = args.lists or DEFAULT_LISTS
    aged, decorated = set(), set()
    for path in lists:
        a, d = rule_blocks(load_list(path)["processors"])
        aged |= a
        decorated |= d

    files = gather(args.templates or [DEFAULT_TEMPLATES])
    if not files:
        sys.exit("no .nbt templates found")

    counts = defaultdict(int)
    sources = defaultdict(set)
    for path in files:
        try:
            for block, n in palette_of(path).items():
                counts[block] += n
                sources[block].add(os.path.relpath(path, REPO))
        except Exception as error:                                   # noqa: BLE001
            print("  ! could not read %s: %s" % (path, error), file=sys.stderr)

    missing = {b: n for b, n in counts.items()
               if b not in aged and not exempt(b, args.all)}

    print("%d template(s), %d distinct block(s), against %d processor list(s)"
          % (len(files), len(counts), len(lists)))
    for path in lists:
        print("    %s" % os.path.relpath(path, REPO))
    print("\n%d block(s) with NO ageing rule (sorted by how much is placed):\n"
          % len(missing))
    print("    %-52s %8s  %s" % ("block", "placed", "note"))
    for block, n in sorted(missing.items(), key=lambda kv: (-kv[1], kv[0])):
        note = "decorated only" if block in decorated else ""
        print("    %-52s %8d  %s" % (block, n, note))
        if args.by_file:
            for source in sorted(sources[block]):
                print("        %s" % source)
    covered = len(counts) - len(missing)
    print("\n%d of %d distinct blocks are covered." % (covered, len(counts)))


if __name__ == "__main__":
    main()
