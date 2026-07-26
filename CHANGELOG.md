# Changelog for Dungeons2 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Dungeon hallways can now be made wider, so corridors feel roomier to walk through instead of being a single block wide.
- Dungeon rooms can now be hand-built prefab structures instead of always being generated procedurally, the same way staircases between floors already work. Rooms and staircases can also be organized by theme (e.g. desert-style rooms), so new themed content can be added without any code changes.

### Changed

- Work in progress: rebuilding how dungeons get placed into the world so they generate reliably on newer Minecraft versions.
- Corridor ceilings now use the dungeon's motif ceiling block instead of a black concrete placeholder.

### Fixed

- Corridors no longer render cutting straight through the middle of nearby rooms. The corridor and room geometry was being placed at the wrong position along one axis; both now line up correctly.
- Fixed a crash that could occur when placing a dungeon with certain room layouts.
- Fixed a dependency version mismatch that could crash the game on startup when Dungeon Blocks was also installed.
- Fixed a misleading warning in the logs about staircase/ladder piece heights that could appear even when everything was working correctly.
