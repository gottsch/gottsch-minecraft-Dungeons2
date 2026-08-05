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
| `-PcorridorHeight` | the motif's own `corridor.height` | corridor wall height in blocks (5-8) |
| `-PnarrowHeight` | the motif's own (default: no drop) | ceiling height for 1-wide cells |
| `-Pprofile` | the motif's own `corridor.profile` | `flat` or `arched` |
| `-ParchBlock` | `minecraft:stone_brick_stairs` | haunch stairs, when arching |
| `-Pstyle` | — | pin every floor to one authored `corridor.styles` entry by name |
| `-PminRoomGap` | `0` | minimum clear cells between rooms; `0` is shipped behaviour |
| `-Px` / `-Pz` | `0` | world XZ the planner is anchored at |
| `-PsurfaceY` | `72` | surface Y the stack hangs from |
| `-Porder` | `EMIT` | write order: `EMIT` (production) or `ROOMS_FIRST` (the pre-2026-08-03 order, for comparison) |
| `-Pout` | — | output path |
| `-Popen` | `false` | open in the default browser when done |
| `-Pdescribe` | `false` | also dump the planner's own layout description |

Without `-Pstyle`, the tool rolls a corridor style per floor exactly as production does — the
audit's first block is which style each floor got. The four geometry overrides above are
different: any of them **drops the styles list entirely** and pins the whole dungeon to one
shape, since applying an override to only the floors that happened to roll it would be useless.

Every run also prints a text audit: the per-floor corridor style, who wins the contested cells,
which writes actually changed the block, per-room wall ownership at the trim row, and any
doorway left bricked up. That is what makes
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

---

## Driving a piece through the real `postProcess`

`FakeWorldGenLevel` + `TestRegistries` let a test run a `StructurePiece`'s actual `postProcess`
headlessly. That matters because **every other check in this project stops short of it**: unit
tests, the floor-plan viewer and ad-hoc probes all call the generators directly, so they see what a
piece *intends* to place. The weathering processor list and `settleJoinShapes` run only in
`postProcess`, and four defects in three sessions lived in that gap — the `updateShape` chunk-gen
crash, outer corners never populating, stairs weathering into dirt cubes, and duplicated arch caps.
All four were found by a person looking at a screenshot.

```java
FakeWorldGenLevel level = FakeWorldGenLevel.create();
piece.postProcess(level.level(), null, null, RandomSource.create(seed),
                  piece.getBoundingBox(), chunkPos, origin);
BlockState landed = level.blockAt(somePos);
```

`TestRegistries.get()` supplies the two datapack registries `postProcess` reads —
`dungeons2:motif_config` and `minecraft:worldgen/processor_list` — decoded from the shipped
resources. **Without it every lookup silently returns its default**, so the piece renders as bare
stone brick with no weathering and the test passes while asserting nothing. `CorridorPostProcessTest`
guards against exactly that with `theWeatheringPassActuallyRan`.

**Invoke it per chunk, not once per piece.** Vanilla calls `postProcess` once for every chunk a
piece's box spans, with the box clipped to that chunk — so everything inside runs N times over the
same piece. `CorridorPostProcessTest.postProcessPerChunk` mirrors `StructureStart.placeInChunk`, and
`splittingAPieceAcrossChunksBuildsTheSameThing` asserts the two give identical worlds. That test
found a real defect the first time it ran: 5 blocks in 75,227, all arch corners on a boundary,
settling their shape against a neighbour that had not been written yet.

Two things it does *not* model, both deliberate:

- **No chunk contents.** `getChunk` returns null and nothing models a chunk that already finished
  generating, so this cannot reproduce "piece silently skipped in an already-generated chunk" —
  that is `/place` behaviour on a live server. Chunk *boundaries* are modelled; see above.
- **No `ServerLevel`.** `getLevel()` throws, so pieces that spawn **entities** — rooms with pots —
  cannot be driven through it yet. Corridors and doors are pure block placement and work today.

Anything else unimplemented throws naming the method it wants, so extending it is a matter of
running a test and reading the message.

**A note on writing assertions here.** It is easy to write one that passes either way. The first
version of `authoredArchShapesSurviveSettleJoinShapes` asserted "some outer corner exists", which
stayed green with the bug reintroduced; it now pairs each stair the generator intended with what
landed at that position, and catches 537 of 1003. Reintroduce the bug and watch the test fail before
trusting it.
