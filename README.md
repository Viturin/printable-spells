# Printable Spells

PF2e spell cards project with:
- Spring Boot backend (`spellcards-app`)
- OpenAPI contract module (`spellcards-api`)
- React/Vite frontend (`spellcards-web`)

## Modules

- `spellcards-api`: OpenAPI spec + generated API contract artifact.
- `spellcards-app`: Spring Boot application implementation.
- `spellcards-web`: React + TypeScript + Vite frontend.

OpenAPI spec location: `spellcards-api/openapi/openapi.yaml`.

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
   - `spellcards-app/src/generated/resources/foundry/spells/*.json`
   - `spellcards-app/src/generated/resources/foundry/spells/index.txt`
   - `spellcards-app/src/generated/resources/foundry/version.txt`

## Build / run

```bash
mvn compile
mvn -pl spellcards-app -am spring-boot:run
```

Frontend (from `spellcards-web`):

```bash
npm install
npm run dev
```

Frontend via Maven (dev server):

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run dev"
```

Generate TypeScript API client from OpenAPI spec:

```bash
npm run generate:client
```

Frontend via Maven:

```bash
mvn -pl spellcards-web verify
```

This runs:
1. `frontend-maven-plugin:install-node-and-npm`
2. `npm install`
3. `npm run generate:client`
4. `npm run build`

For reproducible CI builds, commit `spellcards-web/package-lock.json` and switch Maven step back to `npm ci`.

Only regenerate the TS client via Maven:

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run generate:client"
```

## Tests

```bash
mvn test
```

Important test coverage:
- Parser unit tests for valid/invalid payloads and kind inference.
- Repository tests for lookup behavior.
- Generated data parse IT that iterates all files in `spellcards-app/src/generated/resources/foundry/spells/index.txt` as parameterized tests (isolated per file).

## Package layout

- Inbound adapters (driving): `io.github.viturin.spellcards.adapters.in.cli`
- Inbound REST adapter: `io.github.viturin.spellcards.adapters.in.rest`
- Outbound adapters (driven): `io.github.viturin.spellcards.adapters.out.foundry`
- Domain port: `io.github.viturin.spellcards.domain.port.SpellRepository`
- Application ports: `io.github.viturin.spellcards.application.port.in.SpellCardGenerationService`, `io.github.viturin.spellcards.application.port.in.SpellSearchService`
- Application services: `io.github.viturin.spellcards.application.service.GenerateSpellCardsService`, `io.github.viturin.spellcards.application.service.SearchSpellsService`
