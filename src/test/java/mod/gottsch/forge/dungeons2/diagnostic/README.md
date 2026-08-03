# Floor-plan viewer

A headless 2D floor-plan viewer for planned dungeons. It runs the real planner and the real piece
renderers with no world, no server and no game, and writes one self-contained HTML file you open in
a browser.

```bash
./gradlew floorplan
```

```bash
./gradlew floorplan -Pseed=12345 -Psize=MEDIUM -Pfloors=2 -Popen=true
```

Output lands in `build/floorplan/floorplan-<seed>.html` — one file, no assets, no server. Around
400 KB for a MEDIUM dungeon.

| Property | Default | Meaning |
|---|---|---|
| `-Pseed` | `0` | dungeon seed |
| `-Psize` | `MEDIUM` | `SMALL` / `MEDIUM` / `LARGE` |
| `-Pmotif` | `classic` | motif value, read from the shipped datapack JSON |
| `-Pfloors` | — | floor-count override; omit to let the size tier roll it |
| `-PcorridorWidth` | `3` | dilation width, matching the shipped generation config |
| `-PminRoomGap` | `0` | minimum clear cells between rooms; `0` is shipped behaviour |
| `-Px` / `-Pz` | `0` | world XZ the planner is anchored at |
| `-PsurfaceY` | `72` | surface Y the stack hangs from |
| `-Porder` | `EMIT` | write order: `EMIT` (production) or `ROOMS_FIRST` (the pre-2026-08-03 order, for comparison) |
| `-Pout` | — | output path |
| `-Popen` | `false` | open in the default browser when done |
| `-Pdescribe` | `false` | also dump the planner's own layout description |

Every run also prints a text audit: who wins the contested cells, which writes actually changed the
block, per-room wall ownership at the trim row, and any doorway left bricked up. That is what makes
two runs comparable as numbers rather than as two pictures.

`-Porder` and `-PminRoomGap` exist to measure a change *before* making it. `-Porder` changes who wins
a shared cell and nothing else; `-PminRoomGap` changes the maze. See also `./gradlew spacingSweep`,
which sweeps the gap over many seeds and reports the cost/benefit as a table.

## What the viewer shows

- **Colour by block** — every cell painted as the block that ended up there. This is the view for
  reading floor patterns and wall trim: a `deepslate_bricks_border` floor is a dark ring, a
  `checkerboard` is a checkerboard.
- **Colour by owner** — every cell painted by *which piece wrote it*. Rooms get their own colours,
  corridors share an olive, doors are gold.
- **Maze** — the planner's raw `Grid2D`, cell types straight from `CellType`.
- **Level slider** — Y by Y from the floor plane to the ceiling. Floor / wall row / ceiling shortcuts.
- **Contested cells** — cells more than one piece wrote, in red. Hovering one lists every write in
  order, so you can read "room 512 wrote large stone brick here, corridor 537 then overwrote it with
  stone bricks".
- **Room panel** — click a room for its scheme, size, and a per-side wall audit naming who owns each
  of its four walls at the current level.

## Why it renders through the pieces

It does not call `DungeonLayoutRenderer`. It emits the real `StructurePiece`s through
`DungeonPieceEmitter` and asks each one for its own placements. Three reasons, and all three matter:

- **Attribution.** Each piece's blocks are tagged with that piece, which is what makes contested
  cells and the wall audit possible at all.
- **Fidelity.** Every piece seeds itself from `DungeonPiece#deterministicRandom` (anchor XZ + floor Y
  + piece id) exactly as it does in game, so a room here rolls the scheme it rolls in the world. A
  shared `RandomSource` — what `DungeonLayoutRenderer` uses — would roll something else entirely.
- **Order.** Pieces render in emit order, which is the order `postProcess` runs them, so *last
  writer wins* resolves the same way it does in the world.

**To reproduce a specific dungeon you found in game, pass its anchor as `-Px`/`-Pz` as well as its
seed.** The anchor is part of every piece's seed, so seed alone gives you a different dungeon.

## Files

| File | Role |
|---|---|
| `FloorPlanTool.java` | entry point: parse options, plan, export, write HTML |
| `FloorPlanExporter.java` | layout + rendered pieces → the JSON model |
| `MotifConfigs.java` | loads a shipped motif off the classpath the way `MotifConfigHelper` does in game |
| `src/test/resources/diagnostic/floorplan.html` | the viewer; `"__FLOORPLAN_DATA__"` is replaced with the JSON |
| `FloorPlanExporterTest.java` | smoke test — the model parses, every cell carries attribution, and contested cells are found |

The tool lives in the **test** source set: it needs Minecraft bootstrapped (the generators resolve
block states through the registry) and nothing shipped should depend on it.
