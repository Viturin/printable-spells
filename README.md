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

## Local Hosting (Docker Compose)

Build and run backend + frontend:

```bash
docker compose up --build -d
```

Open:
- `http://localhost`

Optional local DNS name:
1. Add `127.0.0.1 spellcards.local` to `/etc/hosts` (or your LAN DNS).
2. Open `http://spellcards.local`.

Stop:

```bash
docker compose down
```

## Staging + Prod (Same Host)

Run both environments on one machine with different compose project names.

Prod (port `80`):

```bash
docker compose -p spellcards-prod -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

Staging (port `8081`):

```bash
docker compose -p spellcards-staging -f docker-compose.yml -f docker-compose.staging.yml up --build -d
```

Useful commands:

```bash
# Logs
docker compose -p spellcards-prod -f docker-compose.yml -f docker-compose.prod.yml logs -f
docker compose -p spellcards-staging -f docker-compose.yml -f docker-compose.staging.yml logs -f

# Stop
docker compose -p spellcards-prod -f docker-compose.yml -f docker-compose.prod.yml down
docker compose -p spellcards-staging -f docker-compose.yml -f docker-compose.staging.yml down
```

## CI Deploy Flow

- Workflow: `.github/workflows/deploy.yml`
- Trigger: push to `main` (and manual run).
- Runner: self-hosted Linux runner.
- Flow:
  1. Deploy staging (`spellcards-staging`)
  2. Deploy prod (`spellcards-prod`)

To require manual approval before prod:
1. GitHub repository `Settings` -> `Environments` -> `production`
2. Add required reviewers
3. Keep workflow environment name as `production`

## What This Tool Does

- Fetches PF2e spell data from Foundry release asset `json-assets.zip` (`packs/spells.json`) and stores generated resources in `spellcards-app/src/generated/resources/foundry/`.
- Uses a pinned Foundry release tag from `spellcards-app/pom.xml` (`foundry.version`).
- Skips download when generated data already matches the pinned version; `mvn -Pfetch-foundry-data clean` forces a refresh.
- Loads and parses spells in the backend.
- Exposes fuzzy search via REST: `GET /api/v1/spells/search`.
- Provides a frontend for live spell search and card-oriented output.
