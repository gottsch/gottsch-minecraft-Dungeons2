# Changelog for Dungeons2 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Dungeon hallways can now be made wider, so corridors feel roomier to walk through instead of being a single block wide.
- Dungeon rooms can now be hand-built prefab structures instead of always being generated procedurally, the same way staircases between floors already work. Rooms and staircases can also be organized by theme (e.g. desert-style rooms), so new themed content can be added without any code changes.
- Weathering (moss, cracks, crumbling stone) now ages **stairs, slabs and walls** as well as plain blocks, keeping the block's facing and shape intact instead of resetting it. Ageing can also happen in stages, so a block can decay progressively rather than in one jump.

### Changed

- Work in progress: rebuilding how dungeons get placed into the world so they generate reliably on newer Minecraft versions.
- Corridor ceilings now use the dungeon's motif ceiling block instead of a black concrete placeholder.
- Hand-built structures — the surface entrance, the staircases down, and prefab rooms — are now weathered to match the dungeon around them. Previously only the procedurally generated rooms and corridors aged, so authored pieces stood out as conspicuously pristine.
- Weathering is now defined in vanilla Minecraft's standard `worldgen/processor_list` datapack format instead of a Dungeons2-specific one, so a single file controls both the generated dungeon and the hand-built pieces inside it. **Datapack authors:** the old `data/dungeons2/substitution/*.json` files no longer do anything and can be deleted; see `data/dungeons2/structures/README.md` for the replacement.

### Fixed

- Corridors no longer render cutting straight through the middle of nearby rooms. The corridor and room geometry was being placed at the wrong position along one axis; both now line up correctly.
- Fixed a crash that could occur when placing a dungeon with certain room layouts.
- Fixed a dependency version mismatch that could crash the game on startup when Dungeon Blocks was also installed.
- Fixed a misleading warning in the logs about staircase/ladder piece heights that could appear even when everything was working correctly.
