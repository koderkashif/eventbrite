# Status Tracker

The plan lives in **`docs/phases.md`** (MIV = Minimum Impressive Version). Current session scope: **MIV 1 + MIV 2** — MIV 3 stays in the plan as future work (removed from the active task checklist only, per user 2026-09-18).

## MIV 1 — Core Application ✅ COMPLETE (2026-09-18)

Two Spring Boot services + React frontend, verified end-to-end via API journey and a real browser booking flow, running on Neon PostgreSQL:

- event-service :8081 — events CRUD, pagination/filtering (Specifications + Pageable), JWT role security, internal API-key reserve/release, `@Version` concurrency
- booking-service :8082 — register/login (BCrypt + JWT), bookings with price snapshot + booking references, Idempotency-Key (code + DB unique constraint), RestClient with timeouts + error translation + compensation
- frontend :5173 — Vite + React Router + axios (pnpm); events/search/pagination, details + booking, confirmation, My Bookings + cancel, admin CRUD, role-guarded routes

## MIV 2 — Failures, Debugging, Performance ✅ COMPLETE (2026-09-19)

Full write-up with measured numbers: **`docs/perf-notes.md`**.

- Timeouts (2s connect / 5s read), 503 translation, compensation — verified in MIV 1, re-verified here
- **Resilience4j**: retry (3 attempts, connection-refused only) composed inside circuit breaker (10-call window, 50% threshold, 10s open, half-open probes; business errors excluded) — outage drill measured: ~330 ms per failing call → **3 ms fast-fail** once open → automatic recovery
- **Request timing filters** in both services (method, path, status, ms)
- **Dataset**: 50,014 events (13.1 s) + 200,003 bookings (50.7 s) via JDBC batch seeders, loaded through the service boundary
- **Index story**: `idx_events_city_start_time` (expression index matching the JPA spec's `lower(city)`) → **10.4 ms → 0.23 ms (45×)**, Sort node eliminated; `idx_bookings_user_created` → **30.8 ms → 5.8 ms (5×)**
- Honest finding recorded: endpoint wall-times are dominated by WAN + JSON payload, not the DB — measure before optimizing

## MIV 3 — Integration Testing, Packaging, Tooling ⏳ FUTURE (not this session)

Testcontainers, Dockerfiles + docker compose, Swagger/OpenAPI, Flyway, correlation IDs, optional Kafka/outbox/Redis — full spec in `docs/phases.md`.
