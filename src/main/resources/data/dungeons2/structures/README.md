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

**Motif/theme naming (transitions & rooms only; the motif config follows the same idea, see below):**
the start pool a dungeon assembles from is selected by motif, one folder per theme:
`transitions/<motif>/shaft_bottom.json`, `rooms/<motif>/normal.json` (e.g.
`rooms/desert/normal.json` for a `DESERT`-themed room pool). Motif selection itself is still
hardcoded to `classic` (see `DungeonStructure.findGenerationPoint`) — this is just the naming
mechanism, ready for whenever motif selection varies for real. A missing themed pool degrades
gracefully to plain procedural generation for that floor/room/transition, same as an empty pool
always has; a smarter two-tier fallback (missing theme → shared/classic pool instead of straight
to procedural) is a possible future phase, not implemented. **The entrance is parametrized too as
of 2026-08-13** — `entrance/<motif>/surface_entrance.json` → `.../descent_ladder.json` →
`.../descent.json`. It was the last one done because it is the only *chained* assembly: its pieces
name each other's pools in fields baked into compressed NBT rather than in any JSON.

> **Only the `pool` field is motif-scoped.** A jigsaw's three id-shaped fields are not the same
> kind of thing. `pool` names a real `template_pool` resource, so it moves when the pool moves.
> `name` and `target` are just labels vanilla matches against each other when picking a joint —
> the pool already restricts which pieces are candidates, so scoping the labels too would buy
> nothing and would force every new motif to re-label joints that mean the same thing. So a new
> motif's entrance chain reuses `dungeons2:entrance/ladder_top` etc. verbatim and only points its
> `pool` fields at its own folder. `PoolWiringTest` enforces exactly this, and is what
> catches the in-game-Save revert described further down.

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

Taking the live chain's first link as the shape of a joint — the surface building reaching down
for the shaft, and the shaft's top reaching back up:

| Field        | Surface building → shaft                  | Shaft top (mates upward)              |
|--------------|-------------------------------------------|---------------------------------------|
| `name`       | `dungeons2:entrance/surface_entrance`     | `dungeons2:entrance/ladder_top`       |
| `target`     | `dungeons2:entrance/ladder_top`           | `dungeons2:entrance/surface_entrance` |
| `pool`       | `dungeons2:entrance/classic/descent_ladder` | `minecraft:empty` (or next pool)    |
| `final_state`| `minecraft:stone_bricks`                  | `minecraft:stone_bricks`              |
| `joint`      | `aligned`                                 | `aligned`                             |
| orientation  | front = **down** → `down_south`           | front = **up** → `up_north`           |

> **`pool` is motif-scoped; `name` and `target` are not** — see the motif note at the top of this
> file. That asymmetry is deliberate, not an oversight, and `PoolWiringTest` enforces it.

Vertical joints are supported (trial chambers / ancient cities chain vertically). Register
each variant in a `template_pool` JSON under
`data/dungeons2/worldgen/template_pool/entrance/<motif>/` and assemble with a small `max_depth` so
it never recurses into the dungeon. The terminal piece carries the `dungeons2:door` candidates
(role 1 above) on its floor-0 walking plane — **their Y defines floor 0's walking plane** and
their cells become the START room's `candidateDoorways`.

**Worked example, the three-piece `classic` entrance chain (2026-07-30, confirmed in game):**
each piece independently swappable later (e.g. a future `entrance_ladder_2` alternative in the
same pool), unlike the monolithic two-piece pair it replaced (`surface_exit.nbt`/`descent_1.nbt`,
which lingered unreferenced until they were deleted on 2026-08-13).

| Piece | Role | Joint | Name | Target Pool | Target Name |
|---|---|---|---|---|---|
| `entrance_1` | root — surface building | bottom (outgoing) | `dungeons2:entrance/surface_entrance` | `dungeons2:entrance/classic/descent_ladder` | `dungeons2:entrance/ladder_top` |
| `entrance_ladder_1` | middle — vertical shaft | top (incoming) | `dungeons2:entrance/ladder_top` | `minecraft:empty` | `minecraft:empty` |
| `entrance_ladder_1` | middle — vertical shaft | bottom (outgoing) | `dungeons2:entrance/ladder_bottom` | `dungeons2:entrance/classic/descent` | `dungeons2:entrance/room_top` |
| `entrance_exit_1` | terminal — floor-0 room, carries the `dungeons2:door` candidates | top (incoming) | `dungeons2:entrance/room_top` | `minecraft:empty` | `minecraft:empty` |

Note the asymmetry in that table, and that it is deliberate: **Target Pool** carries `classic/`,
**Name** and **Target Name** do not (see the motif note at the top of this file).

Registered as `entrance/classic/surface_entrance.json` (root) → `entrance/classic/descent_ladder.json`
→ `entrance/classic/descent.json` (repointed at `entrance_exit_1`, replacing the old test piece). A
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
| `dungeons2:transitions/<motif>/shaft_bottom` | **start pool** (what the planner assembles from, at the lower floor's plane) | Either a **complete, self-contained piece** with no outgoing joint — this is exactly what `ladder1.nbt`/`stairs_1.nbt` already are, registered here unchanged — or a **bottom segment**: `dungeons2:door` candidates at its own (lower) floor level + one **upward** assembly joint into that chain's own segment pool. |
| `dungeons2:transitions/<motif>/<chain>/segment` | optional, repeatable middle piece(s) | Down joint (mates to whatever sent the connection up) + up joint (continues further) — no doors, corridor/decoration between the two ends. Only needed once you're authoring segmented chains; doesn't need to exist otherwise. |
| `dungeons2:transitions/<motif>/<chain>/top` | terminal piece | `dungeons2:door` candidates at the *upper* floor's level + one **downward** joint (mates to whatever's below it), no further upward connection. |

**Continuation pools are named per chain, not per role** (backlog #11, renamed 2026-08-14 from
`shaft_segment`/`shaft_top`). A middle pool's contents are specific to one authored staircase — it
means "the middle of `stairs_2`", not "any middle" — so a second chain sharing the name would
describe neither. Vanilla would not *mis*-assemble in that case (`canAttach` requires the source's
`target` to equal the candidate's `name`, so a foreign segment is simply rejected), but every
placement attempt against the wrong entries is wasted and the name stops documenting anything.

**`shaft_bottom` deliberately keeps its role name.** It genuinely is the generic start pool, and
multiple unrelated transitions coexisting there as weighted alternatives is the design — self-
contained pieces and segmented chains mix freely. All pieces in one chain should share the same XZ
footprint so walls line up.

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
| `stairs_2_bottom` | up | `dungeons2:stairs_2/bottom_up` | `dungeons2:transitions/classic/stairs_2/segment` | `dungeons2:stairs_2/mid_down` |
| `stairs_2_mid` | down | `dungeons2:stairs_2/mid_down` | `minecraft:empty` | `minecraft:empty` |
| `stairs_2_mid` | up | `dungeons2:stairs_2/mid_up` | `dungeons2:transitions/classic/stairs_2/top` | `dungeons2:stairs_2/top_down` |
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

Per floor, the planner tries `roomTemplateAttemptsPerFloor` candidate slots — see
**[Prefab frequency](#prefab-frequency-roomtemplateattemptsperfloor)** below. For each
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

#### Floor pitch (`floorHeight`, `gapBetweenFloors`) — read this before changing it

> **This is not a tuning knob.** Their sum is the floor-to-floor **pitch**, and the pitch is the
> exact distance every entrance and transition template Dungeons2 ships was cut for. Change it and
> **you must author your own entrances and transitions.** Nothing in the code can fix that from
> inside the config — the geometry lives in `.nbt` files.

```json
{
  "floorHeight": 10,
  "gapBetweenFloors": 2
}
```

`floorHeight` is the budget above a walking plane (**6–24**, default 10); `gapBetweenFloors` is the
stone buffer below it (**0–8**, default 2). Pitch = 12 as shipped.

What breaks, concretely:

| template | how it spans 12 | at another pitch |
|---|---|---|
| `stairs_1` | two door markers 12 apart, in one 21-tall piece | cannot stretch — re-cut |
| `ladder1` | two door markers 12 apart, in one 18-tall piece | cannot stretch — re-cut |
| `stairs_2` chain | `bottom` + *n* × `mid`, **6 of rise per segment** | lands only on multiples of 6 |
| entrance | its bottom door marker **defines** floor 0's walking plane | floor 0's ceiling moves away from the entrance chamber's own |

So pitch 18 needs only the two monolithic templates re-cut — the chain follows for free. **Pitch 15
needs all three**, because 6 does not divide 15 and the chain would stop 3 blocks off the plane.

Three things tell you when it is wrong, and none of them is silent:

- the shipped `default.json` carries a `_comment` saying all of the above, in the file;
- `[D2-PITCH]` logs at **WARN**, once, when a non-shipped pitch is loaded;
- `[D2-SPAN]` logs at **ERROR**, per assembled transition, when the templates in play cannot reach.

`TransitionSpanTest` also fails the build for the shipped templates, so this cannot be changed in
the mod itself without noticing.

> The `_comment` key is declared purely so the file can carry that warning — it decodes to nothing
> and is never written back. **The schema is otherwise still closed:** any other unrecognized key is
> a load error, so a typo does not go quiet.

#### Room height (`roomHeightBands`)

A room's height is a `5 + rand(6)` roll (5–10) **clamped into the band its footprint selects**.
Bands live in the same `data/dungeons2/dungeons2/generation_config/<name>.json`, are matched *in
order* against the room's long side (`max(width, depth)`), and the first match wins:

```json
{
  "roomHeightBands": [
    { "maxLongSide": 7,  "minHeight": 6, "maxHeight": 10 },
    { "maxLongSide": 11, "minHeight": 5, "maxHeight": 9 },
    { "maxLongSide": 15, "minHeight": 5, "maxHeight": 8 },
    { "minHeight": 5, "maxHeight": 7 }
  ]
}
```

**The ceiling falls as the footprint grows, and that is the whole point.** The rule this replaced
was `min(rolled, max(width, depth))` — a cap that *rose* with the footprint, so the only rooms that
could be tall were the big ones. A big tall room is a box; a small tall room is a shaft or a nave.

Rules the loader enforces, because both ways of getting the table wrong are otherwise silent:

- the last band **must** omit `maxLongSide` (it is the catch-all, so every footprint matches
  something), and no earlier band may omit it (an open-ended band in the middle makes every band
  after it unreachable while still loading cleanly);
- `maxLongSide` values must strictly increase;
- `minHeight <= maxHeight`;
- an undeclared key or a malformed value is a load error, not a silent default.

Omitting `roomHeightBands` entirely keeps the shipped taper rather than removing the cap — "absent"
must not mean "uncapped", because uncapped is the tall-box outcome this exists to prevent. A band
whose `maxHeight` exceeds the planner's floor height is refused at generation time with a
`[D2-HEIGHT]` log line, since a taller room would push its ceiling into the floor above.

Measured over 76,285 procedural rooms (`RoomHeightProbe`):

| | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|
| before | 16.7% | 16.2% | 30.1% | 12.3% | 15.2% | 9.5% |
| **after** | **12.3%** | **20.6%** | **24.6%** | **22.8%** | **15.2%** | **4.4%** |

Heights pile up at a band's edges — every roll above `maxHeight` lands on `maxHeight`. That is not
new; the old cap piled on the long side in exactly the same way.

> **Editing this changes existing seeds' room heights, but not their layouts.** The roll stays
> where it is and the band clamps the result, so the planner's random stream is identical whatever
> table is loaded: mazes, footprints and corridors of existing worlds are untouched.

#### Prefab frequency (`roomTemplateAttemptsPerFloor`)

How many prefab rooms a dungeon gets is a datapack knob, in
`data/dungeons2/dungeons2/generation_config/<name>.json` alongside `corridorWidth`:

```json
{
  "corridorWidth": 3,
  "roomTemplateAttemptsPerFloor": 4
}
```

Range **0–8**, default **4**. It is *attempts*, but adoption measures at 100%, so in practice it is
the number of prefabs per floor. Measured over 200 MEDIUM dungeons of 3 floors:

| attempts/floor | prefabs per dungeon | prefab share of rooms | dungeons missing a given template |
|---|---|---|---|
| 2 (the old hardcoded value) | 6.0 | 11.3% | ~18%\* |
| **4 (shipped)** | **12.0** | **20.6%** | ~3%\* |

\* The first two columns are measured; the last is computed as `(3/4)^prefabs` for the four-entry
`classic` pool, because the placement harness uses a synthetic prefab and cannot see which pool
entry vanilla picked. It is trustworthy only because the computed 17.8% at 6 prefabs matches the
19% measured directly over 400 dungeons in Jul 2026. Making the real template id reach
`RoomData.templateId` (it is currently the constant `dungeons2:rooms/assembled`) would let a test
assert this properly.

**Raising this is the right lever, not pool weights.** Weights only reshuffle a fixed budget, so
favouring one template makes the others correspondingly rarer; only the attempt count changes how
many prefab rooms exist at all. The "missing a given template" column is what this fixes — at 2
attempts with four pool entries, nearly one dungeon in five contained no `7x7_junction_1` at all.

**`0` is a legitimate value** and is in range on purpose: it turns prefab rooms off without deleting
the pool, which is the only way to compare a dungeon with and without them.

**The cost is real.** Each attempt is *two* jigsaw assemblies — one to measure the rotated
footprint, one to place it — so doubling the count doubles that work per floor. It is piece-list
construction with no block placement, and the planner-side cost of the change measured at +8%, but
that was against a synthetic assembler; the real vanilla `JigsawPlacement` cost at 8 has not been
measured in game.

---

## Motif config (`dungeons2/motif_config/<motif>/`)

Everything a motif renders with — the base architectural block for each element, plus the weighted
list of **room schemes** a room is decorated from — lives in **one folder per motif**, a codec-backed
datapack registry with the same shape as `dungeons2/generation_config/<name>.json` (the
`corridorWidth` knob).

The split to keep in mind while authoring: the element sections say what the motif is **made of**
(one block per slot, no choices), and `schemes` says how a room is **dressed** (the only thing here
that is rolled).

> **Migrated Jul 2026.** This replaced two separate systems: `data/dungeons2/block_provider/<motif>.json`
> (base blocks, loaded by a reload listener into `BlockProvider`/`BlockSet` maps keyed by enum
> instance) and `dungeons2/floor_pattern_config/<motif>.json` (floor decoration). Both answered
> "which block does this motif use here?" through different machinery, and the `block_provider`
> half's string→enum→map-key indirection silently swallowed lookup misses — which cost two real
> bugs. If you have a datapack using either old path, move it into `motif_config/<motif>.json`
> using the shape below; the old folders are no longer read.

> **Schemes, Jul 2026 — a second breaking change.** `floor.patterns` moved out of the `floor`
> section and became the top-level `schemes` list. Each old pattern entry becomes a scheme with that
> entry in its `floor` slot, keeping its `weight` verbatim; add a `name`. Nothing else changes, and
> a straight mechanical migration reproduces the old behaviour exactly. See **Room schemes** below
> for why the roll moved.

> **A motif became a folder, Aug 2026.** Everything under
> `data/dungeons2/dungeons2/motif_config/<motif>/` is read and combined into that motif's config, so
> a motif can be split across as many files as suits it — shipped `classic` is `base.json`,
> `schemes_floors.json`, `schemes_walls.json`, `schemes_ceilings.json`, `schemes_props.json`. A flat
> `motif_config/<motif>.json` is **still valid** (shipped `catacombs` and `deep_slate` are one file
> each); if both exist the flat file is the base layer. Nothing about the shape below changed —
> every file is the same schema, just not obliged to carry all of it.

**Combining rule.** Files are folded in id order, which for one namespace is plain file-name order
(and puts `<motif>.json` before anything in `<motif>/`, since it sorts as a prefix).

- **Sections** (`wall`, `ceiling`, `door`, `corridor`, `floor`) are taken **whole** from the last
  file that writes one. Not merged field by field — a section is already all-or-nothing (see below),
  and a file that says nothing about walls leaves the earlier file's walls alone.
- **`schemes` lists append**, in file order — except that a scheme whose `name` matches an earlier
  one **replaces it in place**, keeping its position. That is what makes a scheme addressable: an
  add-on can retune shipped `plain`'s weight with a three-line file. Within one datapack, though,
  two files using the same scheme name is a mistake, and one of the two silently disappears.

**Motif-scoped**, same naming convention as `rooms/<motif>/normal.json`/
`transitions/<motif>/shaft_bottom.json` above and the weathering processor lists (shipped:
`classic`, `catacombs`, `deep_slate`). Matching is on the **path**, not the full id, so a datapack
of your own namespace can add `data/<yourpack>/dungeons2/motif_config/classic/extra_schemes.json`
and have it land in `classic`. A motif with no files at all (or a missing registry entirely)
degrades to plain stone_bricks throughout, the same graceful degradation an absent template pool
always has — **no two-tier fallback** to a shared/classic config, matching the rooms/transitions
motif-naming note above.

```json
{
  "wall":     { "wall": "minecraft:stone_bricks" },
  "ceiling":  { "ceiling": "minecraft:stone_bricks" },
  "door":     { "door": "dungeonblocks:spruce_dungeon_door",
                "lintel": "minecraft:polished_andesite",
                "floor": "minecraft:polished_andesite",
                "probability": 0.7 },
  "corridor": { "floor": "minecraft:cobblestone",
                "alternateFloor": "minecraft:gravel",
                "ceiling": "minecraft:stone_bricks",
                "height": 5 },
  "floor": {
    "base": "minecraft:stone_bricks",
    "alternateBase": "minecraft:stone_bricks"
  },
  "schemes": [
    { "name": "plain", "weight": 8 },
    { "name": "andesite_border", "weight": 1, "minSize": 5,
      "floor": { "type": "border", "inset": 2,
                 "cornerBlock": "minecraft:andesite",
                 "edgeLeftBlock": "minecraft:polished_andesite",
                 "edgeRightBlock": "minecraft:polished_andesite" } }
  ]
}
```

**Every section is optional; every *block* inside a section it uses is required.** Omit `door`
entirely and you get the default oak door — but a `door` section with only `door` and no `lintel`
**fails to load, loudly**. That asymmetry is deliberate: it is exactly the silent-fallthrough the
old system was retired for. (It needs `Codecs.strictOptionalFieldOf`, because DFU's own
`optionalFieldOf` cannot tell "absent" from "malformed" and returns the default for both.)

The one non-block field is `door.probability` (0.0–1.0, default 1.0): the chance a doorway is hung
with an actual door. It is optional precisely *because* it is not a material — there is a sensible
default to fall back on, where for a block there deliberately is not. A dungeon with a working door
on every single opening reads as a building someone still maintains, which is the opposite of the
thing being generated; `classic` uses 0.7, so about a third of its doorways stand open. **A doorless
opening still gets its sill and lintel**, so it reads as a framed opening rather than as a hole, and
it is the same four-block column a doored one is.

The other non-block field is `corridor.height` (5–8, default 5): the corridor's wall height in
blocks, so the passage runs `floorY .. floorY + height - 1` — floor row at the bottom, ceiling row
at the top, `height - 2` rows of air between. It is the motif's **baseline** geometry, used
dungeon-wide unless the motif also authors `corridor.styles` (below), in which case it becomes
the fallback rather than the value.

The range is not arbitrary at either end. Below 5 the ceiling would land inside the doorway, which
is a fixed four-block column (sill / two door halves / lintel) at every height — a taller corridor
only ever adds rows *above* the door. Above 8 the corridor stops fitting in the 10-block slab a
floor gets. An out-of-range value **fails to load** rather than clamping back to 5, for the same
reason a `door` section missing its `lintel` does: a datapack that asks for 12 and silently gets 5
is the failure mode this whole config was rebuilt to make impossible.

`corridor.profile` shapes the top of that column: `flat` (the default) is a single ceiling row,
`arched` keeps that crown row and turns the row below it into stair haunches leaning into the
walls, so the ceiling springs off the wall instead of meeting it square. An arch therefore *borrows*
a row rather than needing an extra one.

```jsonc
"corridor": { "floor": "minecraft:stone_bricks",
              "alternateFloor": "minecraft:stone_bricks",
              "ceiling": "minecraft:stone_bricks",
              "height": 7,
              "profile": "arched",
              "archBlock": "minecraft:stone_brick_stairs" }
```

`archBlock` is **required** when the profile is `arched` — the same rule as a `door` section with
no `lintel`. Silently defaulting it would put stone brick stairs in a deepslate corridor.
`profile: arched` also requires `height` of at least 6: the haunch row is `height - 2`, and at
height 5 that is the doorway's lintel row, so the arch would stair-block every door.

`corridor.narrowHeight` is the ceiling height for a cell that is only **one cell wide** — walls
facing each other across it. It **defaults to no drop**, and dropping it is opt-in.

That default was arrived at the hard way. Full height reads fine in a passage you can see across and
reads as a slot canyon in one you cannot, so this originally defaulted to one course below `height`.
But corridor width fluctuates cell by cell after dilation, so a per-cell drop made the ceiling
staircase up and down along every run — visibly worse than the problem it solved. Author it for a
motif whose corridors are uniformly narrow; do not expect it to behave on corridors that pinch and
widen. (Doing the same thing per *run* rather than per cell is the version that would actually work,
and is not built.)

### Per-floor corridor styles

`corridor.styles` is a weighted list of **named** corridor geometries. One is rolled **per floor**,
and every corridor on that floor is built to it — so descending changes the character of the
passages, while the corridor you are standing in never changes shape halfway along.

```jsonc
"corridor": { "floor": "minecraft:cobblestone",
              "alternateFloor": "minecraft:stone_bricks",
              "ceiling": "minecraft:stone_bricks",
              "height": 7, "narrowHeight": 6,
              "profile": "arched", "archBlock": "minecraft:stone_brick_stairs",
              "styles": [
                { "name": "vaulted", "weight": 3, "height": 7, "narrowHeight": 6,
                  "profile": "arched", "archBlock": "minecraft:stone_brick_stairs" },
                { "name": "grand",   "weight": 1, "height": 8, "narrowHeight": 7,
                  "profile": "arched", "archBlock": "minecraft:stone_brick_stairs" },
                { "name": "cramped", "weight": 2, "height": 5, "profile": "flat" }
              ] }
```

A style carries the whole geometry set — `height`, `profile`, `archBlock`, `narrowHeight` — not just
a height. That is deliberate: varying height while leaving the profile pinned motif-wide gives the
tall floors an arch that was tuned for the short ones. `weight` defaults to 1 and must be at least
1; a style that can never be rolled is a load error rather than a silent no-op.

Every rule that applies to the `corridor` section applies to each style: the 5–8 range, arched
needing height ≥ 6, arched needing an `archBlock`, and `narrowHeight` not exceeding `height`. A
styles list is otherwise just a way to smuggle in the shapes the section itself rejects.

Two rules are specific to styles:

- **`name` is required, must not be blank, and must be unique within the list.** A generated
  corridor stores only its style's *name*, so a duplicate would make its geometry depend on the
  order of the list — silently, and only on the floors that happened to roll it.
- **The baseline does not join the roll.** A motif's own `height`/`profile`/`archBlock`/
  `narrowHeight` are what a motif with *no* `styles` generates, and the fallback if a saved corridor
  names a style that no longer exists. A motif that authors styles chooses among those only. If you
  want the baseline in the roll, author it as a style too — which is what the shipped `classic`
  does with `vaulted`.

Renaming or deleting a style does **not** break existing worlds: a corridor whose style name no
longer resolves falls back to the baseline, and its *height* is stored on the piece itself, so the
excavated shape is unchanged either way. Only the arch profile can shift.

### Corridor wall courses

`corridor.courses` is a list of horizontal bands laid over the corridor's wall columns — a plinth
along the base, a string course, a crown under the ceiling. It reuses the **same `CourseEntry`** a
room's `wall` slot takes, so `block`, `alternateBlock`, `alternate`, `anchor`, `offset`, `orient`
and `properties` all mean exactly what they mean there.

Author it on the `corridor` section for a motif-wide band, or inside a **style** so a floor's courses
answer to that floor's height:

```jsonc
{ "name": "vaulted", "weight": 3, "height": 7, "profile": "arched",
  "archBlock": "minecraft:stone_brick_stairs",
  "courses": [
    { "block": "minecraft:polished_andesite", "anchor": "bottom", "offset": 0 },
    { "block": "minecraft:polished_andesite", "anchor": "top",    "offset": 2 }
  ] }
```

A style's list replaces the baseline's outright — it is not merged, and an explicitly empty list
means *no courses*, not *inherit*.

**Anchor from the `top` for anything near the ceiling.** Corridor height is rolled per floor, so a
crown measured from the floor sits at a different distance from the ceiling on every floor of the
same dungeon. A course that resolves outside the column is **dropped, not clamped**, which is what
lets one style carry a plinth and a crown without knowing what height the floor rolled.

**`alternate: "strict"` alternates on `(x + z)` parity**, not on a run coordinate — a corridor winds,
so there is no `u` to count along. Parity alternates on both axes and carries through a 90° turn,
with a possible repeat at the turn cell itself. That is the same class of caveat `WallSurface`
documents for a room's asymmetric patterns, and `strict` is still what a mirrored block pair needs.

Three knobs a room course takes are **load errors** on a corridor, rather than being silently
dropped:

| knob | why |
|---|---|
| `cornerBlock` | corner ownership is a rule about a rectangle's four runs; a corridor wall winds |
| `projection` | the cell it would project into *is* the passage — and on an arched profile it is where the haunch goes |
| `minHeight`/`minSize`/`maxHeight`/`maxSize` | these gate on a room's dimensions, which a corridor has none of |

**A course never fills a doorway's two door-half rows.** Every other row of a door column takes its
course, so a band runs across an opening's sill and lintel rather than stopping dead at each one.

**Expect roughly half of a corridor's courses to be invisible.** A corridor and the room beside it
share one wall column with two faces, and only one block can be in it — measured, about half the wall
faces you see from inside a corridor belong to the room behind them. Courses style the corridor's own
share; the rest follows the neighbouring room's scheme. Nothing in the datapack changes that, and
raising `minRoomGap` to 2 is the only lever that removes it (at ~30% fewer rooms per floor).

The rows between a dropped ceiling and the corridor's full height are **filled solid**, not left
alone — the piece's bounding box covers them either way, and whatever the terrain happened to put
there could be a cave, i.e. a hole in the corridor roof.

**Corner shapes are decided by the generator, not by vanilla.** Normally a stairs block works out
its own `straight`/`inner_*`/`outer_*` corner from its neighbours, and the piece renderer lets it.
That cannot work here: vanilla looks for a neighbouring stair *in the direction the block faces* to
recognise an outer corner, and a haunch faces into its own wall, so it only ever finds solid stone
and every outer corner silently stays `straight` — a visible notch where the wall run ends. The
generator knows the wall layout outright, so it authors the shape, and the renderer leaves any
non-`straight` shape alone.

A haunch only goes in a cell that has a wall on one side and open corridor on the other. That one
condition is what makes narrow corridors degrade correctly — a 1-wide corridor has walls on *both*
sides across its width, so it never arches itself shut; only the ends of the run, where there's a
wall behind and open passage ahead, get a haunch. Inside corners have two candidate directions and
take the lowest of N/S/W/E, deterministically.

Corridors are never dressed by a scheme — a border ring or checkerboard needs a room-sized
rectangle. Corridor *walls* come from the shared `wall` section. Room `base`/`alternateBase` are
rolled per interior cell at 45/55; `classic` sets both to the same block so the floor is uniform
before weathering (the weathering processor list already produces the stone_bricks → cracked/mossy
→ cobblestone → dirt → gravel spread, and pre-baking a second block here both duplicated it and
skipped the deeper decay stages).

### Room schemes

`schemes` is a weighted list of ways to dress a room. **One scheme is rolled per room and supplies
every element's treatment** — today that means the floor; wall, ceiling and pillar slots land as
they get providers behind them. This is orthogonal to the room jigsaw pool above: it decorates a
*procedural* room, not a hand-authored prefab (a prefab is whatever you built it as).

The roll is per room rather than per element on purpose. Independent per-element rolls guarantee
combinations nobody chose — pilasters at an offset that does not line up with the vault they carry,
a formal bordered floor under rough undecorated walls. An architectural style is one choice with
several consequences, so it is made once. The cost is authoring redundancy: two schemes wanting the
same floor border spell it out twice, with no way to reference a shared treatment. That is the same
trade this file already makes by being one-file-per-motif.

| field | meaning |
|---|---|
| `name` | **required.** Unique within the motif; what a log line can identify. |
| `weight` | relative chance among *eligible* schemes (default 1). |
| `minHeight` | skip this scheme in rooms shorter than this (default 0 = always eligible). |
| `minSize` | skip it when the *smaller* of width/depth is below this (default 0). |
| `maxHeight` | skip it in rooms *taller* than this (default: no bound). Inclusive. |
| `maxSize` | skip it when the *smaller* of width/depth is above this (default: no bound). Inclusive. |
| `floor` | optional floor treatment — one pattern entry, described below. |
| `wall` | optional wall treatment — horizontal courses, described below. |
| `ceiling` | optional ceiling treatment — an ordered list of patterns, described below. |
| `pots` | optional loot pots standing on the floor — described below. |

A scheme with nothing but a name is the undecorated room. An absent element slot means "plain for
that element", so `{ "name": "plain", "weight": 8 }` is the whole no-decoration entry.

**Eligibility is filtered before weights are totalled**, so an ineligible scheme's weight leaves the
denominator entirely and the survivors keep their relative proportions in a small room. This matters
more than it looks: room height is a `5 + rand(6)` roll clamped into the footprint's height band
(see `roomHeightBands` in the generation config), so a room has only `height - 2` interior wall
rows — between **3 and 8** under the shipped taper. In a 5-high room rows 1 and 2 are the door
halves and row 3 is the lintel, so there is nowhere to put a crown molding course. A vaulted ceiling
is not a treatment that degrades gracefully in a short room; it is one that must not be rolled there,
and `minHeight` is how you say so. Keep at least one unconstrained scheme in the list — a room
matching none degrades to plain rather than being forced into an ill-fitting scheme.

**`maxHeight`/`maxSize` are the other half of that, and they do something minimums cannot.** A
minimum can push a scheme up the size range but can never confine it to the bottom, so without an
upper bound every modest scheme stays eligible in the largest rooms — and the grand schemes are
permanently a minority of the eligible weight there. Raising a grand scheme's weight does not fix
it, because weight cannot remove a competitor; capping the modest ones is the only lever that makes
a big room reliably feel big. Same argument for detail that does not scale: a `centre` boss of size
1 is a lonely dot in a 15-wide ceiling.

```json
{ "name": "cosy_alcove", "weight": 6, "maxSize": 7 }
```

Both bounds are **inclusive** and measured against the same numbers their minimums are (full height;
the *smaller* of width and depth). Absent means unbounded — there is no "0 means no limit" rule,
because `"maxHeight": 0` would then be indistinguishable from a typo that silently disables the
scheme. A bound below its own minimum is a **load error**, not a clamp: it fits no room at all, and
at generation time that looks exactly like a scheme that merely never won its roll.

> **Upper bounds make it possible to leave a hole.** With minimums only, one unconstrained scheme
> guarantees every room matches something. With bounds, a band of room sizes can fall through every
> scheme at once and silently generate undecorated. `DatapackResourcesParseTest` sweeps every room
> shape the planner can build (odd sides 5–17, height bounded by the footprint's `roomHeightBands`
> entry) and fails if any of them matches nothing.

> Adding any of the four gates to a shipped motif **changes existing seeds**. The roll draws one
> value against the eligible total weight, so gating a scheme out shifts the whole downstream random
> stream for that room.

### The same four gates, on a single element

Any element slot may carry `minHeight`/`minSize`/`maxHeight`/`maxSize` of its own. A slot the room
fails is **dropped**, and the rest of the scheme still draws:

```json
{ "name": "andesite_border", "weight": 5,
  "floor": { "type": "border", "inset": 2, "cornerBlock": "minecraft:andesite",
             "edgeLeftBlock": "minecraft:polished_andesite",
             "edgeRightBlock": "minecraft:polished_andesite" },
  "wall":  { "minHeight": 6,
             "patterns": [ { "type": "courses",
                             "courses": [ { "block": "minecraft:polished_andesite",
                                            "cornerBlock": "minecraft:andesite",
                                            "anchor": "top" } ] } ] } }
```

A 5-high room gets the bordered floor and plain walls; a 6-high room gets both. **That is one scheme
where there used to be two** — `classic` previously shipped `andesite_border` and
`crowned_andesite_border` as separate entries, identical but for the wall slot and a `minHeight`.

The two levels do different things, and the difference is the whole point:

| | Decides | Effect on the roll |
|---|---|---|
| gate on the **scheme** | whether it enters the roll | re-totals every weight |
| gate on an **element** | whether that slot is drawn, after the scheme won | **none** |

An element gate never redistributes weight. A scheme whose wall drops out in half your rooms still
fires at its full weight there — you just get plain walls.

Collapsing a pair of schemes this way also removes an inconsistency the pair had. With two competing
entries, a tall room would sometimes roll the *un*crowned one, so a fraction of tall bordered rooms
came out with no trim for no authored reason. Gated, a tall room gets the crown whenever the scheme
fires — measured, that moved top trim in tall rooms from 75.3% to 79.2% with no weight change.

**A scheme drawing nothing in part of its range is the feature, not a bug.** Shipped `plain` carries
a cornice gated at height 6, so in a 5-high room it deliberately renders an undecorated room — one
scheme doing what `plain` + `plain_6` used to take two to do.

> **The real hazard is dead content:** a slot gated *outside* its own scheme's range — a wall at
> `minHeight: 9` inside a scheme capped at `maxHeight: 7` — loads cleanly, wins rooms, and can never
> render. `DatapackResourcesParseTest` checks per scheme that at least one eligible room shape draws
> something, and names the offender. `SchemeIncidenceTest` also reports a "no decoration drawn"
> percentage, but does not fail on it: barring that number would forbid exactly the legitimate case
> above.

#### Sharing content between schemes (`extends` / `abstract`)

Gates handle variants that differ in **fitness** — the same scheme drawing less in a room too small
for all of it. `extends` handles variants that differ in **content**: the same hall in andesite and
in deepslate, where nothing about the eligibility changes and only the materials do.

```json
{ "name": "grand_hall", "abstract": true,
  "wall": { "patterns": [ { "type": "courses", "courses": [
      { "block": "minecraft:polished_andesite", "anchor": "top" } ] } ] },
  "pots": { "minCount": 1, "maxCount": 2, "lootTable": "dungeons2:pots/classic",
            "variants": [ { "entity": "dungeonblocks:pot", "weight": 1 } ] } },

{ "name": "joisted_hall_stone", "extends": "grand_hall",
  "weight": 12, "minSize": 9, "minHeight": 7,
  "ceiling": { "patterns": [ { "type": "joists", "block": "minecraft:polished_andesite" } ] } }
```

The child gets the parent's wall and pots, adds a ceiling of its own, and states its own gates.

- **`abstract: true` keeps a template out of the roll.** Without it the parent competes as a room in
  its own right, and it cannot be silenced with `weight: 0` — the floor is 1. An abstract scheme is
  dropped whether or not anything extends it. Extending a *concrete* scheme is allowed too; that one
  simply also keeps rolling.
- **A slot the child fills replaces the parent's wholesale.** No merging of the lists inside it — a
  merge cannot express *removing* an inherited entry, and "override with less" is the commoner
  intent. A child wanting the parent's three ceiling patterns plus one more restates all four.
- **`weight`, `minHeight`, `minSize`, `maxHeight` and `maxSize` never inherit.** Partly because a
  primitive cannot distinguish "omitted" from "wrote the default", but mostly because a variant
  exists *because* its eligibility differs — quietly copying a parent's `minSize` is how a whole
  band of room sizes ends up with no scheme at all.
- **The parent may live in a different file**, and that is the point: inheritance is resolved after
  every file in the motif has merged, so an addon that retunes `grand_hall` reaches every child
  without restating any of them.
- **One hop.** A parent may not itself extend. That makes cycles unrepresentable rather than
  something to detect, and keeps reading a scheme to at most two places.

Two mistakes here are **not** load errors, because a parent is addressed across the whole motif and
no single file's codec can see it: naming a parent that does not exist, and extending a scheme that
itself extends. Both **drop the child from the roll** (a scheme half of whose content is missing
would draw a room nobody authored) and log an error naming both ends, once. Extending *yourself* is
visible in one file and does fail the load.

**A single course can be gated too**, which is the case a slot gate cannot reach: a plinth belongs
on every wall in the dungeon, while the crown above it needs headroom a 5-high room does not have.
One `wall` slot, two fates:

```json
"wall": {
  "patterns": [
    { "type": "courses",
      "courses": [
        { "block": "dungeonblocks:left_large_stone_brick",
          "alternateBlock": "dungeonblocks:right_large_stone_brick" },
        { "block": "minecraft:stone_brick_stairs", "anchor": "top", "projection": 1,
          "orient": "toward_wall", "properties": { "half": "top" },
          "minHeight": 6 }
      ] }
  ]
}
```

This is *not* the same as relying on a course being clipped when its row falls outside the wall. A
top course in a 5-high room lands on the **lintel** row and draws perfectly happily — it just looks
cramped. Only a gate expresses that. Every course gating out leaves an empty list, which renders as
a plain wall.

Bounds on an element are validated exactly like a scheme's, and the error names the slot:
`scheme 'x', wall slot: maxHeight 5 is below minHeight 7`. A gate on a nested `generators` entry of
a `composite` floor does nothing — only a top-level slot is gated.

#### Floor treatments

`type` is a plain string, not an enum — `"empty"` (or any unrecognized type) means no special
pattern, just the base blocks; `"border"`, `"checkerboard"`, `"speckle"`, `"cross"`, `"spokes"`
and `"composite"` are described below. An absent `floor` slot **or a pattern whose block ids fail
to resolve** both fall back to plain — there is deliberately no Java-side default block for any
pattern's material slots, so a typo'd id degrades that entry to plain rather than silently
rendering a guessed block. A `weight` inside a `floor` slot is ignored; only the scheme's own
weight is rolled on. The roll happens once per room, using that room's own deterministic seed
(`DungeonRoomPiece#deterministicRandom`) — so it stays the same across the repeated `postProcess`
calls a piece gets per overlapping chunk, exactly like every other per-room roll in this pipeline.

> **Choosing an accent block: mind what weathering does to it.** The test is *not* "does this id
> appear in `worldgen/processor_list/<motif>_weathering.json`" — it is "does this block's decay
> keep the pattern legible". Two distinct ways that fails:
>
> - **Redundant from the start.** The accent is an *output* of the floor's own base block, so
>   weathering would scatter it there anyway and the pattern carries no information. Speckle
>   originally used `minecraft:cracked_stone_bricks` over a `minecraft:stone_bricks` base — which
>   is precisely one of stone brick's own decay products.
> - **Converges with the base over time.** The accent is weathered *away* at a high rate into the
>   generic rubble palette (`cobblestone`/`mossy_cobblestone`/`dirt`/`gravel`) that the base also
>   decays to, so the two blur together and the pattern dissolves.
>   `dungeonblocks:square_stone_brick` (the checkerboard's primary) has three aging chains and only
>   ~32% survives — `0.7 x 0.57 x 0.8`, per the conditional-probability convention documented in
>   the weathering file itself.
>
> An accent with an **in-family** chain is fine, and arguably ideal: one that ages into a
> recognisable variant of *itself* (`chiseled_stone_bricks` → a future
> `mossy_chiseled_stone_bricks`) rather than into shared rubble. The pattern's silhouette survives,
> it just gets older. So a high conversion rate is fine when the destination stays in-family, and a
> generic destination is fine when the rate is low — it is the *combination* that erases a pattern.
>
> `classic` uses `minecraft:chiseled_stone_bricks` for speckle: today it has no chains at all, so a
> speckled floor gains contrast as the stone around it ages, and it becomes the in-family case
> unchanged if a mossy variant is added to `dungeonblocks` later.

#### The `checkerboard` and `speckle` patterns

Two full-floor fills, each taking `primaryBlock` and `secondaryBlock` (both required):

- **`checkerboard`** — alternates the two by `(x + z) % 2`, 1x1 cells, no inset. Setting both to
  the same block makes it invisible, which is a legitimate way to disable it without deleting the
  entry.
- **`speckle`** — fills with `primaryBlock` and sprinkles `secondaryBlock` at `probability` per
  cell (0-1, default 0.05). Unlike every other pattern here its output is *not* a pure function of
  `(x, z)` — it consumes the room's own deterministic random, so it is still stable per room.

Both are full fills rather than overlays, so in a `composite` they belong in the **first** slot.

#### The `composite` pattern

Layers several patterns into one, via an ordered (not weighted) `generators` list of nested
entries:

```json
{
  "type": "composite",
  "generators": [
    { "type": "checkerboard", "primaryBlock": "...", "secondaryBlock": "..." },
    { "type": "border", "inset": 2, "cornerBlock": "...", "edgeLeftBlock": "...", "edgeRightBlock": "..." }
  ]
}
```

The **first** entry is the base full fill; every entry after it is layered on top and only takes
effect if its type is **overlay-capable** (`border`, `cross`, `spokes` — the ones that mark some
cells and leave the rest). A full-fill type in an overlay slot is silently skipped rather than
stomping the base. `weight` on a nested entry is ignored; only the enclosing scheme's weight
matters for the roll. An empty `generators` list degrades to plain.

Ordering is execution order, and later placements win the same cell — the same convention the
`processor_list` files and the wall/floor/ceiling build order already use.

#### The `cross` and `spokes` patterns

Two single-accent-block shapes, both taking `primaryBlock` and both **overlay-capable** (so either
composes over a `checkerboard` base in a `composite`):

- **`cross`** — an accent plus through the room's centre: a band of `thickness` columns at the
  width's midpoint and a band of `thickness` rows at the depth's midpoint. `thickness` defaults to
  1. Always both axes; a single-axis stripe is a different look with no second use yet, so it
  doesn't get a knob until there is one.
- **`spokes`** — `spokes` evenly-spaced lines radiating from the centre to the edges, like a
  compass rose. Defaults to 8 (cardinals plus diagonals); 4 gives just the cardinals, and counts
  that aren't divisors of 4 work fine, just stair-stepped. Arms are rasterised in half-cell steps
  so they're never broken. Note every spoke starts at the centre, so a high count on a small floor
  degenerates into a mostly-accent blob — author the count against the room sizes the motif
  actually generates.

Both fill non-pattern cells with the motif's `floor.base` when used standalone, and emit nothing
there when used as an overlay.

#### The `border` pattern

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

#### Wall patterns (`wall`)

The `wall` slot is an **ordered list of patterns**, exactly like `ceiling` — each drawn on top of
the last, later cells winning. Four types exist: `courses` (horizontal bands), `pilasters` (evenly
spaced vertical strips), `end_pilasters` (a strip at each end of a wall) and `panels` (rectangular
fields).

```json
"wall": {
  "patterns": [
    { "type": "courses", "courses": [ … ] },
    { "type": "panels", "block": "dungeonblocks:stone_bricks_fluted_block", "inset": 1 },
    { "type": "pilasters", "block": "dungeonblocks:stone_bricks_pillar_block" },
    { "type": "end_pilasters", "block": "dungeonblocks:stone_bricks_pillar_block" }
  ]
}
```

**Order is how you say which one wins where they cross.** Listed after the courses, a pilaster
interrupts the bands; listed before, the bands run across it. Both are reasonable looks, so there is
no default — put the one that should win last.

> **Before Aug 2026 this slot was a single typed entry** (`"wall": { "type": "courses", "courses":
> […] }`). That form no longer loads. Wrap what you had in a `patterns` list and it means exactly
> what it used to. Size gates (`minHeight` and friends) stay on the **slot**; each pattern may also
> carry its own.

##### Courses

Horizontal bands across all four walls — plinth, chair rail, string course, crown molding. They are
all the same feature at a different height, which is why one pattern type covers them.

```json
{ "type": "courses",
  "courses": [
    { "block": "minecraft:polished_andesite" },
    { "block": "minecraft:andesite", "anchor": "top", "offset": 1 },
    { "block": "minecraft:chiseled_stone_bricks", "anchor": "top" }
  ] }
```

`block` is required. `anchor` is `"bottom"` (default) or `"top"`, and `offset` (default 0) counts
rows away from it — so `bottom`/0 is a plinth on the lowest wall row and `top`/0 a crown on the
highest. A misspelled anchor **fails to load**; it is not defaulted, because silently reading
`"topp"` as `bottom` would put the crown molding on the floor with no error anywhere.

**`alternateBlock` and `cornerBlock`** are the same two knobs the floor has, and **both default to
`block`** — a course written with `block` alone is a uniform band, exactly as before they existed.

```json
{ "block": "minecraft:stone_bricks",
  "alternateBlock": "dungeonblocks:square_stone_brick",
  "cornerBlock": "minecraft:chiseled_stone_bricks" }
```

`alternateBlock` is mixed into the band per cell at the same 45/55 split the floor's
`base`/`alternateBase` pair gets: a single block reads as a machined stripe, which is right for
polished trim and wrong for a rough stone course.

**`alternate` chooses how they mix** — `"random"` (the default, that 45/55 roll) or `"strict"`,
every other cell starting from `block`. **A mirrored block pair needs `strict`.** A family like
`left_large_stone_brick`/`right_large_stone_brick` is two halves of one wide brick; mixed randomly
you get adjacent left-left runs and the halves stop pairing up, which is a texture bug rather than
variety. Anything that is genuinely two *different* blocks wants `random`.

```json
{ "block": "dungeonblocks:left_large_stone_brick",
  "alternateBlock": "dungeonblocks:right_large_stone_brick",
  "alternate": "strict" }
```

`strict` consumes no randomness, so it renders identically for every seed. Parity is on the run's
own `u`, which restarts at 0 on each of the four walls — so the sequence does not carry around a
corner, and on an odd-length run the seam is visible there. Same consequence of per-run authoring
noted above for asymmetric patterns. `cornerBlock` goes on the room's **four corner
columns** — the quoin a real masonry course has. You do not need to think about which wall owns a
corner; that is handled, including the fact that ownership flips for a projecting course (a cornice
takes its ring corners from the short walls, a flush band from the long ones). `properties` applies
to all three, since they are meant to be one block family.

**Trim can stand out from the wall.** `projection: 1` moves a course off the wall plane into the
interior cell in front of it — a real cornice or moulding rather than a flat band. `orient` then
turns a block that has a `facing` property, per wall, so one authored course comes out correct on
all four:

```json
{ "block": "minecraft:stone_brick_stairs",
  "anchor": "top", "projection": 1, "orient": "toward_wall",
  "properties": { "half": "top" } }
```

- `orient`: `toward_wall` puts a stair's **full-height half against the wall**, stepping down into
  the room — that is a cornice. `toward_room` is the inverse. `none` (default) leaves `facing`
  alone. (A stair's solid half sits on its own `facing` side, so `toward_wall` resolves to the
  *opposite* of the wall's inward facing — which is exactly why this is named by intent rather than
  by direction.)
- `properties`: any other block-state values, applied literally — `half: top` for an upside-down
  stair, or whatever a dedicated trim block needs. Unknown names and unparseable values are ignored
  rather than failing, so a property a block doesn't have costs you nothing.
- Projecting trim is **skipped in front of a doorway** at door height; it would stand in the opening.
- Corners are handled twice over. The projected ring is a complete inset-1 ring with each cell owned
  by exactly one wall, so no corner ever gets two conflicting facings; and after the room is written,
  any block with a `shape` property (stairs, cornices, mouldings) is re-settled against its
  neighbours using Minecraft's own derivation, so the four corners come out **mitred** rather than
  as square notches. Nothing here reimplements that rule, and it works for any mod block that
  follows the same contract.
> **`dungeonblocks` trim faces the opposite way from vanilla.** `toward_wall` / `toward_room` are
> named for where a *vanilla* block's solid side ends up. The `dungeonblocks` cornice, crown moulding
> and sill blocks are modelled inverted, so the same visual result needs the opposite value: a
> cornice of `minecraft:stone_brick_stairs` wants `toward_wall`, while
> `dungeonblocks:*_crown_molding_block` wants `toward_room`. Getting this wrong renders the trim
> inside-out and nothing errors, so the shipped schemes are checked against it by
> `DatapackResourcesParseTest`.

> **A `sill` or `double_sill` block always wants `projection: 1`.** It is a ledge; set flush in the
> wall plane it reads as a recessed panel instead. Also checked against the shipped schemes.

> **Projecting trim at floor level takes the cells pots would stand in — and the pots move.** Pots
> stand on the interior cells that touch a wall, at exactly the height a `bottom`/0 projecting course
> (or any pilaster) occupies. Since Aug 2026 the wall wins: it is emitted first, and the prop pass is
> told which cells it took, so pots are placed elsewhere in the room instead of inside a block. A
> room whose trim leaves too few cells simply gets fewer pots, the same degradation a small room
> already had. You no longer have to keep the two out of one scheme.

**Anchoring from the top is the point.** A wall is `height - 2` rows tall and room height is a
`5 + rand(6)` roll clamped into the footprint's `roomHeightBands` entry — so a course measured from
the floor drifts away from the ceiling as rooms vary, while a top-anchored one stays put.

**Mind the height budget.** That leaves only **3 to 8** wall rows. A course that resolves outside
the wall is silently dropped rather than clamped (a crown squashed onto the plinth row reads worse
than no crown), but relying on that gives you rooms with half a scheme. Use the scheme's `minHeight`
instead: roughly `minHeight: 6` for a plinth alone, `7` for a plinth plus a crown, more if you want
plain wall left between them. The shipped `classic` schemes are authored that way.

Two other things a course cannot do anything about, both handled for you:

- **Doorways win.** The two door-half rows of a doorway cell are always air, whatever the pattern
  says — a solid block there is the lichen-on-doors bug. A pattern is authored in the wall's own
  coordinates and cannot see doors, so the rule is enforced after it.
- **Courses never break at corners.** A band sits at a constant height, so it rings the room
  unbroken regardless of how the corner columns are divided between wall runs.

One unresolvable block id degrades the **whole pattern** to plain wall, not just its own course —
same rule as the floor patterns, and for the same reason: a crown with no plinth under it reads as
a bug, where a plain wall reads as a plain wall. The *other* patterns in the list still draw,
though: two patterns are two authored decisions, and silently dropping the pilasters because a
course names a typo'd block would hide which of them is actually broken.

> The accent-block rule above applies with more force here than on floors. Walls are what a player
> actually looks at — and `classic` renders wall, floor and ceiling from the same
> `minecraft:stone_bricks`, so trim in anything close to that block is invisible.

##### Pilasters

Evenly spaced vertical strips up a wall — engaged columns when they project, panelling when they do
not. The vertical counterpart of a course.

```json
{ "type": "pilasters",
  "block": "dungeonblocks:stone_pillar_block",
  "baseBlock": "dungeonblocks:stone_pillar_base_block",
  "capBlock": "dungeonblocks:stone_pillar_base_block",
  "spacing": 4,
  "projection": 1 }
```

`block` is **required and fails the load if absent** — there is no default material for a pilaster.
`baseBlock` and `capBlock` take the strip's bottom and top rows and both default to `block`, so a
pilaster written with `block` alone is a uniform strip. `projection` and `orient` mean exactly what
they do on a course.

**`baseProperties` and `capProperties` let the plinth and the capital differ**, each falling back to
`properties` when absent. This is the one place the pilaster schema differs from a course, which
shares one `properties` map across its three block slots — and it exists because a capital is
usually the *same block as the plinth, inverted*:

```json
{ "type": "pilasters",
  "block": "dungeonblocks:stone_bricks_pillar_block",
  "baseBlock": "dungeonblocks:stone_bricks_pillar_base_block",
  "capBlock": "dungeonblocks:stone_bricks_pillar_base_block",
  "baseProperties": { "base": "up" },
  "capProperties":  { "base": "down" },
  "projection": 1 }
```

A course's three block slots are one family wanting the *same* state, which is why they share a map;
a column's two ends want opposite ones, which is why these do not.

> **`base` reads the opposite way from what you would guess.** `dungeonblocks` pillar and
> pillar-base blocks carry `base`, and the row sitting on the **floor** wants **`base: up`** while
> the capital under the ceiling wants **`base: down`** — confirmed in game. (`base: up` is the
> unrotated model and `base: down` is it flipped vertically; which end of the column each *looks*
> right on is the part that surprises.) Same family of gotcha as the facing-inverted trim blocks
> noted above: authoring it the intuitive way renders both ends upside down and nothing errors.

**`spacing` (default 4) is a stride, not a count**, and the strips are laid out **symmetric about
each wall's own centre** rather than counted from one end. That matters because the four runs are
not the same length — the long walls span the full `width`, the short ones only `depth - 2` — so
striding from a fixed end would give each wall a different phase and the room would read as an
accident.

**The strips never land on a room corner**, so the gaps stay even the whole way round. A corner
column is a course's `cornerBlock`, not a pilaster. (Which wall *owns* a corner flips depending on
whether the pattern projects, so this is not simply "skip the first and last cell" — it is handled
for you, and it is why a room's four walls agree on spacing at every size.)

**A projecting pilaster in a doorway is dropped whole**, not clipped. Trim at door height would
stand in the opening, but removing just those two cells from a full-height strip leaves a column
floating above the doorway with a gap at the floor — worse than not drawing it. A cornice is
unaffected, because it never occupies those rows in the first place.

Two notes on short rooms: a wall is only 3 rows tall at the low end, so a base plus a cap leaves a
single shaft row between them — gate the scheme's `minHeight` if that reads badly. And unlike a
course, a pilaster cannot be partly drawn: it is full height or absent.

##### End pilasters

`end_pilasters` places **one strip at each end of a wall** rather than a repeating rhythm. Same
blocks and the same `projection` / `orient` / `properties`; it takes `inset` (default 0) instead of
`spacing`.

```json
{ "type": "end_pilasters",
  "block": "dungeonblocks:stone_bricks_pillar_block",
  "baseBlock": "dungeonblocks:stone_bricks_pillar_base_block",
  "projection": 1 }
```

**Listed alongside `pilasters` it gives you the paired corner** — this wall's end strip next to the
perpendicular wall's, reading as a clustered pier, with the even rhythm running between them. That
is the reason the two are separate types rather than one flag: a corner pier is a deliberate
decision, and it should not depend on whether a room's width happened to divide by the spacing.

`inset` moves both end strips in from the ends, symmetrically — `0` puts them as close to the corner
as the wall can reach, `1` one cell along, and so on. On a wall too short for two, you get one; too
short for that, none.

This is the one pattern that *does* stand in a corner column. `pilasters` deliberately never does.

##### Panels

`panels` draws repeating **rectangular fields** — the panel between the pilasters. It is the only
pattern that can stop short of the wall vertically: a course fills a whole row and a strip a whole
column, so a rectangle was not expressible before this.

```json
{ "type": "panels",
  "block": "dungeonblocks:stone_bricks_fluted_block",
  "width": 3,
  "spacing": 4,
  "inset": 1 }
```

`block` is required. `width` (default 3) is the field's width in cells, `spacing` (default 4) the
stride between fields, and **`inset` (default 0) is the number of rows left plain above and below**
— vertical here, unlike on `end_pilasters` where it is horizontal. In both it means "in from the
edge this pattern is measured against". Fields are centred on each wall and never straddle a corner,
the same rules `pilasters` follows. A wall too short to carry the field plus its margins draws
nothing rather than squashing it.

> **`panels` draws the field, not the frame — on purpose.** A recessed panel reads as a field with a
> border, and the border is already expressible: two `courses` for the horizontal edges and
> `pilasters` for the vertical ones, listed either side of the panel in the same slot. Only the
> rectangle needed new geometry.

**Order matters more here than anywhere else.** A field is a solid block of cells, so anything
listed *after* it is drawn over it and anything listed *before* it is erased. Put `panels` early —
under the strips and bands that are supposed to cross it.

> **The block has to actually contrast.** A flush field is texture, not relief, so at `projection: 0`
> it is only visible if it reads differently from the wall — and `classic` draws wall, floor and
> ceiling all from `minecraft:stone_bricks`. A panel in anything close to that block is invisible.
> Pick a genuinely different block, or give the field a projection.

#### Ceiling patterns (`ceiling`)

Treatments on the ceiling. Like the wall slot (and unlike the floor) this is an **ordered list**,
applied in sequence with later patterns drawn on top:

```json
"ceiling": {
  "patterns": [
    { "type": "coffers", "block": "minecraft:polished_andesite", "spacing": 3, "projection": 1 },
    { "type": "border", "block": "minecraft:andesite", "cornerBlock": "minecraft:chiseled_stone_bricks" },
    { "type": "centre", "block": "minecraft:chiseled_stone_bricks", "size": 1 }
  ]
}
```

| `type` | shape | knobs |
|---|---|---|
| `coffers` | a lattice of ribs dividing the ceiling into panels | `spacing` (3) |
| `border` | a ring following the walls, reading as a soffit | `inset` (0), `cornerBlock` |
| `centre` | a square boss at the middle (`center` also accepted) | `size` (1) |
| `joists` | parallel beams (rafters) crossing the room one way | `spacing` (3), `bracketBlock`, `orient` |

**`joists` is the one-directional counterpart to `coffers`.** A lattice reads as formal masonry; a
run of parallel beams reads as the floor above you. Four things about it are not obvious:

- **The beams span the room's *shorter* side**, and the `spacing` rhythm steps along its longer one
  — the opposite of the `colonnade` pillar layout, which runs *along* the length. A square room
  always runs east–west, deterministically; unlike `colonnade` this never declines a room.
- **A beam block that has an `axis` is laid along the run automatically.** Do not author `axis` — the
  run direction comes from the room's proportions, so any authored value is wrong in every room
  shaped the other way. A beam with no `axis` (any plain cube) is placed unchanged, which is what
  lets the same entry carry a stone beam and a timber one.
- **`bracketBlock` is optional and is any block, and it hangs *under* the beam.** A bracket carries
  its beam from below — one sitting in the beam's own row is not supporting it, it is interrupting
  it. So a bracketed entry occupies **two rows**: beams at `projection`, brackets at `projection + 1`,
  with the beam running unbroken wall to wall above them. `dungeonblocks`' corbels are the obvious
  choice, stairs are an equally good one, and no bracket at all is the default.
  **Mind the headroom** — give a bracketed entry a `minHeight` one greater than a bare one needs.
- **`orient` turns the bracket, not the beams.** `outward` points it at the wall its end rests on,
  `inward` into the room; one authored value comes out turned correctly at both ends. A `dungeonblocks`
  corbel wants **`inward`** — its post sits on the far face and the arm cantilevers away from it, so
  the block faces off its wall. `orient` with no `bracketBlock` is a **load error**, since it would
  otherwise be a line that does nothing.

The beams are **not assumed to be timber.** Stone and stone brick beams are equally legitimate, and
they are the ones that weather today: `classic_weathering.json` has 110 rules and none of them touch
wood, so a timber beam ships un-aged among cracked stone until that is authored.

Every type also takes **`projection`** (default 0), which hangs the treatment below the ceiling
instead of drawing it flush in it — the same knob, the same meaning and the same cap as a wall
course's.

**This is the difference between a coffered ceiling and a coffered pattern**, and it is the reason
`projection` exists here. Ribs drawn flush are in the same plane as the panels they are supposed to
divide, so the lattice reads as texture painted on a flat ceiling. `"projection": 1` hangs them one
cell down and the panels become genuinely recessed — an actual coffer. Like a wall's, a projecting
treatment is *absent from the plane*, so the ceiling behind it stays the plain ceiling block, which
is exactly what a recessed panel is.

Two things to know before using it:

- **A projecting ceiling wants a `minHeight`.** The rib eats a row of headroom, and in a 5-high room
  that leaves two. `classic` gates its projecting coffers at 7 and keeps a flush variant
  (`coffered_ceiling_flat`) for shorter rooms so ceiling decoration does not vanish from them.
- **Where it meets projecting wall trim, the ceiling wins.** The ring of ceiling cells against the
  walls is also where a projecting crown moulding hangs, so a scheme carrying both writes twice to
  the cells where a rib meets that ring — and the ceiling is emitted last, so the rib takes them.
  That is the intended look: the rib runs into the cornice and interrupts it, the way coffering
  meets a cornice in a real ceiling. The cornice survives everywhere no rib lands. Ribs always run
  the full width of the room; they never stop short of the wall.

`block` is required by every type — there is no Java-side default for a pattern's material, so an
absent or unregistered id skips that pattern rather than substituting a guess. `cornerBlock` is the
one exception and not really one: absent, it falls back to `block`, which is another *authored*
value rather than a guessed block.

**There is no `composite` type here, and there will not be one.** Ceiling and wall patterns are
*sparse* — a pattern leaves cells it does not care about untouched — so layering is just listing
them in order. That is why the floor needs a `composite` wrapper and this does not: floor generators
fill every cell, so layering them required a separate mechanism.

Put the broad fill first and the accents after. A `centre` boss listed after `coffers` replaces the
middle rib cell; listed before, the lattice would draw straight over it.

Two differences from wall patterns worth knowing:

- **The ceiling covers the interior only** (1 inset from the walls), so a pattern's extent is
  `width-2` x `depth-2`, not the room footprint. Both `coffers` and `centre` are centred on that
  interior, so they line up with the room's axes.
- **One bad block id drops only its own pattern**, not the whole list — unlike a wall `courses`
  entry, which degrades entirely. A ceiling list is several independent patterns, so a typo in the
  boss should not silently strip the coffers with it.

A flush ceiling pattern needs no minimum height (it sits on one plane), but `minSize` is worth
setting: `coffers` needs roughly a 7x7 room before the lattice reads as panels rather than as noise.
A projecting one needs both.

#### Authoring for incidence, not for weight

A room rolls **one** scheme for all of its elements, so making one element common necessarily makes
the others rarer — raising wall-trim weights until trim was common would have squeezed floor
patterns out of the dungeon. The way out is to author *combined* schemes: `classic` ships crowned
variants of its floor, ceiling and pots schemes (`crowned_andesite_border`, `crowned_ceiling_boss`,
`crowned_pots`, ...) beside the plain ones, gated at `minHeight` so short rooms still get the plain
version. That is what lets **two thirds of rooms over 5 high** carry top trim while floor patterns
stay at ~35% and ceilings at ~18%.

`SchemeIncidenceTest` measures all of this against real planner output and fails the build if trim
stops being common; it prints the full per-scheme breakdown, so retune against its numbers rather
than against the weights on paper. The two are very different — see the gate warning above.

#### Loot pots (`pots`)

Scatters `dungeonblocks` pots across the room's floor, each carrying a loot table.

```json
"pots": {
  "minCount": 1,
  "maxCount": 3,
  "lootTable": "dungeons2:pots/classic",
  "variants": [
    { "entity": "dungeonblocks:pot", "weight": 2 },
    { "entity": "dungeonblocks:squat_clay_pot", "weight": 2 },
    { "entity": "dungeonblocks:thin_clay_pot", "weight": 1 },
    { "entity": "dungeonblocks:stone_pot", "weight": 2 },
    { "entity": "dungeonblocks:squat_stone_pot", "weight": 2 },
    { "entity": "dungeonblocks:thin_stone_pot", "weight": 1 }
  ]
}
```

**Shape and palette are separate axes.** `dungeonblocks` ships each of the three shapes — tall
(`pot`), squat (`squat_*`) and thin (`thin_*`) — in four palettes, and the id is the two combined:

| Palette | Ids | Used by |
|---|---|---|
| terracotta | `pot`, `squat_clay_pot`, `thin_clay_pot` | procedurally generated rooms |
| grey / stone | `stone_pot`, `squat_stone_pot`, `thin_stone_pot` | procedurally generated rooms |
| red | `red_pot`, `squat_red_pot`, `thin_red_pot` | **template rooms only** |
| blue | `blue_pot`, `squat_blue_pot`, `thin_blue_pot` | **template rooms only** |

Red and blue are **deliberately kept out of the scheme `variants` lists**: they are reserved as a
hand-placed signal in template (prefab) rooms, and a scheme that rolls them procedurally would make
that signal meaningless. Add grey freely; do not add red or blue to a motif's schemes.

`lootTable` and `variants` are **required**; `minCount`/`maxCount` default to 1 and 3. A count is
rolled per room from that inclusive range, then that many distinct cells are drawn — a room with
fewer eligible cells than the rolled count just gets fewer pots.

**Pots are entities, not blocks**, and that has consequences worth knowing before authoring:

- **`lootTable` is required for a reason.** `PotEntity` drops nothing at all when its table id is
  null or `minecraft:empty`, and it does **not** fall back to the entity type's own table — the
  ones `dungeonblocks` ships for its pot types are empty stubs with no pools. A missing or
  typo'd id is a pot that shatters into thin air with no error anywhere. The id must resolve to a
  file this mod ships; `DatapackResourcesParseTest` fails the build if it doesn't.
- **The table must be `"type": "minecraft:entity"`.** The drop path builds its `LootParams` with the
  ENTITY parameter set. A chest-style (`minecraft:chest`) table will not work.
- **Each pot gets a non-zero `LootTableSeed`**, so its contents are fixed when the dungeon generates
  rather than rolled when a player breaks it — the same treatment vanilla gives a structure chest.
- **A creative-mode player gets no drops.** There is an explicit early return for it. Easy to
  mistake for broken loot when testing.

Placement is not configurable and is deliberately narrow: pots go on **interior floor cells that
touch a wall**, never on the cell immediately inside a doorway. Pots have gravity and a fall-break
distance, so one placed over anything but solid floor falls and shatters before a player ever sees
it; and a pot alone in the middle of an open floor reads as dropped rather than placed. Use
`minSize` on the scheme to keep pot schemes out of rooms too cramped to hold them.

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

> **A misspelled block id does not fail this file — it becomes `minecraft:air`.** The block registry
> is *defaulted*, so an unknown id decodes cleanly to the default value instead of erroring: a
> misspelled decay target produces a rule that never fires, and a misspelled palette entry grows air.
> Two things now catch it. GottschCore logs a warning naming the id at load, and
> `ShippedBlockIdsTest` fails the build for any bad id anywhere in this mod's shipped data —
> including `dungeonblocks:` ids, which it checks against the blockstate files in that mod's jar,
> because a headless registry cannot answer for them.
>
> If you add a field that holds a block id, add its key to that test's `BLOCK_KEYS`. It fails on any
> namespaced value under a key it does not recognise, precisely so the sweep cannot quietly stop
> covering new fields.

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
  "cobwebs":        { "probability": 0.02, "blocks": ["minecraft:cobweb"] },
  "corner_cobwebs": { "probability": 0.06, "blocks": ["dungeonblocks:angle_cobweb_1",
                                                      "dungeonblocks:angle_cobweb_2"] },
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
| `corner_cobwebs` | air with a **full-cube wall AND a full-cube floor or ceiling** — the junction between the two | that cell, angled into the junction |
| `wall_growth` | air beside a solid block | that cell, attached to the wall's face |
| `floor_growth` | a `dirt` block with air above | the cell above |
| `hanging_growth` | a `dirt` block with air below | the cell below |
| `underwater_growth` | water with a **solid floor** under it | that cell (replacing the water) |
| `floating_growth` | water with air above | the cell above |
| `unsupported` | a matching block whose **wall behind it** has become air | clears it to air |

Every behaviour takes `probability` (absolute — one roll per candidate position, *not*
conditional like the rule/aging chains) and `blocks` (a palette, picked from uniformly).

**`corner_cobwebs` is a separate behaviour from `cobwebs`, and deliberately so.** A
`minecraft:cobweb` fills its cell and strings itself across any gap, so "something solid beside it"
is the whole requirement. An angle web is modelled as a triangular sheet that **gathers into the
angle where two surfaces meet** and tapers away from it — put one halfway up a bare wall and it has
nothing to gather into, and reads as a sheet hanging in mid-air. It is a different question, not a
different palette entry, which is also why the two carry independent probabilities. Three things
follow:

- **Both surfaces must be full cubes**, not merely solid — same reason `wall_growth` needs one. A
  stair or a facade passes `isSolid` but presents no face to gather against.
- **The block must be able to express the orientation.** The processor sets `facing` from the
  neighbours, compensating for the piece's own rotation and mirroring. A block with no such
  property — `minecraft:cobweb` — draws nothing here rather than being placed unoriented. Nothing
  in the processor names a block.
- **The junction is chosen with `half`, not with `facing`.** Both junctions use the *same*
  horizontal facing so the web's sheet stands against the wall; `half=top` gathers it at the
  ceiling and `half=bottom` at the floor, and the two are different models. This is not a stylistic
  choice — a vertical facing tips the sheet flat onto the floor, and the vertical mirror that would
  be wanted instead cannot be expressed as a blockstate rotation at all. **A web with no `half`
  (e.g. a ceiling-only model) is used at ceiling junctions and skipped at floor-only ones** rather
  than placed wrong.
- **Use a higher probability than `cobwebs`.** This fires on far fewer cells (only wall/floor and
  wall/ceiling junctions), so the same rate would be nearly invisible; `classic` runs 0.06 against
  the plain web's 0.02.

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

## DATA structure-block markers — **DO NOT USE ANY OF THESE**

> **A DATA structure block cannot work in a Dungeons2 template, and it fails destructively.**
> Established 2026-08-14, the expensive way. Every authored piece is placed as a **jigsaw pool
> element**, and `SinglePoolElement.getSettings` installs `BlockIgnoreProcessor.STRUCTURE_BLOCK`
> *before* appending the pool's own `processors`. That processor returns `null` for a structure
> block, which **removes it from the placement list** rather than replacing it. Nothing then writes
> the cell — so you do not get a marker that quietly fails, you get **whatever terrain the dungeon
> was carved out of**, sitting inside the finished room. The reported case was a coal ore block in
> the middle of a hall.
>
> `ShippedSpawnerMarkerTest` fails the build if any shipped template contains a structure block at
> all, and `JigsawStripsStructureBlocksTest` pins the vanilla mechanism so a future version change
> is visible.

The table below is **historical**. None of these five was ever implemented; `d2:spawner` briefly
was, and had to be redone as a marker **block** — see below.

| Marker string | Was meant to | Status |
|---------------|--------------|--------|
| `d2:spawner`  | become a mob spawner | **superseded** by the `dungeons2:spawner_marker` block |
| `d2:descend`  | entrance drop / lower-floor connection column | never built; superseded by the jigsaw `dungeons2:door` / `dungeons2:connector` markers |
| `d2:ascend`   | upper-floor connection column | never built; same |
| `d2:chest`    | become a loot chest | never built |
| `d2:anchor`   | origin override | never built |

> The old `d2:door` DATA marker is **removed** — doors are jigsaw blocks (role 1), not DATA blocks.

**If you need a new marker, use a block.** Register one, add it to the pool's processor list with a
processor that swaps it, and give it a `BlockItem` so it can be placed by hand. That is what
`dungeons2:spawner_marker` does, and it is the only shape that survives jigsaw placement.

## The mob-set spawner (backlog #10, 2026-08-14)

**Place a `dungeons2:spawner_marker` block on a floor cell.** It is a normal solid block that looks
like a vanilla spawner, it has an item (`/give @s dungeons2:spawner_marker`, or find it in the
Functional Blocks creative tab), and it is visible while you author — so you can see what you put
where. At placement the `dungeons2:spawner` processor swaps it for `dungeons2:mob_set_spawner`: an
**invisible, non-solid, non-collidable** block whose block entity spawns a datapack-defined set of
mobs when a player comes within `proximity` blocks (8 by default, roughly "as you enter the room" at
Dungeons2's room sizes).

Shipped example: `rooms/classic/15x21_hall_1.nbt` carries one at local `(7, 1, 10)` — centre of the
hall's aisle, on the walking plane.

**Which mobs is datapack content, not code.** The set is named by the `dungeons2:spawner` entry in
the motif's processor list (`worldgen/processor_list/classic_weathering.json`), and the shipped one
is `dungeons2:classic_vermin` — the dungeon's own rats.

**One marker means one set per motif.** A block carries no free text, so there is no per-cell
override. A motif wanting a second set registers a second marker block and adds a second processor
entry pointing `marker_block` at it; that half is pure data.

Sets live at `data/<namespace>/mob_sets/*.json` and are read by GottschCore's `MobSetDataHandler`,
so a datapack can add or replace them without touching the mod:

```json
{
  "id": "dungeons2:classic_vermin",
  "category": "classic",
  "count": { "min": 2, "max": 4 },
  "mobs": [
    { "id": "dungeons2:rat", "weight": 10 },
    { "id": "dungeons2:giant_rat", "weight": 3 }
  ]
}
```

`minecraft:empty` is a valid weighted entry and means "spawn nothing this roll".

> **Why a marker block rather than a DATA marker.** Vanilla's `handleDataMarker` hook belongs to
> `StructurePiece`, and every authored Dungeons2 piece is a `PoolElementStructurePiece` this mod
> does not subclass — so a **structure processor** is the available mechanism, and all four pools
> already name a processor list. But a processor never sees a structure block in a pool element
> (see the warning at the top of this section), so the marker has to be an ordinary block. Village
> Dungeons uses marker blocks for exactly this reason.
>
> Consequently the marker only works in **authored templates**. A procedurally-built room places no
> marker and reaches the same spawner through the motif config's **`spawners` scheme slot** instead
> (`RoomSpawnerGenerator` → `BlockEntityData` → `DungeonPiece.applyBlockEntity`) — see
> §spawners of the Room Schemes manual. The two paths build the same block-entity tag by two
> different encodings, so a field renamed on one side must be renamed on the other;
> `SpawnerTagParityTest` fails the build if they drift.

> **`ShippedMobSetsTest` is what catches a typo.** The processor deliberately does not validate that
> a set exists — `MobSetDataRegistry` fills at datapack reload while a processor runs during
> worldgen, so "not loaded yet" and "does not exist" are indistinguishable there. A misspelled set
> is a build failure instead of a silent no-op spawner.
