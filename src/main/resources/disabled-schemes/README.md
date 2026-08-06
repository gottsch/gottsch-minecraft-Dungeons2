# Parked scheme files

**This folder is deliberately outside `data/`, so nothing here is loaded.** The `dungeons2:motif_config`
registry only reads `data/<namespace>/dungeons2/motif_config/...`; a file here is packaged into the
jar but never seen by the datapack loader.

## Why

`classic` is cut down to **two schemes** so that a scheme under development fires often enough to
find. With the full set a new scheme lands in ~1.85% of rooms; with only `plain` competing it lands
in **12.4%** (~8.5 per MEDIUM dungeon).

**This is the deliberate working configuration for scheme authoring**, not a leftover — Mark's call
on 2026-08-05 once `vaulted_hall` was verified in game. It still must not ship.

`base.json` currently holds both `plain` and `vaulted_hall`. The copy of the vault in
`classic/schemes_vaults.json` here is the one that should survive the restore.

## Restoring

1. Move `classic/schemes_*.json` back to
   `src/main/resources/data/dungeons2/dungeons2/motif_config/classic/`.
2. Delete the `vaulted_hall` scheme from `base.json` — `schemes_vaults.json` carries it, and two
   schemes with the same name in one motif is not something the loader checks for.
3. Remove the `@Disabled` from both of these, which guard the shipped content and fail loudly while
   it is cut down:
   - `SchemeIncidenceTest#shippedTrimIsFindableInAnOrdinaryDungeon`
   - `DatapackResourcesParseTest#classicShipsItsFullSchemeList`
4. `./gradlew test` — expect 407 passing, and `vaulted_hall` back at ~1.85%.

**Do not "fix" those two tests by lowering their bars.** They exist because wall and ceiling trim
once shipped at ~17% by weight and was nearly unfindable in game; the numbers in them were authored
against measured planner output, not chosen.
