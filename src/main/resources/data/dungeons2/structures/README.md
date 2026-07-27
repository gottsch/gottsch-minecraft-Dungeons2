# Dungeons2 structure templates (`.nbt`)

Hand-authored prefabs the structure system loads at worldgen time. Build each in a
creative world with Structure Blocks, **SAVE**, then copy the `.nbt` here.

```
data/dungeons2/structures/
  entrances/    surface → floor-0 entrance pieces           (Phase 4b, jigsaw-assembled)
  transitions/  floor-to-floor links                        (jigsaw-assembled, see below)
  rooms/        pooled room prefabs                          (Phase 8, jigsaw-assembled, see below)
  corridors/    themed corridor prefabs                      (Phase 8, not started)
```

Jigsaw `template_pool` JSONs for the assembled entrance, transitions, and rooms live separately
under `data/dungeons2/worldgen/template_pool/{entrance,transitions,rooms}/`.

**Motif/theme naming (transitions & rooms only):** the start pool a dungeon assembles from is
selected by motif, one folder per theme: `transitions/<motif>/shaft_bottom.json`,
`rooms/<motif>/normal.json` (e.g. `rooms/desert/normal.json` for a `DESERT`-themed room pool).
Motif selection itself is still hardcoded to `classic` (see `DungeonStructure.findGenerationPoint`)
— this is just the naming mechanism, ready for whenever motif selection varies for real. A
missing themed pool degrades gracefully to plain procedural generation for that
floor/room/transition, same as an empty pool always has; a smarter two-tier fallback (missing
theme → shared/classic pool instead of straight to procedural) is a possible future phase, not
implemented. **The entrance is the one exception** — `entrance/surface_exit.json`/`descent.json`
stay unparametrized for now, because `surface_exit.nbt`/`descent_1.nbt` have jigsaw assembly-joint
`pool`/`target` fields baked into their NBT that cross-reference each other by exact resource
location; moving them under a motif subfolder needs those fields updated too (safest done by
re-saving the jigsaw blocks in-game with the new Target Pool value), not a blind file move.

**Conventions (all templates):** author facing **north** (the planner rolls a 0/90/180/270
rotation and rotates markers with it); footprint **odd** in X and Z; local origin =
**NW-bottom corner** of the bounding box (unless a `d2:anchor` DATA marker overrides it);
**local Y = 0 is the floor walking plane**.

**Vertical sizing — how tall to build:** a room is as tall as you want, up to the **floor
slab height** (`DungeonStackPlanner.DEFAULT_FLOOR_HEIGHT`, currently **10** → Y0 floor up to
Y9 ceiling). Don't exceed it: the planner stacks floors using that fixed height plus a
2-block gap, so a taller room would punch into the floor above. (Procedural rooms already
roll their height up to 10.) The "5" you see below is **not** a room height — it's the fixed
height of the engine's **corridors** (`DungeonCorridorPiece`), which only matters at the
doorway interface, not for your room's walls or ceiling.

**Transitions specifically** span floor-to-floor: local Y=0 is the *lower* floor's walking
plane, and the whole assembled chain must reach **between `floorHeight + gapBetweenFloors`
(currently **12**, the minimum needed to actually bridge the two floor planes) and
`floorHeight*2 + gapBetweenFloors` (currently **22**, the maximum before it overflows the
upper floor's own reserved room-height budget at that XZ column)** — anywhere in that range
lands cleanly; it does not need to hit 22 exactly. A monolithic template like
`ladder1.nbt`/`stairs_1.nbt` just needs to fall somewhere in that range. If you're instead
composing a chain from multiple pieces (top room + descent segment + bottom room, see the
jigsaw pools section below), **you're responsible for keeping any combination of pieces you
build within that range** — the engine doesn't enforce or adjust for it, and going under the
minimum (or over the maximum) leaves a gap or overlap at whichever end is off (a warning is
logged when the realized height falls outside the range, to catch this at test time rather
than in-game).

---

## Jigsaw blocks — exact data

There are **three roles**, all `minecraft:jigsaw` blocks, told apart **by the `name` field**.
Fields below are the in-game Jigsaw Block GUI fields (= the block-entity NBT keys).

### 1. Door candidate — the maze attaches here (this is what you asked about)

Place one at **every perimeter wall cell** where you'd allow a corridor to attach. They are
*candidates*: the maze opens up to the room's `degrees` of them and walls off the rest, so
mark generously. The planner finds them with
`StructureTemplate.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW)` and keeps only the
ones whose **name is exactly `dungeons2:door`**.

| Jigsaw GUI field | NBT key      | Value for a door candidate |
|------------------|--------------|----------------------------|
| Name             | `name`       | `dungeons2:door`           |
| Target Name      | `target`     | `minecraft:empty`          |
| Target Pool      | `pool`       | `minecraft:empty`          |
| Turns into       | `final_state`| `minecraft:air`            |
| Joint Type       | `joint`      | `rollable` (ignored — never assembled by vanilla) |

Block state (orientation): **front faces OUTWARD**, top faces up. Pick by which wall it's on:

| Wall the marker sits on | Front faces | `orientation` blockstate |
|-------------------------|-------------|--------------------------|
| North wall              | north       | `north_up`               |
| South wall              | south       | `south_up`               |
| East wall               | east        | `east_up`                |
| West wall               | west        | `west_up`                |

**Placement rules:**
- At **local Y = 0** (the jigsaw block *is* the door sill / floor cell).
- In the **perimeter wall cell** of the footprint (the outer ring the maze treats as wall) —
  **author this cell as solid wall**, same material as everywhere else. The jigsaw block just
  occupies that one cell; do **not** pre-cut a passage around it (see below).
- **≥ 2 cells in from every corner** (the maze rejects door positions within 2 of a corner).
- Front (`orientation`) points **out of the room** = the direction a corridor approaches.
- Place **≥ the intended door count**; for an END/terminal room (`degrees = 1`) place 2–4 so
  the maze has options for the single entrance it opens.

**Do not pre-cut the passage — author the whole column solid.** This matches how the rest of
the engine already resolves doors: `BasicWallGenerator` (procedural rooms) has no doorway
awareness at all — it always builds a fully solid wall. A chosen door is opened afterward by a
separate `DungeonDoorPiece`, placed only at the room's *resolved* door coordinates, which
overwrites that column with the door geometry. Unchosen candidates simply never get that
overwrite, so the wall stays solid — solid by default, opened by a later overwrite. Template
pieces (entrance/transition) follow the same pattern once Phase 4b is wired:

```
 local Y   marker cell (as authored)      matches…
   …       wall continues to ceiling      (room wall — up to Y9 / floor slab top)
   4       wall (solid)                   top of the corridor that would connect here (corridors are 5 tall: Y0..Y4)
   3       wall (solid)                   lintel position — solid until opened
   2       wall (solid)                   door upper half — solid until opened
   1       wall (solid)                   door lower half — solid until opened
   0       JIGSAW block                   corridor / door sill
```

**The engine does the opening/closing — not the author. Implemented for both the entrance and
transitions** (both are now assembled via real vanilla `JigsawPlacement`; see the "Assembly
joint" and "Transition jigsaw pools" sections below):
- **Chosen candidate:** the maze's `DungeonDoorPiece` overwrites the column (sill, two halves,
  lintel) with an open door, placed after the assembled pieces so it wins — the connecting
  corridor butts its own 5-tall wall against it.
- **Unchosen candidate:** the jigsaw block converts to its own `final_state` value automatically
  — this is inherent vanilla behavior for any real `PoolElementStructurePiece` (every
  `dungeons2:door` candidate has `target`/`pool` = `minecraft:empty`, so it never matches
  anything and always resolves as "unconnected"), not custom code. Worst case is a single-cell
  pocket where the jigsaw sat, never a breach.

Raw block-entity NBT (1.20.1), for reference:

```snbt
{
  id: "minecraft:jigsaw",
  name: "dungeons2:door",
  target: "minecraft:empty",
  pool: "minecraft:empty",
  final_state: "minecraft:air",
  joint: "rollable"
}
```

> 1.20.1 jigsaw block entities have exactly these five fields. `selection_priority` /
> `placement_priority` do **not** exist until 1.20.5 — don't add them.

**Design note — self-conversion is free, but only for real jigsaw pieces.** This only works
because the entrance and transitions are placed via real vanilla `JigsawPlacement`, producing
genuine `PoolElementStructurePiece`s — vanilla's own jigsaw machinery does the `final_state`
conversion as part of normal jigsaw postProcess, no custom code required. It would **not** work
for a plain `TemplateStructurePiece` (no jigsaw awareness at all in that placement path) — this
is why both the entrance and transitions are jigsaw-assembled rather than flat single templates.

### 2. Premade door / connector — a candidate that's already a real, built door

Same idea as a door candidate (the maze may attach a corridor here), but for a doorway you've
**already built** — a real door frame/opening, not solid wall. Use this when you want a
specific, hand-decorated doorway (an ornate archway, a portcullis, whatever) to be the one the
maze connects to, instead of letting the generic door piece carve a plain opening later.

| Jigsaw GUI field | NBT key      | Value for a premade door |
|------------------|--------------|----------------------------|
| Name             | `name`       | `dungeons2:connector`      |
| Target Name      | `target`     | `minecraft:empty`          |
| Target Pool      | `pool`       | `minecraft:empty`          |
| Turns into       | `final_state`| `minecraft:air`            |
| Joint Type       | `joint`      | `rollable` (ignored — never assembled by vanilla) |

Same placement rules as a door candidate (local Y=0, ≥2 cells from every corner, front faces
outward per the same orientation table) with **one key difference**: **build the actual door
here — do not author it as solid wall.** The jigsaw block sits at the sill of a doorway you've
already fully constructed.

**Behavior:** `dungeons2:connector` cells participate in the maze's normal candidate-doorway
selection exactly like `dungeons2:door` — the maze may pick one as a real connection (a corridor
routes to it, counts toward the room's `degrees`) or leave it unpicked. The difference is what
happens when it's picked: **no `DungeonDoorPiece` is generated for it.** Your prebuilt door is
left completely untouched either way — chosen or not. This is why it's a *connector*, not a
*door candidate*: it never gets door geometry written over it, because it already has real door
geometry.

**Authoring caveat:** if a `dungeons2:connector` isn't picked by the maze, nothing connects to it
— it remains a decorative, already-open doorway with whatever's behind it (unrendered terrain,
if nothing else fills that space). Same tradeoff regular unchosen `dungeons2:door` candidates
accept (README's role 1 above: "worst case is a single-cell pocket"), just via a different
mechanism (a real open door leading nowhere, vs. a jigsaw block self-converting to `final_state`
in a solid wall). Place premade doors only where you're fine with that outcome if unchosen, same
guidance as regular candidates.

### 3. Assembly joint — snaps entrance/transition pieces together (vanilla `JigsawPlacement`)

Used inside the **jigsaw-assembled entrance** (surface building → descent → …) and **transitions**
(see below). These have a **real pool** so vanilla connects them; our planner ignores them
(name ≠ `dungeons2:door`).

| Field        | Surface building → descent          | Descent top (mates upward)        |
|--------------|-------------------------------------|-----------------------------------|
| `name`       | `dungeons2:entrance/surface_exit`   | `dungeons2:entrance/descent_top`  |
| `target`     | `dungeons2:entrance/descent_top`    | `dungeons2:entrance/surface_exit` |
| `pool`       | `dungeons2:entrance/descent`        | `minecraft:empty` (or next pool)  |
| `final_state`| `minecraft:air`                     | `minecraft:air`                   |
| `joint`      | `aligned`                           | `aligned`                         |
| orientation  | front = **down** → `down_south`     | front = **up** → `up_north`       |

Vertical joints are supported (trial chambers / ancient cities chain vertically). Register
each variant in a `template_pool` JSON under
`data/dungeons2/worldgen/template_pool/entrance/` and assemble with a small `max_depth` so
it never recurses into the dungeon. The descent piece carries the `dungeons2:door` candidates
(role 1 above) on its floor-0 walking plane — **their Y defines floor 0's walking plane** and
their cells become the START room's `candidateDoorways`.

### Transition jigsaw pools

Transitions assemble **bottom-anchored** — the opposite direction from the entrance. The planner
places the start pool at the *lower* floor's walking plane (local Y=0, per the convention above)
and chains **upward**; the terminal piece must land exactly at the *upper* floor's walking plane
(see "Transitions specifically" above for the fixed-height requirement this implies). This
matches how `ladder1.nbt`/`stairs_1.nbt` are already authored (built upward from their own floor
at local Y=0) — don't flip this without re-authoring those templates.

Pools live under `data/dungeons2/worldgen/template_pool/transitions/<motif>/` (currently only
`classic/` is authored; see the motif-naming note above).

| Pool | Role | Contents |
|------|------|----------|
| `dungeons2:transitions/<motif>/shaft_bottom` | **start pool** (what the planner assembles from, at the lower floor's plane) | Either a **complete, self-contained piece** with no outgoing joint — this is exactly what `ladder1.nbt`/`stairs_1.nbt` already are, registered here unchanged — or a **bottom segment**: `dungeons2:door` candidates at its own (lower) floor level + one **upward** assembly joint (`up_north`) into `shaft_segment`. |
| `dungeons2:transitions/<motif>/shaft_segment` | optional, repeatable middle piece(s) | Down joint (`down_south`, mates to whatever sent the connection up) + up joint (`up_north`, continues further) — no doors, corridor/decoration between the two ends. Only needed once you're authoring segmented chains; doesn't need to exist otherwise. |
| `dungeons2:transitions/<motif>/shaft_top` | terminal piece | `dungeons2:door` candidates at the *upper* floor's level + one **downward** joint (`down_south`, mates to whatever's below it), no further upward connection. |

Both self-contained pieces and segmented chains can coexist as weighted alternatives in the same
`shaft_bottom` pool — nothing stops you from mixing monolithic and composed styles. All pieces
that participate in one chain should share the same XZ footprint so walls line up.

`dungeons2:door` candidates from **both** ends get read back after assembly (bucketed by Y, so
which pool contributed them doesn't matter) and become that floor's `candidateDoorways` — the
bottom piece's candidates restrict the lower floor's START room, the top piece's restrict the
upper floor's END room, exactly mirroring how the entrance's descent candidates drive floor 0's
START room.

### Room jigsaw pool

Ordinary interior ("NORMAL") rooms can also be hand-authored prefabs instead of always
procedural. Much simpler than transitions: **one pool, no chaining, one Y anchor.**

Lives under `data/dungeons2/worldgen/template_pool/rooms/<motif>/` (currently only `classic/`
is authored; see the motif-naming note above — e.g. a desert theme would add
`rooms/desert/normal.json`).

| Pool | Role | Contents |
|------|------|----------|
| `dungeons2:rooms/<motif>/normal` | the only pool | Complete, self-contained pieces (`minecraft:single_pool_element`, like `ladder1.nbt`/`stairs_1.nbt`) with `dungeons2:door` (and optionally `dungeons2:connector`) candidates around the perimeter at local Y=0, the room's own walking plane. No assembly joints, no segments, no top/bottom split — a room is never chained. |

Per floor, the planner tries a small, fixed number of candidate slots (currently 2) at a
rolled footprint size, assembles a piece there via real jigsaw placement, and — if it fits
without colliding with the floor's other reserved slots — hands its real footprint and door
markers to the maze as one of `MazeLevelGenerator2D`'s **supplied rooms**, restricted to
those candidate doorways exactly like the entrance/transition START/END rooms are. A failed
or colliding attempt is simply skipped; ordinary procedural fill rooms cover the gap, same
graceful degradation as an empty entrance/transition catalog. There's no height-budget
constraint like transitions have — a room is whatever height you build it to, same as any
other authored room.

Add real content by creating `data/dungeons2/worldgen/template_pool/rooms/<motif>/normal.json`
(same shape as `transitions/<motif>/shaft_bottom.json`, just `single_pool_element` entries with
no outgoing joint) plus the `.nbt` files it references — nothing else needs to change, the
mechanism already reads whatever pool entries exist. `classic/normal.json` is the only one
authored today.

---

## Weathering / decoration (`worldgen/processor_list/<motif>_weathering.json`)

Aging (mossy, cracked, crumbled-to-cobble) is a **vanilla
`minecraft:worldgen/processor_list`**, and the same file decorates both halves of a
dungeon:

- **Prefabs** — each pool element's `"processors"` field points at it, the ordinary
  vanilla way (`"processors": "dungeons2:classic_weathering"`).
- **Procedural rooms / corridors / doors** — `DungeonPiece.placeAll` runs the same list
  over its own blocks via `PieceProcessors`, using vanilla's `processBlockInfos` with no
  template involved.

So a hand-authored prefab and the procedural room next to it weather identically, from
one file. Adding a theme's weathering is pure data: create
`data/dungeons2/worldgen/processor_list/<motif>_weathering.json`. A motif with no such
file simply generates undecorated — the same graceful degradation a missing pool has.

**Two rules for authoring these, both about chunk-safety:**

1. **`minecraft:rule` processors only.** A procedural piece is re-rendered once per chunk
   it overlaps, so each block is processed more than once and must resolve the same way
   every time. `RuleProcessor` is safe: it seeds its random from the block's absolute
   world position. Anything that decides from the whole block list at once
   (`minecraft:capped`, or any processor overriding `finalizeProcessing`) sees only the
   current chunk's slice of the piece and would decide differently per chunk. A test
   enforces this (`WeatheringProcessorListTest`).
2. **Probabilities are conditional, not absolute.** One rule produces one output state —
   there is no weighted variant list. Several variants of the same source block are
   consecutive rules, first match wins, and each rule only rolls after the previous one
   missed. So three variants at 10% each are authored `0.1`, `0.1111`, `0.125`, not
   `0.1, 0.1, 0.1`. (Rules for a *different* source block don't interfere — the block
   check short-circuits before the roll.) `WeatheringProcessorListTest` asserts the
   composed absolute rates, so if you edit the numbers, update the expectations there.

> `entrance/surface_exit.json` is deliberately left on `minecraft:empty` — it's the
> above-ground building, with no procedural neighbour to look inconsistent against, so
> whether it should weather is a purely stylistic call rather than a consistency fix.

---

## DATA structure-block markers (content / vertical links)

Type the string into the DATA structure block's text field. The block is cleared to air on
placement; code fills in the content.

| Marker string | Place where | Meaning |
|---------------|-------------|---------|
| `d2:descend`  | a floor cell | Entrance: where the drop lands on floor 0. Transition: the lower-floor connection column. |
| `d2:ascend`   | a floor cell | Transition only: the upper-floor connection column. |
| `d2:chest`    | a floor cell | Becomes a loot chest (loot table assigned in code). Optional. |
| `d2:spawner`  | a floor cell | Becomes a mob spawner (entity assigned in code). Optional. |
| `d2:anchor`   | one cell     | Optional origin override (default origin = NW-bottom corner). |

> The old `d2:door` DATA marker is **removed** — doors are jigsaw blocks (role 1), not DATA blocks.
