# Changelog for Dungeons2 (Forge 1.12.2)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-10-19

### 🛠️ Fixed
- On a fresh install no dungeons ever generated: the style and spawn sheets were copied out before their folder existed, and the sheet loading then gave up instead of falling back to the built-in sheets ("style sheet has no themes" in the log).
- Dungeon generation no longer forces chunks to load or generate. The checks for whether a chunk was loaded loaded the chunk first, so they always passed, and some looked at the wrong chunk altogether. This caused lag spikes and "cascading worldgen" when a dungeon was built.
- Dungeons are no longer built into chunks that haven't been decorated yet, so trees, lakes and other mods' features no longer get stamped over a freshly built dungeon.
- An error while building a dungeon can no longer crash the server or leave a world that crashes every time it loads.
- On multiplayer servers, dungeons only generated when the first player in the player list was nearby. Any nearby player now counts.
- The `waitChunks` config option shared its setting with `dungeonGenProbability`, so it was really set to the generation probability. It now has its own setting.
- After you quit one single player world and opened another, the second world still remembered the first world's dungeons, which kept new dungeons from generating near those spots.
- The "chunks to wait before the first dungeon" count started over every time the world was reloaded. It is now saved with the world.
- Saved dungeon records lost their bounds when the world was reloaded (all of them were read into the X bounds).
- A level whose theme isn't in the style sheet now falls back to the dungeon's theme.

### ✨ Added
- New `/dgn2locate` command finds the nearest generated dungeon and the nearest dungeon site that hasn't been built yet, each with a click-to-teleport link. `/dgn2locate list [count]` lists the nearest dungeons and `/dgn2locate sites [count]` the nearest unbuilt sites (10 by default). A site is built once you teleport to it and its chunks load.

### ⚙️ Changed
- Dungeon entrances now look like real ruins. Instead of an intact building with moss on it and scattered holes, an entrance loses more of its upper walls than its lower ones, and anything left hanging in the air (cornices, wall pieces, ladders, torches) falls away. Chests and spawners are never removed.
- Dungeons are now built a few rooms at a time over several ticks instead of all at once, which removes the lag spike when a dungeon generates. The new `generationMsPerTick` config option (default 10) sets how much of each tick is spent building.
- Dungeon sites are now chosen during world generation and built once all of the dungeon's chunks are loaded and a player is nearby, starting with the site closest to a player. Sites waiting to be built are saved with the world, and dungeons still being built when the server stops are finished before the world saves.
- On multiplayer servers, players in different places get their dungeons built at the same time (up to 4 at once). A dungeon left half-built when everyone walks away waits for someone to return without holding up anyone else's.
- New `buildWithoutPlayers` config option (default off) for pre-generated worlds: dungeons are built as soon as their chunks are generated, during the pre-generation, instead of when players first come near. While no players are online, each dungeon is built all at once rather than a few rooms per tick.
- The invisible deferred dungeon generator block is no longer placed in the world. Any already in a world are replaced by the new build queue.
- Requires GottschCore 1.15.1 or later.
- Built with RetroFuturaGradle and Gradle 8.10.
