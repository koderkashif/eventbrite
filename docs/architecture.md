# System Design & Architecture

This document describes **what was built, why, and how** — the companion learning doc is [`docs/concepts.md`](concepts.md) (explains each concept from scratch). Measured performance numbers live in [`docs/perf-notes.md`](perf-notes.md).

---

## 1. What this system is

An event booking platform (an Eventbrite-style clone): users browse/search events, book tickets, and cancel; admins create and manage events. What makes it interesting is not the feature list but the engineering underneath:

- **Two microservices**, each owning its own database, talking over HTTP
- **Concurrency-safe booking** — 20 racing requests for the last seat produce exactly 1 booking (verified by a 20-thread test)
- **Double-click-proof** — retries with an `Idempotency-Key` never book twice
- **Failure-aware** — timeouts, retry, circuit breaker, and compensation (undo) around cross-service calls
- **Stateless JWT auth** with role-based access
- A **React SPA** frontend on top

Built in milestones (MIV = Minimum Impressive Version) per [`docs/phases.md`](phases.md): MIV 1 core app, MIV 2 failures/debugging/performance, MIV 3 (future) integration testing & packaging.

## 2. High-level architecture

```
                    Browser (React SPA, Vite dev server :5173)
                          |  /api/** (same-origin; Vite proxies in dev)
        ┌─────────────────┴──────────────────┐
        ▼                                     ▼
┌──────────────────┐               ┌──────────────────────┐
│  event-service   │  reserve/     │   booking-service    │
│  :8081           │◀─release──────│   :8082              │
│                  │  REST +        │                      │
│ Owns:            │  X-Internal-   │ Owns:                │
│  events          │  Api-Key       │  users (BCrypt)      │
│  availability    │                │  JWT issuing         │
│                  │                │  bookings            │
│  DB: eventdb     │                │   DB: bookingdb      │
└──────────────────┘               └──────────────────────┘
        Neon PostgreSQL (separate databases — no cross-DB access anywhere)
```

**The one rule that shapes everything:** no service ever reads another service's database. booking-service knows an event only by its **ID** and by what event-service tells it over HTTP.

## 3. The stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 21 | JD requirement; records, modern language features |
| Framework | Spring Boot 3.5.16 | 3.x is the interview mainstream |
| Data access | Spring Data JPA (Hibernate 6) | entities + repositories; Specifications for dynamic filters |
| Bulk loading | `JdbcTemplate` batchUpdate | 10× faster than JPA `saveAll` for synthetic data |
| Security | Spring Security 6 + jjwt 0.12.6 | stateless JWT, method security |
| Resilience | Resilience4j 2.2.0 (core, programmatic) | retry + circuit breaker |
| HTTP client | Spring `RestClient` | synchronous service-to-service calls with timeouts |
| Database | PostgreSQL (Neon serverless) / H2 (tests) | `DB_URL/DB_USER/DB_PASSWORD/DB_DRIVER` env vars switch between them, zero code change |
| Connection pool | HikariCP (Boot default, tuned) | `keepalive-time: 240000` for Neon's scale-to-zero |
| Frontend | React 18 + Vite + React Router 6 + axios | SPA with dev proxy splitting `/api` by service |
| Package manager | pnpm | user preference |
| Build | Maven 3.9.16 | |

## 4. Service breakdown

Both services follow the same layering: `controller → service → repository`, with DTOs at the boundary, mappers between DTO and entity, and a `GlobalExceptionHandler` that converts every exception into one JSON error shape:

```json
{ "timestamp": "...", "status": 409, "code": "INSUFFICIENT_SEATS", "message": "...", "fieldErrors": null }
```

### 4.1 event-service (`:8081`)

**Bounded context:** everything about events — catalog, lifecycle (DRAFT → PUBLISHED → CANCELLED/COMPLETED), capacity and availability.

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/events` | public | paginated + `category`/`city`/`search` filters |
| `GET /api/events/{id}` | public | |
| `GET /api/events/admin` | ADMIN | all statuses |
| `POST/PUT/DELETE /api/events/**` | ADMIN | `@PreAuthorize("hasRole('ADMIN')")` |
| `POST /api/events/{id}/reserve` | internal (API key) | the concurrency-critical operation |
| `POST /api/events/{id}/release` | internal (API key) | compensating action |

Key files:

- `entity/Event.java` — the aggregate. Business rules live **on the entity**: `reserveSeats()` throws `InsufficientSeatsException` / `InvalidEventStateException` instead of exposing bare setters for seat math. `@Version` field for optimistic locking.
- `service/EventService.java` — `@Transactional` boundaries; dynamic filtering via a `Specification` built from whichever parameters arrived.
- `repository/EventRepository.java` — `JpaRepository` + `JpaSpecificationExecutor` (13 lines total).

### 4.2 booking-service (`:8082`)

**Bounded context:** identity and bookings — users, passwords, JWT issuing, booking records.

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/auth/register`, `/api/auth/login` | public | returns JWT + user |
| `POST /api/bookings` | USER | supports `Idempotency-Key` header |
| `GET /api/bookings/me` | USER | |
| `GET /api/bookings/{id}` | owner or ADMIN | ownership checked in the service |
| `DELETE /api/bookings/{id}` | owner | cancel = release seats remotely, then mark CANCELLED |

Key files:

- `service/BookingService.java` — orchestrates the cross-service booking flow (§7).
- `client/EventServiceClient.java` — the **only** place that speaks HTTP to event-service; wraps every call in retry-inside-circuit-breaker (§9) and translates downstream errors.
- `entity/Booking.java` — stores a **price/name snapshot** of the event at booking time and a plain `event_id` column (no foreign key — it points into another service's database).
- `security/` — `JwtService` (issues), `JwtAuthFilter` (validates), `SecurityConfig` (stateless chain).

## 5. Data model

### eventdb.events (owned by event-service)

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity PK | |
| name, description, venue, city | text | |
| category | enum (MUSIC, TECH, SPORTS, BUSINESS, FOOD, ART) | stored as STRING |
| start_time, end_time | timestamp | |
| ticket_price | NUMERIC(10,2) | BigDecimal, never double |
| capacity, available_seats | INT | `availableSeats` is decremented on reserve |
| status | enum (DRAFT, PUBLISHED, CANCELLED, COMPLETED) | only PUBLISHED is bookable |
| **version** | BIGINT | `@Version` — optimistic locking |
| created_at, updated_at | | set in `@PrePersist`/`@PreUpdate` |

### bookingdb.bookings (owned by booking-service)

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity PK | |
| booking_reference | `BK-XXXXXXXX` | unique, shown to users |
| user_id | BIGINT | plain column — points into bookingdb.users |
| event_id | BIGINT | plain column — points into **eventdb** (cross-service, by ID only) |
| event_name, price_per_ticket | snapshot | denormalized at booking time — a receipt must not change when the event is renamed or repriced |
| quantity, total_amount | | total computed **server-side**, never trusted from the client |
| status | enum (CONFIRMED, CANCELLED) | |
| **idempotency_key** | VARCHAR(64) | UNIQUE constraint — the DB-level idempotency guarantee |
| created_at, updated_at | | |

### bookingdb.users

`id, name, email (unique), password (BCrypt), role (USER/ADMIN)`.

### Indexes (added in MIV 2, with EXPLAIN ANALYZE evidence)

| Index | Query it serves | Effect |
|---|---|---|
| `idx_events_city_start_time ON events (lower(city), start_time)` | the main browse query | 10.4 ms → 0.23 ms (45×); Sort node eliminated |
| `idx_bookings_user_created ON bookings (user_id, created_at)` | my-bookings | 30.8 ms → 5.8 ms (5×) |

The events index is an **expression index** — it matches `lower(city)` because that's exactly what the JPA `Specification` generates; a plain `(city, …)` index would never be used by that query.

## 6. Cross-service communication

booking-service → event-service uses Spring `RestClient` (synchronous), configured in `RestClientConfig`:

- **Timeouts**: 2 s connect / 5 s read — a hung downstream must not hold threads forever
- **Two trust paths, deliberately separate**:
  - *User traffic* → `Authorization: Bearer <JWT>` (both services validate with the shared secret)
  - *Service traffic* → `X-Internal-Api-Key` on `/reserve` + `/release` only (`InternalApiKeyFilter` in event-service). booking-service does **not** forward the user's JWT on these calls — the reserve decision is the service's, not the user's.
- **Error translation contract** (`EventServiceClient`):
  - 404/409 from event-service → business exceptions (`EventNotFoundException`, `InsufficientSeatsException`, …) → surface to the user as-is. These are **successful infrastructure calls** and never count as failures.
  - connection refused / timeout → `EventServiceUnavailableException` → 503 to the user; these DO count against the circuit breaker.
  - circuit open → `EventServiceUnavailableException` answered in ~3 ms.

## 7. The booking flow (the money path)

`POST /api/bookings` with JWT + `Idempotency-Key`, in `BookingService.create()`:

```
1. Idempotency fast path
   └─ SELECT by idempotency_key → hit? return original booking (200, replayed=true)

2. RESERVE on event-service (HTTP, outside any local transaction)
   └─ POST /api/events/{id}/reserve {quantity}
      inside event-service, one @Transactional:
        SELECT event (with @Version)
        if status != PUBLISHED       -> 409 InvalidEventState
        if quantity > availableSeats -> 409 InsufficientSeats
        availableSeats -= quantity
        UPDATE ... WHERE id=? AND version=?     <- 0 rows = someone else won; rollback, 409

3. SAVE booking locally
   price + name snapshotted from the reserve response; total computed server-side
   └─ unique constraint on idempotency_key is the last line of defense:
        - same key raced us? -> release our seats, return the winner's booking
        - any other failure? -> release our seats, rethrow  (compensation)

4. Return 201 with booking reference
```

Two deliberate principles, both visible in the code:

1. **Remote calls never happen inside a local DB transaction** — holding a pool connection open across a WAN HTTP call starves the pool.
2. **Compensation over distributed transactions** — no 2PC; if the local save fails after a successful reserve, we call `/release` to give the seats back. (A tiny hand-rolled saga.)

**Cancel** is remote-first for the same reason: release the seats on event-service, *then* mark the booking CANCELLED. If the release fails, the booking stays CONFIRMED and the user retries — we never destroy the local record while the remote side still holds the seats.

## 8. Concurrency & idempotency (why nothing double-books)

**Overbooking — optimistic locking.** Every event row carries `version`. A transaction that modified the row updates with `WHERE id = ? AND version = ?`. Two racing reservations both read version 7; the first UPDATE bumps it to 8; the second matches **0 rows**, Spring throws `OptimisticLockingFailureException` → rollback → the user gets a 409. Verified: 20 parallel bookings for 1 remaining seat → `1×201, 19×409`, seats never negative (`EventReserveConcurrencyTest`).

**Double-booking on retry — idempotency.** The frontend generates a UUID per booking click (`crypto.randomUUID()`). A retry with the same key:
- fast path: app-level `findByIdempotencyKey` → return the original booking, book nothing
- race window: if two requests with the same key pass the fast path simultaneously, the **DB unique constraint** `uk_booking_idempotency_key` decides the winner; the loser catches `DataIntegrityViolationException`, releases its seats, and returns the winner's booking

So three layers guard the same invariant: app check, DB constraint, and (for lost-response retries) the key itself.

## 9. Resilience layer (MIV 2)

Configured programmatically in `ResilienceConfig` (composition order is the point, annotations would hide it):

```
circuitBreaker( retry( HTTP call ) )        // EventServiceClient.protectedCall()
```

- **Retry** — 3 attempts, 150 ms apart, and *only* for connection-refused (`EventServiceRetryPolicy.isDefinitelyNotDelivered`: `ResourceAccessException` caused by `ConnectException`). If the TCP connect never succeeded, the request provably never reached event-service — so retrying even the state-changing `/reserve` cannot double-book. A read timeout is **not** retried (the request may have succeeded with a lost response — that case belongs to idempotency, not retry).
- **Circuit breaker** — sliding window 10, min 5 calls, ≥50% failure → OPEN (fail fast, ~3 ms, `CallNotPermittedException`); after 10 s → HALF_OPEN with 3 probes; success closes. Business 404/409s are `ignoreExceptions` — a sold-out event must never trip infrastructure protection.

Measured outage drill (event-service killed): failing calls ~330 ms each → circuit opens → **3–4 ms fail-fast** → restart + 10 s → probe succeeds → CLOSED. No restart of booking-service needed. Full table in [`perf-notes.md`](perf-notes.md).

## 10. Security architecture

**Stateless JWT (HS256, shared secret via `jwt.secret`):**

```
register/login (booking-service)
   └─ BCrypt verify → Jwts.builder().subject(userId).claim(role, email).sign() → token
every request (both services)
   └─ JwtAuthFilter: parse Bearer token → SecurityContext gets (userId, ROLE_x)
      controllers read @AuthenticationPrincipal Long userId
```

- booking-service **issues** (it owns identity); both services **validate statelessly** — no session, no DB lookup per request.
- **BCrypt** via `DelegatingPasswordEncoder` (stores the algorithm inline: `{bcrypt}$2a…`), so upgrading algorithms later doesn't orphan old passwords.
- **Roles**: URL rules in `SecurityConfig` (`/api/auth/**` permitAll, else authenticated) + method rules `@PreAuthorize("hasRole('ADMIN')")` on mutations. Authorization denials (403) surface from MVC, not the filter chain — handled in `GlobalExceptionHandler`.
- **CSRF disabled** deliberately: no cookies → no CSRF surface (the token goes in a header, which JavaScript-injected attacks can't read).
- **Two trust paths** (see §6): user JWT vs internal API key. Internal key compared with `.equals()` in `InternalApiKeyFilter`; user traffic never carries it.

## 11. Querying: dynamic filters + pagination

`GET /api/events?category=TECH&city=Mumbai&search=java&page=0&size=20&sort=startTime,asc`

- `@PageableDefault(size = 20, sort = "startTime") Pageable` — resolved straight from the query string.
- `EventService.byFilters()` builds a **JPA Specification**: each present parameter adds a predicate, combined with AND. One code path for every filter combination instead of a derived query per combination.
- Response shape `PageResponse<T>`: `{content, page, size, totalElements, totalPages}` — a stable contract the frontend's `Pagination` component understands.

## 12. Observability

- `RequestTimingFilter` (both services): `log.info("{} {} -> {} [{} ms]", method, uri, status, ms)` — one line per request, cheap enough to keep on always. Never logs headers or bodies (tokens, passwords).
- `EventServiceClient` logs per-call duration (`System.nanoTime()`), so WAN vs processing time is separable.
- This is how MIV 2's decomposition was done: endpoint wall time ≠ DB time (see perf-notes).

## 13. Frontend architecture (React SPA)

```
frontend/src/
  api/        client.js (axios instance + interceptors), auth.js, events.js, bookings.js
  context/    AuthContext.jsx        login/register/logout, user state, token persistence
  components/ Navbar, ProtectedRoute, Pagination, EventCard
  pages/      Events, EventDetails, BookingConfirmation, MyBookings, Login, Register
  pages/admin/ AdminEvents, EventForm
  App.jsx     route table
```

- **Dev proxy** (`vite.config.js`): `/api/auth` + `/api/bookings` → :8082, `/api/events` → :8081. The browser sees one origin — no CORS in dev, and paths stay deployment-agnostic.
- **Axios instance** (`api/client.js`): one `baseURL: '/api'`; a request interceptor attaches `Bearer <token>` from localStorage to every call; helpers `getErrorMessage` (uniform error text incl. field errors) and `cleanParams` (drop empty filters).
- **AuthContext**: wraps the app, exposes `{user, isAdmin, login, register, logout}`; lazy-initializes `useState` from localStorage; `useAuth()` consumes it.
- **ProtectedRoute**: renders `<Navigate to="/login">` when logged out, and additionally requires `ADMIN` when `adminOnly` — the UI mirror of backend `@PreAuthorize` (the backend remains the real enforcer; the UI guard is UX, not security).
- **Data fetching**: `useEffect` keyed on `[page, filters]`, with a `cancelled` cleanup flag so a stale response can't overwrite a newer one. Filters are applied on submit (`applyFilters` resets page to 0).
- **Idempotency on the client**: `bookings.js` generates `crypto.randomUUID()` as the `Idempotency-Key` per booking click — the retry-safety story starts here.
- Build check: `pnpm build` runs through Vite.

## 14. Key design decisions & trade-offs

| Decision | Alternative rejected | Why |
|---|---|---|
| Two services, database-per-service | Single monolith | the JD is microservices; and it *forces* the interesting problems (partial failure, compensation, idempotency) into the code |
| Sync REST between services | Kafka/events | booking needs the reserve answer *now*; async would make "sold out" unknowable at click time. MIV 3 lists Kafka+outbox as the future direction |
| Optimistic locking (`@Version`) | Pessimistic (`SELECT … FOR UPDATE`) | conflicts are rare (most events aren't down to the last seat); pessimistic locks hurt throughput for every request to pay for the rare race |
| Compensation (release) over 2PC/XA | Distributed transactions | 2PC couples availability and is operationally painful; a hand-rolled saga is transparent and interview-grade |
| Retry only connection-refused | Retry all 5xx | an unaware retry of a delivered POST double-books; only "provably not delivered" is safe |
| Business errors excluded from CB | All errors trip CB | sold-out events would otherwise take down infrastructure protection |
| Price/name snapshot on Booking | JOIN across services (impossible) or always fetch current | a receipt must show what was booked, at the booked price |
| `BigDecimal` for money | `double` | binary floats can't represent 0.10 exactly |
| H2 in tests, PostgreSQL in prod | Postgres everywhere incl. CI | fast, zero-setup tests; same SQL surface for what this app exercises |
| No CORS — dev proxy | CORS config on services | one origin from the browser's perspective; simpler and safer |

## 15. Known limitations (documented, not hidden)

- `/api/bookings/me` returns every booking unpaginated (~4,000 rows → multi-MB JSON) — the top measured follow-up (perf-notes).
- `?search=` uses `lower(name) LIKE '%term%'` — leading-wildcard LIKE can't use a B-tree; fix would be a `pg_trgm` GIN index.
- Shared JWT secret via config — at real scale, asymmetric keys (RS256: booking-service signs with private key, event-service verifies with public) would remove secret sharing.
- Internal API key compared with `equals()` (not constant-time) — acceptable here; noted for completeness.
- MIV 3 (future): Testcontainers, Dockerfiles + compose, OpenAPI docs, Flyway migrations, correlation IDs, Kafka/outbox.

## 16. Repository map

```
event-service/src/main/java/com/eventbrite/event/
  controller/EventController.java        REST endpoints (+ @PreAuthorize)
  service/EventService.java              @Transactional use cases, Specification builder
  repository/EventRepository.java        JpaRepository + JpaSpecificationExecutor
  entity/Event.java                      aggregate: rules + @Version on the data they protect
  dto/                                   Create/Update requests, EventResponse, PageResponse
  mapper/EventMapper.java                entity <-> DTO
  security/                              JwtService (validate), JwtAuthFilter, InternalApiKeyFilter, SecurityConfig
  exception/                             ApiError + GlobalExceptionHandler + domain exceptions
  filter/RequestTimingFilter.java        one log line per request
  bootstrap/                             DataSeeder (demo), BulkEventSeeder (50k, @Profile("bulk"))

booking-service/src/main/java/com/eventbrite/booking/
  controller/                            AuthController, BookingController
  service/                               AuthService (login/register), BookingService (the money path)
  client/EventServiceClient.java         ALL cross-service HTTP + resilience wrappers
  config/                                RestClientConfig (timeouts), ResilienceConfig (retry+CB)
  entity/                                User (BCrypt), Booking (snapshot + idempotency_key)
  security/                              JwtService (issues), JwtAuthFilter, CustomUserDetailsService, SecurityConfig
  exception/                             ApiError + handler + business exceptions
  filter/RequestTimingFilter.java
  bootstrap/                             DataSeeder, BulkBookingSeeder (200k)

frontend/src/                            see §13
docs/                                    phases.md (plan), ROADMAP.md (status), perf-notes.md (numbers)
scripts/dev.sh                           boots both services against Neon (creds from .env) or H2 fallback
```
