# Dungeons2 structure templates (`.nbt`)

Hand-authored prefabs the structure system loads at worldgen time. Build each in a
creative world with Structure Blocks, **SAVE**, then copy the `.nbt` here.

```
data/dungeons2/structures/
  entrances/    surface → floor-0 entrance pieces           (Phase 4)
  transitions/  2-story floor-to-floor links                (Phase 4)
  rooms/        pooled room prefabs                          (Phase 8)
  corridors/    themed corridor prefabs                      (Phase 8)
```

Jigsaw `template_pool` JSONs for the assembled entrance live separately under
`data/dungeons2/worldgen/template_pool/entrance/`.

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

---

## Jigsaw blocks — exact data

There are **two roles**, both `minecraft:jigsaw` blocks, told apart **by the `name` field**.
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

**Phase 4b (not yet implemented) does the opening/closing — not the author:**
- **Chosen candidate:** Phase 4b overwrites the column (sill, two halves, lintel) with an open
  door, the same job `DungeonDoorPiece` already does for procedural rooms — the connecting
  corridor butts its own 5-tall wall against it.
- **Unchosen candidate:** the jigsaw block converts to its own `final_state` value — same
  mechanism vanilla uses to resolve orphaned jigsaws in a structure (§ below) — and nothing
  else about the wall changes. Worst case is a single-cell pocket where the jigsaw sat, never
  a breach.

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

**Phase 4b design note — self-conversion, vanilla-style.** Today, nothing converts these
markers: `DungeonTemplatePiece` extends vanilla `TemplateStructurePiece` directly and never
overrides `postProcess`, so placement goes through plain `template.placeInWorld(...)`, which
has no jigsaw awareness (that logic lives only in vanilla's `PoolElementStructurePiece`/jigsaw
assembly path). So placing this template today leaves a literal, un-converted jigsaw block
sitting in the wall — expected, not a bug, until Phase 4b exists. When Phase 4b is built, an
unchosen `dungeons2:door` jigsaw must **self-convert to its `final_state` value**, exactly like
vanilla resolves any orphaned jigsaw in a placed structure — Phase 4b owns doing this
explicitly (read the marker's `final_state`, swap the block), it does not happen for free.

### 2. Assembly joint — snaps entrance pieces together (Phase 4b, vanilla `JigsawPlacement`)

Used only inside the **jigsaw-assembled entrance** (surface building → descent → …). These
have a **real pool** so vanilla connects them; our planner ignores them (name ≠ `dungeons2:door`).

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
