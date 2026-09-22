# PurseKeep

Personal and shared finances: track expenses, budgets, accounts and cards across currencies, alone or with a household. Live at a tailnet-only URL for now; Android companion app in `android/`. Full design in [plan.md](plan.md).

## Stack

Next.js (App Router) · TypeScript · Tailwind v4 · Drizzle ORM · PostgreSQL 16 · Auth.js v5 · Docker Compose with a Tailscale sidecar (`tailscale serve` exposes the app tailnet-only over HTTPS).

## Local development

```bash
npm install
cp .env.example .env        # fill in AUTH_SECRET (openssl rand -base64 32) and DB creds
                            # for local dev set DATABASE_URL host to localhost
docker run -d --name money-pg -p 5432:5432 \
  -e POSTGRES_USER=money -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=money_maker \
  postgres:16-alpine        # or any local Postgres 16
npm run db:migrate          # apply migrations
npm run dev                 # http://localhost:3000
```

Useful scripts: `npm run lint` · `npm run typecheck` · `npm run test` · `npm run db:generate` (new migration from schema changes).

## Deployment

### Render (recommended)

The repo ships a `render.yaml` Blueprint. In the Render dashboard: Blueprints
→ New → select this repo. It creates the web service and a Postgres database,
wires `DATABASE_URL` between them, and generates `AUTH_SECRET`.
`REGISTRATION_MODE=invite` is set by the blueprint, so sign-up is invite-only
by default on a public URL — send people a household invite link rather than
opening registration. If you attach a custom domain, set `AUTH_URL` to that
public origin (otherwise Render's own `RENDER_EXTERNAL_URL` is used).

### Self-hosted with Docker Compose

Push to `main` → GitHub Actions `deploy` workflow runs on a **self-hosted runner** on the home docker host and does `docker compose up -d --build` with env from `/opt/money-maker/.env`.

One-time server setup (`tailscale ssh root@docker`):

1. `mkdir -p /opt/money-maker && cp .env.example /opt/money-maker/.env` — fill in real secrets; `DATABASE_URL` host stays `postgres`.
2. Install a GitHub Actions self-hosted runner (repo → Settings → Actions → Runners → New self-hosted runner), run it as a systemd service, runner user in the `docker` group.
3. Put a one-time Tailscale auth key in `TS_AUTHKEY` for first boot; the tailscale state volume persists the identity afterwards.

App comes up at `https://money-maker.<your-tailnet>.ts.net` (tailnet-only; both phones need the Tailscale app). `REGISTRATION_MODE` is unset here, so it defaults to `open` — same behaviour as before.

## Project layout

See [plan.md §7](plan.md) — schema in `src/db/schema.ts`, migrations in `src/db/migrations/`, domain logic in `src/lib/`, infra in `docker/` and `.github/workflows/`.
