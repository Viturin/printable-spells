# Printable Spells

## IntelliJ

Shared run configurations are in the workspace root under `.idea/runConfigurations/`:
- `bootstrap-spellcards` (bootstrap data + generated clients)
- `spellcards-app` (Spring Boot backend)
- `spellcards-web-dev` (Vite dev server)
- `fullstack-dev` (starts both)

## Maven

Modules:
- `spellcards-api`: OpenAPI contract + generated server interfaces/models
- `spellcards-queue`: shared queue contracts/configuration
- `spellcards-app`: REST API (search + job enqueue)
- `spellcards-worker`: async queue consumer
- `spellcards-web`: frontend

Common commands:

```bash
mvn -Psystem-node compile
mvn -pl spellcards-app -am spring-boot:run
mvn -pl spellcards-worker -am spring-boot:run
mvn -pl spellcards-web verify
mvn test
```

The `system-node` profile uses the Node.js and npm binaries on `PATH` for local
builds. CI can use the default Maven-managed Node installation.

Frontend dev via Maven:

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run dev"
```

Regenerate frontend OpenAPI client:

```bash
mvn -pl spellcards-web frontend:npm -Dfrontend.npm.arguments="run generate:client"
```

## Local Hosting (Docker Compose)

The shared `../dev-workspace` repository owns local RabbitMQ. Start it before running the application containers:

```bash
cd ../dev-workspace
./bin/init-workspace
./bin/services-up rabbitmq
cd ../spell_cards_from_pf2
```

Build and run the API + worker + frontend:

```bash
docker compose --env-file ../dev-workspace/.env up --build -d
```

Open:
- `http://localhost`
- RabbitMQ UI: `http://localhost:15672`

Optional local DNS name:
1. Add `127.0.0.1 spellcards.local` to `/etc/hosts` (or your LAN DNS).
2. Open `http://spellcards.local`.

Stop:

```bash
docker compose --env-file ../dev-workspace/.env down
```

## IntelliJ Local Development

Use the centralized IntelliJ project in `../dev-workspace`. Run `../dev-workspace/bin/init-workspace` after changing the workspace manifest, then open the `dev-workspace` directory in IntelliJ and import its generated `pom.xml` as a Maven project.

The generated run configurations include `spellcards-app`, `spellcards-worker`, `spellcards-web-dev`, `fullstack-dev`, and RabbitMQ lifecycle commands.

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

On a push to `main`, `.github/workflows/deploy.yml` publishes the images and dispatches staging to `homelab-ops`. It waits for that deployment and its homepage and spell-search checks to pass. The `production` environment then requests reviewer approval before dispatching the same image tag to production.

`HOMELAB_OPS_DISPATCH_TOKEN` needs **Contents: Read and write** and **Actions: Read-only** for `Viturin/homelab-ops`. The manual `production` target can promote an already-verified image or roll back directly, with the same reviewer approval.

## What This Tool Does

- Fetches PF2e spell data from Foundry release asset `json-assets.zip` (`packs/spells.json`) and stores generated resources in `spellcards-app/src/generated/resources/foundry/`.
- Uses a pinned Foundry release tag from `spellcards-app/pom.xml` (`foundry.version`).
- Skips download when generated data already matches the pinned version; `mvn -Pfetch-foundry-pf2e-spells clean` forces a refresh.
- Loads and parses spells in the backend.
- Exposes fuzzy search via REST: `GET /api/v1/spells/search`.
- Exposes async job enqueue endpoint: `POST /api/v1/spellcards/jobs`.
- Records each job in `runtime/spellcard-jobs/<job-id>/job.json` before publishing. Local Compose shares the `spellcards-artifacts` volume between the API and worker. When running the processes directly from different working directories, set `SPELLCARDS_ARTIFACT_DIR` to the same absolute path for each.
- Publishes job messages to RabbitMQ queue `spellcards.pdf.jobs`.
- Generates an A4 PDF in `spellcards-worker` and retains it for 24 hours after completion by default.
- Provides job status and PDF download endpoints under `/api/v1/spellcards/jobs/{jobId}`.
- Provides a frontend for live spell search and card-oriented output.

Select spells and choose **Generate PDF**. The browser checks progress while the worker generates the document and attempts a download when it is ready. Use **Download PDF** if your browser blocks automatic downloads or you want another copy.
