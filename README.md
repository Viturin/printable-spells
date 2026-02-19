# Foundry Spell Data Fetch

This repository currently documents only the Foundry spell data fetch step.

## Fetch script

```bash
./scripts/fetch_foundry_spells.sh <version-tag-or-branch>
```

Example:

```bash
./scripts/fetch_foundry_spells.sh v13-dev
```

What it does:
1. Uses a cached clone at `.cache/foundry/pf2e-<version>` (refreshes after 7 days by default, configurable via `CACHE_TTL_DAYS`).
2. Copies spell JSON files recursively from:
   - `packs/pf2e/spells/spells`
   - `packs/pf2e/spells/focus`
   - `packs/pf2e/spells/rituals`
   (excluding `_folders.json`).
3. Writes generated files to:
   - `src/generated/resources/foundry/spells/*.json`
   - `src/generated/resources/foundry/spells/index.txt`
   - `src/generated/resources/foundry/version.txt`
