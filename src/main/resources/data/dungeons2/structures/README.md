# Dungeons2 structure templates (`.nbt`)

Hand-authored prefabs the structure system loads at worldgen time. Build each in a
creative world with Structure Blocks, **SAVE**, then copy the `.nbt` here.

```
data/dungeons2/structures/
  entrances/          surface → floor-0 entrance pieces     (Phase 4b, jigsaw-assembled)
  transitions/<motif>/ floor-to-floor links                 (jigsaw-assembled, see below)
  rooms/<motif>/      pooled room prefabs                    (Phase 8, jigsaw-assembled, see below)
  corridors/          themed corridor prefabs                (Phase 8, not started)
```

The `.nbt` files themselves are filed under a motif folder, mirroring the pools — a second theme's
files never sit next to classic's with only a filename prefix telling them apart. **Moving an
`.nbt` is safe** in a way moving a *pool* is not: the only reference to an nbt path anywhere is the
plain-text `location` field in the pool JSONs, so a move is "move the file, update `location`,
done". No Java code hardcodes a structure path. (`entrances/` is not motif-scoped — see the
exception at the end of this section.)

Jigsaw `template_pool` JSONs for the assembled entrance, transitions, and rooms live separately
under `data/dungeons2/worldgen/template_pool/{entrance,transitions,rooms}/`.

**Motif/theme naming (transitions & rooms only; floor patterns follow the same idea, see below):**
the start pool a dungeon assembles from is selected by motif, one folder per theme:
`transitions/<motif>/shaft_bottom.json`, `rooms/<motif>/normal.json` (e.g.
`rooms/desert/normal.json` for a `DESERT`-themed room pool). Motif selection itself is still
hardcoded to `classic` (see `DungeonStructure.findGenerationPoint`) — this is just the naming
mechanism, ready for whenever motif selection varies for real. A missing themed pool degrades
gracefully to plain procedural generation for that floor/room/transition, same as an empty pool
always has; a smarter two-tier fallback (missing theme → shared/classic pool instead of straight
to procedural) is a possible future phase, not implemented. **The entrance is the one
exception** — `entrance/surface_entrance.json`/`descent.json`
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

> **An authored doorway is guaranteed to be connected.** A marked cell only becomes a real
> door when a carved region ends up on its far side, and nothing in the maze routes a corridor
> *to* a marker — so a piece offering only one or two candidates used to end up sealed, with the
> maze punching into it elsewhere instead. The connectivity pass now tunnels to an authored
> doorway before it will punch a new hole, at both ends of a transition. You still want to mark
> generously (more candidates = better-looking routes), but a lone marked door will no longer be
> stranded.

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

> The **start pool** id is `dungeons2:entrance/surface_entrance` (renamed from `surface_exit` —
> from the player's perspective, this piece is what they discover and enter to begin the descent,
> not something they exit through). The shipped `surface_exit.nbt`'s own jigsaw-joint identity
> string above is unchanged and still literally `dungeons2:entrance/surface_exit`; that string is
> just this joint's local matching identifier, not the pool id, and it's unused for this piece's
> own placement (it's the assembly root, so nothing targets it) — so the mismatch is harmless. New
> pieces authored going forward should use `dungeons2:entrance/surface_entrance` for this joint's
> identity to keep the two in sync.

Vertical joints are supported (trial chambers / ancient cities chain vertically). Register
each variant in a `template_pool` JSON under
`data/dungeons2/worldgen/template_pool/entrance/` and assemble with a small `max_depth` so
it never recurses into the dungeon. The descent piece carries the `dungeons2:door` candidates
(role 1 above) on its floor-0 walking plane — **their Y defines floor 0's walking plane** and
their cells become the START room's `candidateDoorways`.

**Worked example, the three-piece `classic` entrance chain (2026-07-30, confirmed in game):**
each piece independently swappable later (e.g. a future `entrance_ladder_2` alternative in the
same pool), unlike the old monolithic `surface_exit.nbt`/`descent_1.nbt` pair.

| Piece | Role | Joint | Name | Target Pool | Target Name |
|---|---|---|---|---|---|
| `entrance_1` | root — surface building | bottom (outgoing) | `dungeons2:entrance/surface_entrance` | `dungeons2:entrance/descent_ladder` | `dungeons2:entrance/ladder_top` |
| `entrance_ladder_1` | middle — vertical shaft | top (incoming) | `dungeons2:entrance/ladder_top` | `minecraft:empty` | `minecraft:empty` |
| `entrance_ladder_1` | middle — vertical shaft | bottom (outgoing) | `dungeons2:entrance/ladder_bottom` | `dungeons2:entrance/descent` | `dungeons2:entrance/room_top` |
| `entrance_exit_1` | terminal — floor-0 room, carries the `dungeons2:door` candidates | top (incoming) | `dungeons2:entrance/room_top` | `minecraft:empty` | `minecraft:empty` |

Registered as `entrance/surface_entrance.json` (root) → `entrance/descent_ladder.json` (new) →
`entrance/descent.json` (repointed at `entrance_exit_1`, replacing the old test piece). A
`dungeons2:connector` (role 2) spanning a **3-wide** opening was authored into this chain and
confirmed working — multi-cell-wide connectors generalize the same way single-cell ones already
did.

> **Trap worth knowing:** a structure block's **Save** always serializes from the world's live
> block-entity state, not from whatever's on disk — so hand-patching an exported `.nbt`'s jigsaw
> fields (e.g. with a script) gets silently reverted the next time Save is pressed in-game, because
> the in-world jigsaw blocks still hold the old values. Fix: copy the corrected `.nbt` into
> `<world save>/generated/dungeons2/structures/...` and use **Load** (not Save) in the structure
> block — that syncs the world's block entities to the corrected data, so a later Save round-trips
> cleanly.

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

**Wiring a chain — which jigsaw carries what.** Vanilla's `JigsawBlock.canAttach` connects two
jigsaws when their fronts are opposite, their tops match (for `aligned`), and **the first's
`target` equals the second's `name`**. So:

- `name` is *this* jigsaw's own identity — what other pieces aim at.
- `target` is the name of the jigsaw it wants to meet.
- `pool` is the pool to draw that next piece from.

Only the **outgoing** (upward) side of each joint needs `pool` + `target`; the receiving
(downward) side just needs its `name` set, with `pool`/`target` left `minecraft:empty`. Because
assembly runs bottom-up, the piece in `shaft_bottom` **must** be the one carrying a `pool` — a
bottom piece with `pool: minecraft:empty` is a dead end and the chain silently stops at one piece.
Worked example, the three-piece `stairs_2` chain:

| Piece | Joint | Name | Target Pool | Target Name |
|---|---|---|---|---|
| `stairs_2_bottom` | up | `dungeons2:stairs_2/bottom_up` | `dungeons2:transitions/classic/shaft_segment` | `dungeons2:stairs_2/mid_down` |
| `stairs_2_mid` | down | `dungeons2:stairs_2/mid_down` | `minecraft:empty` | `minecraft:empty` |
| `stairs_2_mid` | up | `dungeons2:stairs_2/mid_up` | `dungeons2:transitions/classic/shaft_top` | `dungeons2:stairs_2/top_down` |
| `stairs_2_top` | down | `dungeons2:stairs_2/top_down` | `minecraft:empty` | `minecraft:empty` |

A jigsaw `name` must be unique to its role — two pieces sharing a name makes which one gets
attached ambiguous.

> **A chain with no `dungeons2:door` or `dungeons2:connector` markers anywhere is discarded.**
> `scanTransitionGeometry` returns `null` when it finds no markers at all, and the planner falls
> back to the synthetic placeholder — so an otherwise perfectly assembled chain renders as
> nothing. Doors go on the bottom and top pieces; middle segments don't need any.

`dungeons2:door` candidates from **both** ends get read back after assembly (bucketed by Y, so
which pool contributed them doesn't matter) and become that floor's `candidateDoorways` — the
bottom piece's candidates restrict the lower floor's START room, the top piece's restrict the
upper floor's END room, exactly mirroring how the entrance's descent candidates drive floor 0's
START room.

**A chain's footprint sprawls, and the planner measures it rather than guessing.** Vanilla places
the chain's *first* piece where it's asked to and lets the rest sprawl outward from there, so a
chain's real (union) footprint is bigger than any single piece and its min corner is **offset**
from the assembly point by a rotation-dependent amount — `stairs_2`'s union is 7×12 sitting up to
11 blocks negative of where the chain was anchored. The planner handles this by assembling twice:
once to *measure* the union (nothing is kept), then again with the same seed anchored so the union
lands on a slot reserved at that measured size. Two consequences for authoring:

- **Every element in a transition pool must be `"projection": "rigid"`.** A `terrain_matching`
  entry snaps to the heightmap, so the measured shape wouldn't survive being re-anchored, and
  the planner's guard would reject the transition every time — it would silently never generate.
- **Union size is what has to fit**, not piece size. A chain whose union is too big to fit the
  link's placement bound alongside the reserved start slot just re-rolls the pool; author a
  smaller self-contained alternative into `shaft_bottom` so cramped links have somewhere to land
  (`ladder1` / `stairs_1` serve this purpose today).

### Room jigsaw pool

Ordinary interior ("NORMAL") rooms can also be hand-authored prefabs instead of always
procedural. Much simpler than transitions: **one pool, no chaining, one Y anchor.**

Lives under `data/dungeons2/worldgen/template_pool/rooms/<motif>/` (currently only `classic/`
is authored; see the motif-naming note above — e.g. a desert theme would add
`rooms/desert/normal.json`).

| Pool | Role | Contents |
|------|------|----------|
| `dungeons2:rooms/<motif>/normal` | the only pool | Complete, self-contained pieces (`minecraft:single_pool_element`, like `ladder1.nbt`/`stairs_1.nbt`) with `dungeons2:door` (and optionally `dungeons2:connector`) candidates around the perimeter at local Y=0, the room's own walking plane. No assembly joints, no segments, no top/bottom split — a room is never chained. |

Per floor, the planner tries a small, fixed number of candidate slots (currently 2). For each
it assembles the prefab once to **measure** it, reserves a slot at that real size (kept clear
of the floor's own boundary), then assembles it again anchored so it lands exactly there, and
hands its footprint and door markers to the maze as one of `MazeLevelGenerator2D`'s **supplied
rooms**, restricted to those candidate doorways exactly like the entrance/transition START/END
rooms are. A failed or colliding attempt is simply skipped; ordinary procedural fill rooms
cover the gap, same graceful degradation as an empty entrance/transition catalog. There's no
height-budget constraint like transitions have — a room is whatever height you build it to,
same as any other authored room.

**Rooms need `"projection": "rigid"` for the same reason transitions do** (see the sprawl note
in the transition section above). Vanilla is free to *rotate* a prefab, which moves its
bounding box's min corner off the position it was asked for — all four shipped 7x7 prefabs get
displaced 6 blocks west and/or north in three of the four rotations — so the planner has to
measure the displacement before it can reserve anything. Measuring only works if re-anchoring
the prefab translates it and changes nothing else, which `terrain_matching` would break.
(Before this was measured, 2026-07-30, **44% of all prefab room slots were being silently
dropped** and covered by procedural fill.)

Add real content by creating `data/dungeons2/worldgen/template_pool/rooms/<motif>/normal.json`
(same shape as `transitions/<motif>/shaft_bottom.json`, just `single_pool_element` entries with
no outgoing joint) plus the `.nbt` files it references — nothing else needs to change, the
mechanism already reads whatever pool entries exist. `classic/normal.json` is the only one
authored today.

---

## Floor patterns (`dungeons2/floor_pattern_config/<motif>.json`)

Ordinary procedural rooms can decorate their floor with a hand-designed pattern instead of the
plain/alternating default, picked per room by a **datapack-driven, weighted roll** — same codec
+ registry shape as `dungeons2/generation_config/<name>.json` (the `corridorWidth` knob). This is
orthogonal to the room jigsaw pool above: it decorates a *procedural* room's floor, not a
hand-authored prefab (a prefab is whatever you built it as, floor included).

**Motif-scoped**, same naming convention as `rooms/<motif>/normal.json`/
`transitions/<motif>/shaft_bottom.json` above and the weathering processor lists: entries live at
`data/dungeons2/dungeons2/floor_pattern_config/<motif>.json` (there's currently one shipped
entry, `classic`). A motif with no entry (or a missing registry entirely) degrades to always
plain, the same graceful degradation an absent template pool always has — **no two-tier fallback**
to a shared/classic config, matching the rooms/transitions motif-naming note above. Each entry is
a weighted list:

```json
{
  "elements": [
    { "type": "empty", "weight": 3 },
    { "type": "border", "weight": 1, "inset": 2 },
    {
      "type": "border",
      "weight": 1,
      "inset": 2,
      "cornerBlock": "minecraft:andesite",
      "edgeLeftBlock": "minecraft:polished_andesite",
      "edgeRightBlock": "minecraft:polished_andesite"
    }
  ]
}
```

`type` is a plain string, not an enum — `"empty"` (or any unrecognized type) means no special
pattern, same graceful degradation an absent/empty pool always has elsewhere; `"border"` selects
the decorative frame described below, with `inset` (default 2) and the three block-substitution
fields (see below) as its tunables. A missing `floor_pattern_config` registry, or a config with
no elements or non-positive total weight, also falls back to `"empty"`. The roll happens once per
room, using that room's own deterministic seed (`DungeonRoomPiece#deterministicRandom`) — so it
stays the same across the repeated `postProcess` calls a piece gets per overlapping chunk, exactly
like every other per-room roll in this pipeline.

### The `border` pattern

Reverse-engineered from a hand-authored reference structure (`dungeonblocks:left_large_stone_brick`
/ `right_large_stone_brick`, a picture-frame ring inset from the floor's edge) and generalized to
**any** floor width/depth in `FloorBorderPatternProvider`:

- The ring is the perimeter of the rectangle `[inset, size-1-inset]` on each axis.
- Each of the **4 corners** is the *corner block*, facing the cardinal direction reached walking
  the ring clockwise from north (NW→north, NE→east, SE→south, SW→west).
- Each **straight run** between two corners alternates the *left*/*right edge blocks* starting
  with left, facing outward along that edge's own cardinal direction (so a longer edge just keeps
  alternating — there's no fixed run length).
- Everywhere else — outside the ring, and inside it — is plain floor.
- **Degenerate sizes degrade gracefully**: if the floor is too small to fit a ring at the
  requested `inset` (fewer than 2 cells of ring width on either axis), the whole floor is plain.

**The three blocks are substitutable per config entry**, not hardcoded to
`dungeonblocks:left_large_stone_brick`/`right_large_stone_brick` — that pair is only the
*default* for whichever of `cornerBlock`/`edgeLeftBlock`/`edgeRightBlock` a `border` entry leaves
unset. Each is a plain resource-location string, resolved independently — you don't need to
override all three to change one, and an id that doesn't resolve (typo, unloaded mod) just falls
back to that slot's own default rather than breaking the whole entry. Set `edgeLeftBlock` and
`edgeRightBlock` to the **same** id for a single-block edge with no left/right texture
variant (the `classic.json` example above does exactly this with `minecraft:polished_andesite`,
plus a plain `minecraft:andesite` accent at the corners) — the alternation still happens
internally, it's just invisible when both slots resolve to the same block.

**Orientation is applied generically**, not just to `dungeonblocks` pieces: a substituted block
only gets a facing set if its blockstate actually has one (`IFacingBlock.FACING` or vanilla's
`HorizontalDirectionalBlock.FACING`, checked in that order) — a plain cube like polished andesite
has neither, so it's placed as-is with no orientation attempt. This means substituting in *any*
block (plain cubes, stairs, vanilla directional blocks, another mod's `dungeonblocks`-style
paired pieces) works without extra configuration.

The geometry (`FloorBorderPatternProvider.plan`) is pure data — no `BlockState`, no Forge
registry — specifically so it's unit-testable without a running Forge instance;
`dungeonblocks:*` blocks only resolve once Forge has actually loaded that mod, which a bare
`Bootstrap.bootStrap()` JUnit environment never does (same limitation `DecorationOnRealRoomTest`
already documents for a different block). Block *substitution*, correspondingly, isn't resolved
until `build()` actually runs — constructing a `FloorBorderPatternProvider` never touches a
registry by itself. `FloorBorderPatternProviderTest` checks the geometry against the reference
structure's exact palette, and checks substitution end-to-end using vanilla blocks (which *do*
resolve under a bare bootstrap, unlike `dungeonblocks:*`).

---

## Weathering / decoration (`worldgen/processor_list/<motif>_weathering.json`)

Aging (mossy, cracked, crumbled-to-cobble) is a **vanilla
`minecraft:worldgen/processor_list`**, and the same file decorates both halves of a
dungeon:

> The file is called *weathering* even though it also grows lichen and hangs cobwebs,
> because in a dungeon those **are** weathering — mold, lichen and cobwebs all say the
> same thing about the place's age as a cracked brick does. One motif, one file.

- **Prefabs** — each pool element's `"processors"` field points at it, the ordinary
  vanilla way (`"processors": "dungeons2:classic_weathering"`).
- **Procedural rooms / corridors / doors** — `DungeonPiece.placeAll` runs the same list
  over its own blocks via `PieceProcessors`, using vanilla's `processBlockInfos` with no
  template involved.

So a hand-authored prefab and the procedural room next to it weather identically, from
one file. Adding a theme's weathering is pure data: create
`data/dungeons2/worldgen/processor_list/<motif>_weathering.json`. A motif with no such
file simply generates undecorated — the same graceful degradation a missing pool has.

### Which processor to use

The shipped list runs three, deliberately split by what each can express:

| Processor | Use it for | Why |
|---|---|---|
| `minecraft:rule` (vanilla) | plain full cubes — `stone_bricks`, `cobblestone`, `polished_andesite` | Standard vanilla, nothing custom needed. |
| `dungeons2:aging` (ours) | **shaped blocks** — stairs, slabs, walls, fences, pillars | A vanilla `ProcessorRule` emits one fixed `output_state` and **drops the input's properties**, so ageing a stair with it silently resets facing/half/shape. `dungeons2:aging` copies every property the source and replacement share, so one rule ages a whole family. It also supports multi-stage decay chains. |
| `dungeons2:decoration` (ours) | anything decided by a block's **neighbours** — cobwebs, creeping growth | The other two decide one block at a time and can't see what's next to it. This one gets the whole block list. |

`dungeons2:aging` entries look like:

```json
{
  "processor_type": "dungeons2:aging",
  "agings": 2,
  "rules": [
    { "block": "minecraft:stone_brick_stairs",
      "output_blocks": [
        { "block": "minecraft:mossy_stone_brick_stairs", "probability": 0.3 },
        { "block": "minecraft:cobblestone_stairs",       "probability": 0.3 }
      ] }
  ]
}
```

`output_blocks` is a **chain**: a stage is only reachable if the stage before it was
rolled, so the example gives 30% mossy of which 30% decay further to cobblestone.
`agings` caps how many stages may be applied (default `1` = first stage only) — read it
as how many rounds of decay the structure has been through. Two rules with the same
`block` act as alternative chains; the first that decays at all wins.

A `dungeons2:decoration` entry is a set of independent behaviours, **all off by default**.
Each is an object, not a bare number, so a behaviour carries its own palette:

```json
{
  "processor_type": "dungeons2:decoration",
  "cobwebs":     { "probability": 0.02, "blocks": ["minecraft:cobweb"] },
  "wall_growth": { "probability": 0.04, "bonus": 0.22, "max": 0.55,
                   "blocks": ["minecraft:glow_lichen", "dungeonblocks:lichen"] },

  "dirt":           { "tags": ["minecraft:dirt"] },
  "floor_growth":   { "probability": 0.35, "blocks": ["minecraft:brown_mushroom"] },
  "hanging_growth": { "probability": 0.3,  "blocks": ["minecraft:hanging_roots"] },

  "underwater_growth": { "probability": 0.15, "blocks": ["minecraft:seagrass"] },
  "floating_growth":   { "probability": 0.02, "blocks": ["minecraft:lily_pad"] },

  "unsupported": { "tags": ["dungeonblocks:corbels", "dungeonblocks:ledges"] }
}
```

| Behaviour | Fires on | Writes to |
|---|---|---|
| `cobwebs` | air with a **horizontally adjacent solid** — corners and wall faces, not open floor | that cell |
| `wall_growth` | air beside a solid block | that cell, attached to the wall's face |
| `floor_growth` | a `dirt` block with air above | the cell above |
| `hanging_growth` | a `dirt` block with air below | the cell below |
| `underwater_growth` | water with a **solid floor** under it | that cell (replacing the water) |
| `floating_growth` | water with air above | the cell above |
| `unsupported` | a matching block whose **wall behind it** has become air | clears it to air |

Every behaviour takes `probability` (absolute — one roll per candidate position, *not*
conditional like the rule/aging chains) and `blocks` (a palette, picked from uniformly).
`wall_growth` adds two more:

| Field | Default | Meaning |
|---|---|---|
| `bonus` | `0.0` | Added **per already-grown neighbour** (all 6 directions). This is what makes growth spread in patches instead of an even speckle — turn it up for bigger, blotchier patches. |
| `max` | `1.0` | Cap on `probability + n × bonus`. |

A candidate **inherits the species of the growth touching it**, so a patch is one
organism, not a mosaic. Give `wall_growth` multiface (glow-lichen-like) blocks so the face
attaches; anything else is placed in its default state.

`dirt` and `unsupported` say which blocks a behaviour applies *to* (rather than what it
places), and take `blocks` and/or `tags` — the union of both. Prefer tags: `minecraft:dirt`
already covers the whole family, and DungeonBlocks ships `dungeonblocks:corbels` /
`dungeonblocks:ledges` covering one variant per stone type.

### How `unsupported` decides

**A block that faces somewhere is held up from behind, and by nothing else.** A corbel or a
ledge is bracketed onto a wall and juts out from it — it *gives* support to whatever sits on
top of it, it doesn't *take* support from there, nor from the block below, nor from whatever
happens to be beside it. So for any block with a `facing` property exactly one neighbour is
consulted: the one at `facing.getOpposite()`. Lose the wall, lose the corbel, regardless of
what else is around it.

That's the right neighbour because DungeonBlocks' corbels and ledges put their backing plate
on the face *opposite* FACING (a north-facing corbel's post occupies the south of its cell),
and set FACING from the placing player's direction reversed — the same convention vanilla's
ladder uses.

A block with **no** `facing` has no "behind", so a looser rule stands for it: kept unless air
is seen on *every* side including below. Both paths err towards **keeping** the block, since a
false positive deletes architecture somebody authored:

- **Support is "not air", not "is a full cube."** The behaviour is for a ledge whose wall
  *crumbled away*, and crumbling produces air. A ledge mounted on a stair, a slab or another
  ledge stays put.
- **A neighbour that isn't part of the piece counts as support.** Absent means "this piece
  places nothing here", not "here is nothing" — the wall may belong to the adjoining piece,
  or lie outside a prefab's bounds.

It follows that `unsupported` only ever fires where weathering actually produced air, so how
often you see it is really a property of the aging rules, not of this setting.

**The behaviours are independent, not a chain.** A dirt block is both "dirt" and "solid",
so it can sprout mushrooms above *and* grow lichen on its side. Where two behaviours want
the same cell, the first one reached takes it.

> **Everything writes into air.** A behaviour only ever replaces a cell **the piece itself
> places as air** — the two exceptions being `underwater_growth`, which replaces the piece's
> own water, and `unsupported`, which clears its own block. Nothing here can touch the
> terrain around the dungeon. Procedural rooms and corridors emit their interior air, and
> every shipped `.nbt` is authored with real `minecraft:air` — but a prefab whose interior
> is `minecraft:structure_void` **will not decorate**, because a void is neither air nor
> solid.

> **`floor_growth` / `hanging_growth` work on aged dirt too.** Classic dungeons author no
> dirt at all; the dirt they grow on is what `dungeons2:aging` makes out of decayed stairs.
> That works because aging and decoration share a pass and run in the order this file lists
> them — so **keep `dungeons2:aging` above `dungeons2:decoration`**. A test enforces it.

**These files may contain comments.** Minecraft loads datapack registry JSON through
`RegistryDataLoader` &rarr; `JsonParser.parseReader`, which puts Gson in lenient mode, and
lenient Gson skips `//`, `#` and `/* */`. `classic_weathering.json` uses `//` comments to
record the conditional-probability rule below at the point of use. (Two caveats: strict
JSON tooling &mdash; including PowerShell's `ConvertFrom-Json` &mdash; will choke on them,
and this leniency is *not* guaranteed for every JSON file in the mod. It holds for vanilla
datapack **registry** files like `worldgen/processor_list`; a custom
`SimpleJsonResourceReloadListener` may parse strictly.)

**Two rules for authoring these, both about chunk-safety:**

1. **Only vetted processors.** A procedural piece is re-rendered once per chunk it
   overlaps, so each block is processed more than once and must resolve the same way
   every time. `PieceProcessors` therefore runs the list in **two passes**, split on one
   question — *does this processor read the level?*

   - **Level-independent, unclipped.** `dungeons2:aging` and `dungeons2:decoration`, and
     anything else implementing GottschCore's `LevelIndependentProcessor` marker in code
     (both processors themselves live in GottschCore; only the registered *names* are
     ours, which is why the `processor_type` values are still `dungeons2:`). These get
     the piece's **whole** block list, in the order this file lists them. Decoration
     *needs* that (a neighbour map built from one chunk's slice would be missing
     everything across the seam); aging is there so the two stay in authored order.
   - **Level-reading, clipped.** `minecraft:rule`, which reads the existing world block
     for its `location_predicate` — and reading outside the current region during
     worldgen is illegal, so it only ever sees the part of the piece inside the chunk.
     It survives the repeat because it seeds its random from the block's absolute world
     position.

   A third-party processor is in **neither** category and must not be added:
   `minecraft:capped`, for instance, counts across the block list, isn't marked, and so
   would land in the clipped pass and cap per chunk. A test enforces the allowlist and
   the markers (`WeatheringProcessorListTest`).

   One consequence worth knowing: vanilla runs a **prefab** through the unsplit list, so
   `minecraft:rule` gets to go before anything neighbour-aware; on a **procedural piece**
   it goes last. That's invisible for the shipped list, whose rules only swap one full
   cube for another and so never change what decoration keys off (air, solidity, block
   identity). Keep it that way: a rule that turns a block into **air** would show the
   difference.
2. **Probabilities are conditional, not absolute** — in `minecraft:rule` and
   `dungeons2:aging` (but *not* `dungeons2:decoration`). A vanilla rule
   produces one output state with no weighted variant list, so several variants of the
   same source block are consecutive rules, first match wins, each rolling only after the
   previous missed. Three variants at 10% each are authored `0.1`, `0.1111`, `0.125`, not
   `0.1, 0.1, 0.1`. (Rules for a *different* source block don't interfere — the block
   check short-circuits before the roll.) The same applies down an aging chain.
   `WeatheringProcessorListTest` asserts the composed absolute rates, so if you edit the
   numbers, update the expectations there.

All four pools — entrance (`surface_entrance` + `descent`), transitions, and rooms — point at
the weathering list, so every authored prefab ages by default.

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
