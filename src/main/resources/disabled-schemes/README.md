# Parked scheme files

**This folder is deliberately outside `data/`, so nothing here is loaded.** The `dungeons2:motif_config`
registry only reads `data/<namespace>/dungeons2/motif_config/...`; a file here is packaged into the
jar but never seen by the datapack loader.

## Why

`classic` is cut down so that a scheme under development fires often enough to find. With the full
set a new scheme lands in ~1.85% of rooms; with only `plain` competing it lands in **12.4%**
(~8.5 per MEDIUM dungeon). Mark's deliberate working configuration for scheme authoring
(2026-08-05). It must not ship.

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

**Do not lower those tests' bars to make them pass.** The numbers were authored against measured
planner output after trim once shipped at ~17% by weight and proved nearly unfindable in game.

## A lighter alternative to moving files

Because a later scheme replaces an earlier one *by name*, you can isolate a scheme without moving
anything: add one file that sorts last (e.g. `zz_authoring.json`) restating the other schemes at
`weight: 1` and the one under test at `weight: 100`. Delete that one file when you are done. Weights
have a floor of 1, so this cannot silence a scheme completely — but it gets the one you care about
to a large majority of eligible rooms, and it leaves the shipped files untouched.
