# Printable Spells

Backend prototype for loading PF2e spells from Foundry data and exposing parsed spell domain objects.

## Tech

- Java 25
- Spring Boot 4
- Maven
- Immutables

## Data fetch

Fetch generated spell resources:

```bash
./scripts/fetch_foundry_spells.sh <version-tag-or-branch>
```

Example:

```bash
./scripts/fetch_foundry_spells.sh v13-dev
```

What the script does:
1. Uses/refreshes a cached Foundry clone at `.cache/foundry/pf2e-<version>` (default TTL: 7 days, configurable via `CACHE_TTL_DAYS`).
2. Copies JSON files recursively from:
   - `packs/pf2e/spells/spells`
   - `packs/pf2e/spells/focus`
   - `packs/pf2e/spells/rituals`
   (excluding `_folders.json`).
3. Writes generated resources to:
   - `src/generated/resources/foundry/spells/*.json`
   - `src/generated/resources/foundry/spells/index.txt`
   - `src/generated/resources/foundry/version.txt`

## Build / run

```bash
mvn compile
mvn spring-boot:run -Dspring-boot.run.arguments="Daze"
```

## Tests

```bash
mvn test
```

Important test coverage:
- Parser unit tests for valid/invalid payloads and kind inference.
- Repository tests for lookup behavior.
- Generated data parse test that iterates all files in `src/generated/resources/foundry/spells/index.txt` as parameterized tests (isolated per file).

## Package layout

- Inbound adapters (driving): `io.github.viturin.spellcards.adapters.in.cli`
- Outbound adapters (driven): `io.github.viturin.spellcards.adapters.out.foundry`
- Domain port: `io.github.viturin.spellcards.domain.port.SpellRepository`
- Application service: `io.github.viturin.spellcards.application.service.GenerateSpellCardsService`
