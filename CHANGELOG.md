# Changelog for Dungeons2 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Dungeon hallways can now be made wider, so corridors feel roomier to walk through instead of being a single block wide.
- Dungeon rooms can now be hand-built prefab structures instead of always being generated procedurally, the same way staircases between floors already work. Rooms and staircases can also be organized by theme (e.g. desert-style rooms), so new themed content can be added without any code changes.
- Weathering (moss, cracks, crumbling stone) now ages **stairs, slabs and walls** as well as plain blocks, keeping the block's facing and shape intact instead of resetting it. Ageing can also happen in stages, so a block can decay progressively rather than in one jump.
- Dungeons now grow **cobwebs in corners and creeping lichen on their walls**. The lichen spreads in patches — a patch is more likely to grow next to lichen that's already there, and keeps to a single species — rather than being sprinkled evenly over every wall. Both are datapack-controlled alongside the rest of a theme's weathering.
- Where the stonework has decayed all the way down to dirt, **mushrooms and moss now sprout on top of it and roots hang down underneath**, so a crumbled patch looks overgrown rather than just discoloured.
- **Ledges and corbels now crumble away** when the wall they jutted out of has decayed out from under them, instead of being left hanging in mid-air. One only goes if there is genuinely nothing left touching it.
- **Seagrass and lily pads** on standing water — unused by the classic dungeon, but available to themes that have water.

### Changed

- Work in progress: rebuilding how dungeons get placed into the world so they generate reliably on newer Minecraft versions.
- Corridor ceilings now use the dungeon's motif ceiling block instead of a black concrete placeholder.
- Hand-built structures — the surface entrance, the staircases down, and prefab rooms — are now weathered to match the dungeon around them. Previously only the procedurally generated rooms and corridors aged, so authored pieces stood out as conspicuously pristine.
- Weathering is now defined in vanilla Minecraft's standard `worldgen/processor_list` datapack format instead of a Dungeons2-specific one, so a single file controls both the generated dungeon and the hand-built pieces inside it. **Datapack authors:** the old `data/dungeons2/substitution/*.json` files no longer do anything and can be deleted; see `data/dungeons2/structures/README.md` for the replacement.

### Fixed

- Creeping lichen no longer appears plastered across doors. The doorway was still a solid wall when the lichen grew, so the lichen latched onto it and stayed there once the door was cut in; rooms and corridors now leave the doorway open at door height from the start.
- Corridors no longer render cutting straight through the middle of nearby rooms. The corridor and room geometry was being placed at the wrong position along one axis; both now line up correctly.
- Fixed a crash that could occur when placing a dungeon with certain room layouts.
- Fixed a dependency version mismatch that could crash the game on startup when Dungeon Blocks was also installed.
- Fixed a misleading warning in the logs about staircase/ladder piece heights that could appear even when everything was working correctly.
