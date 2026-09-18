# Status Tracker

The plan lives in **`docs/phases.md`** (MIV = Minimum Impressive Version). This file tracks where we are.

## MIV 1 — Core Application ✅ COMPLETE (2026-09-18)

Two Spring Boot services + React frontend, verified end-to-end via API journey and a real browser booking flow:

- event-service :8081 — events CRUD, pagination/filtering (Specifications + Pageable), JWT role security, internal API-key reserve/release, `@Version` concurrency
- booking-service :8082 — register/login (BCrypt + JWT), bookings with price snapshot + booking references, Idempotency-Key (code + DB unique constraint), RestClient with timeouts + error translation + compensation
- frontend :5173 — Vite + React Router + axios; events/search/pagination, details + booking, confirmation, My Bookings + cancel, admin CRUD, role-guarded routes

Verified behaviors (live runs): register/dup-email/bad-login · user→admin API 403 · no-token 401 · publish flow · validation 400s with field errors · pagination+filters (category/city/search, invalid enum 400) · booking 201 + seats decrement · **idempotent replay 200 same-reference, seats unchanged** · 404/409 translation across services · ownership 403 · cancel restores seats · double-cancel 409 · **race: 1×201 + 9×409 on last seat, seats = 0** · **event-service killed → clean 503, booking-service stays healthy** · malformed JSON → 400.

Test suites: event-service (unit + 20-thread race `1 success / 19 failures`), booking-service (idempotency, ownership, compensation, auth).

## MIV 2 — Failures, Debugging, Performance ⏳ NEXT

Timeouts already in place; remaining: retries (safe vs unsafe), Resilience4j circuit breaker, 50–100k event dataset, EXPLAIN ANALYZE + index before/after numbers, N+1 hunt, request timing logs.

**Blocked on a user decision: Docker Desktop (admin + WSL2, enables `docker compose up` later) vs native PostgreSQL install. Needed for real-Postgres profiling.**

## MIV 3 — Integration Testing, Packaging, Tooling ⏳

Testcontainers (concurrency + idempotency integration tests), Dockerfiles + `docker compose up`, Swagger/OpenAPI, Flyway migrations, correlation IDs. Optional: Kafka notifications, transactional outbox, Redis.
