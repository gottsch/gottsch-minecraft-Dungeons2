# Changelog for Dungeons2 1.20.1

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-11-01

A ground-up rewrite. Dungeons2 now builds sprawling, multi-floor dungeons deep underground, full of monsters, traps, treasure and secrets, with a boss at the bottom. Nothing carries over from the 1.12 version, so read this as a new mod. The features are described on the mod's page and in the wiki.

### Changed
- **Built on Minecraft's own structure system.** A dungeon's layout is planned up front, then placed chunk by chunk the way vanilla places villages and strongholds, rather than being built in one go.
- **Procedural and hand-built together.** Generated rooms and corridors are mixed with hand-built pieces.
- **Datapacks instead of config sheets.** The stylesheets and chestsheets are gone, and there is no config file for dungeon generation. Placement, materials, room styles, weathering, monsters and loot are all datapack JSON, and a mistake in a pack is reported when it loads.
- **Existing worlds:** dungeons only generate in chunks that haven't been generated yet.

### Added
- The dungeon's own monsters and bosses, plus secret rooms, traps, mimics, sealed tombs, counter-items, Dungeon Maps, a Dungeon Compass, lore books and advancements.
- Optional support for Treasure2 (boss chests) and Enemy Echelons API (monsters that get tougher with depth).

### Requirements
- Forge 1.20.1.
- GottschCore 2.9.0+, Dungeon Blocks 3.1.0+ and Gottsch's Monster Manual 1.1.0+.
- Gottsch Books is bundled inside the jar.
- Optional: Treasure2 4.1.0+ and Enemy Echelons API 1.1.0+.
