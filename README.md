# Printable Spells

## IntelliJ

Shared run configurations are in `.run/`:
- `init-setup` (bootstrap data + generated clients)
- `spellcards-app` (Spring Boot backend)
- `spellcards-web-dev` (Vite dev server)
- `fullstack-dev` (starts both)

## Maven

Common commands:

```bash
mvn compile
mvn -pl spellcards-app -am spring-boot:run
mvn -pl spellcards-web verify
mvn test
```

Frontend dev via Maven:

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run dev"
```

Regenerate frontend OpenAPI client:

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run generate:client"
```

## What This Tool Does

- Fetches PF2e spell JSON data from Foundry and stores generated resources in `spellcards-app/src/generated/resources/foundry/`.
- Loads and parses spells in the backend.
- Exposes fuzzy search via REST: `GET /api/v1/spells/search`.
- Provides a frontend for live spell search and card-oriented output.
