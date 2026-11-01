# Parked scheme files

**This folder is deliberately outside `data/`, so nothing here is loaded.** The `dungeons2:motif_config`
registry only reads `data/<namespace>/dungeons2/motif_config/...`; a file here is packaged into the
jar but never seen by the datapack loader.

## Why

`classic` is cut down so that a scheme under development fires often enough to find. With the full
set a new scheme lands in ~1.85% of rooms; with only `plain` competing it lands in **12.4%**
(~8.5 per MEDIUM dungeon). Mark's deliberate working configuration for scheme authoring
(2026-08-05). It must not ship.

`base.json` now holds **six** schemes (all 2026-08-06 unless noted): `plain`, `vaulted_hall`,
`pilastered_hall`, `hypostyle_hall`, `colonnaded_hall` and `quartet_hall`.

**Each new scheme in the same size band dilutes the others.** The four grand ones all gate at
`minSize: 9`, so they split one pool four ways — currently 185/202/191/188 rooms of 3459, i.e. ~5.4%
each, down from 12.4% when `vaulted_hall` had `plain` to itself. Ceiling decoration fell 9.5% → 5.3%
and pots 8.7% → 5.8% purely from this. **Weight cannot fix it**: raising a weight does not remove a
competitor.

`quartet_hall` is the counter-example worth copying: gated `minSize: 7, maxSize: 7`, a band none of
the others occupy, it took 506 rooms (14.6%) from `plain` and changed the other four by *nothing*.
If you are adding a scheme for authoring visibility, giving it its own band beats raising its weight.

> **A gated scheme's share moves when the size distribution moves — measured 2026-08-10.**
> `quartet_hall` is now **609 rooms (21.2%)**, up from that 14.6%, with nothing about it changed.
> Backlog #35 stopped the planner building 5×5 joiner rooms, which grew the 7-wide band from 38% to
> 53% of the dungeon *and* left `quartet_hall` as one of only **two** schemes eligible there. It
> takes 40.3% of that band, because at min side 7 the roll is `plain` (12) against `quartet_hall`
> (12) and nothing else. Owning a band cuts both ways: it insulates a scheme from new competitors,
> and it hands the scheme the whole band when the band grows. **Re-measure a gated scheme after any
> change to room sizes.** (Left at weight 12 deliberately — Mark, 2026-08-10: the schemes are not
> fleshed out yet and high visibility is wanted during authoring.)

## There is no required set of files

**A motif is a folder, and the folder's contents are entirely up to you** — any number of files,
named anything. `MotifConfigHelper` takes every registry entry whose path starts with `classic/`,
sorts them by resource location, and folds them in that order. The flat `motif_config/classic.json`
form still works and layers underneath the folder.

The split into `schemes_walls` / `schemes_floors` / `schemes_ceilings` / `schemes_props` is one
person's filing, not a contract. Merge them all into `base.json`, split them per scheme, or rename
them — nothing reads those names.

Two merge rules are all there is:

- **Element sections** (`wall`, `ceiling`, `door`, `corridor`, `floor`) are whole-section: the last
  file that authors one wins it outright.
- **Schemes concatenate, and a later scheme with a name already used replaces it in place.** That is
  the override mechanism: a file sorting after `base.json` can retune `plain`'s weight without
  restating the list, and keeping the original position means an override cannot quietly reorder
  the rest.

## Restoring

Move whatever you want back under
`src/main/resources/data/dungeons2/dungeons2/motif_config/classic/`, in whatever arrangement suits
you, then re-enable the two guards that assert on the shipped scheme list:

- `SchemeIncidenceTest#shippedTrimIsFindableInAnOrdinaryDungeon`
- `DatapackResourcesParseTest#classicShipsItsFullSchemeList`

`vaulted_hall` currently exists in both `base.json` and `classic/schemes_vaults.json`. That is
**not** a conflict — same name, so whichever sorts later wins and there is exactly one scheme either
way. Delete one only if having it twice is confusing to read.

**Four schemes live only in `base.json` and have no parked counterpart** — `pilastered_hall`,
`hypostyle_hall`, `colonnaded_hall` and `quartet_hall`. A restore that just moves these files back
keeps them; a restore that rewrites `base.json` from scratch would drop all four. `pilastered_hall`
is also the only one carrying `pots`, which is what keeps `SchemeIncidenceTest`'s pots bar off zero
today.

**Re-measure after restoring.** The full set plus six is a different roll from either half, and the
manual's headline figures (top trim 79%, floor patterns 41%) were measured against the full set
*before* any of the pillar or vault schemes existed. Expect them to move.

**These files were migrated to the new `wall` slot shape on 2026-08-06** (`"wall": {"patterns":
[{"type": "courses", …}]}` rather than `"wall": {"type": "courses", …}`). Nothing here is loaded, so
no test would have caught it if they had been missed — and the old shape now fails to load rather
than degrading quietly, so a stale file would break the restore loudly. They are current; just
move them.

**Do not lower those tests' bars to make them pass.** The numbers were authored against measured
planner output after trim once shipped at ~17% by weight and proved nearly unfindable in game.

## A lighter alternative to moving files

Because a later scheme replaces an earlier one *by name*, you can isolate a scheme without moving
anything: add one file that sorts last (e.g. `zz_authoring.json`) restating the other schemes at
`weight: 1` and the one under test at `weight: 100`. Delete that one file when you are done. Weights
have a floor of 1, so this cannot silence a scheme completely — but it gets the one you care about
to a large majority of eligible rooms, and it leaves the shipped files untouched.
