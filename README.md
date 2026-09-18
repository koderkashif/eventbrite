# Eventbrite — Event Booking System

A full-stack event booking showcase: **React frontend, two Spring Boot microservices, service-to-service REST, JWT auth, and concurrency-safe bookings** — built in three Milestones (MIV = Minimum Impressive Version, see `docs/phases.md`).

## Architecture

```
React (Vite, :5173)
   |  REST/JSON (dev proxy)
   -------------------------
   |                       |
event-service :8081    booking-service :8082
   |                       |        \
   |                  own DB      REST + internal API key
   |                       |          (reserve / release seats)
events + availability   users, JWT, bookings
```

Each service owns its data (no shared database). Booking-service never touches the events DB — it calls event-service over HTTP with explicit timeouts, and translates downstream failures into clean API errors (404/409/503).

## The interesting problems (and how they're solved)

| Problem | Solution |
|---|---|
| **Overbooking** — 10 users race for 1 seat | `@Transactional` + `@Version` optimistic locking in event-service. Verified: `1×201, 9×409`, seats never negative (`EventReserveConcurrencyTest`) |
| **Double-booking on retry** — user clicks Book twice | `Idempotency-Key` header + DB unique constraint; replay returns the original booking (200) instead of a second one |
| **Partial failure across services** — reserve succeeds, booking save fails | Compensation: seats released back to event-service; remote calls kept OUTSIDE local DB transactions |
| **Downstream outage** — event-service down | 2s/5s connect/read timeouts; clean `503 EVENT_SERVICE_UNAVAILABLE`; no half-created bookings |
| **Trust boundaries** | Users authenticate with JWT (issued by booking-service, validated by both). Service-to-service endpoints protected by an internal API key |
| **Dynamic filtering + pagination** | JPA `Specification`s + Spring Data `Pageable` (`?page&size&sort&category&city&search`) |

## Run it (dev)

Three terminals:

```bash
# 1. event-service
cd event-service && D:/Dev/tools/apache-maven-3.9.16/bin/mvn.cmd spring-boot:run

# 2. booking-service
cd booking-service && D:/Dev/tools/apache-maven-3.9.16/bin/mvn.cmd spring-boot:run

# 3. frontend
cd frontend && pnpm install && pnpm dev       # http://localhost:5173
```

Seeded logins: `admin@eventbrite.com / admin123` (ADMIN) · `demo@eventbrite.com / demo1234` (USER). 13 demo events are seeded on first boot.

Databases default to in-memory H2 (PostgreSQL mode); switch to PostgreSQL with `DB_URL/DB_USER/DB_PASSWORD/DB_DRIVER` env vars — no code change. `docker compose up` arrives in MIV 3.

## API surface

```
event-service (:8081)
  GET    /api/events                 public, paginated + filters (category, city, search)
  GET    /api/events/{id}            public
  POST   /api/events                 ADMIN
  PUT    /api/events/{id}            ADMIN (publish/cancel via status)
  DELETE /api/events/{id}            ADMIN (drafts/cancelled only)
  POST   /api/events/{id}/reserve    internal (API key) — concurrency-safe
  POST   /api/events/{id}/release    internal (API key)

booking-service (:8082)
  POST   /api/auth/register|login    public, returns JWT
  POST   /api/bookings               USER (supports Idempotency-Key)
  GET    /api/bookings/me            USER
  GET    /api/bookings/{id}          owner or ADMIN
  DELETE /api/bookings/{id}          owner — releases seats back
```

Every error has the same shape: `{timestamp, status, code, message, fieldErrors?}`.

## Tests

```bash
cd event-service   && mvn test   # incl. 20-thread last-seat race
cd booking-service && mvn test   # idempotency, ownership, compensation, auth
cd frontend        && pnpm build
```

## Status

- **MIV 1 (core application)** — ✅ complete and verified end-to-end (API journey + browser booking flow)
- **MIV 2 (failures, debugging, performance)** — ⏳ next: retries, circuit breaker, large dataset, EXPLAIN ANALYZE, N+1 hunt (needs PostgreSQL/Docker)
- **MIV 3 (integration testing, packaging)** — ⏳ Testcontainers, docker compose, OpenAPI, Flyway, correlation IDs
