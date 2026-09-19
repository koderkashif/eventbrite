# Performance & Resilience Notes (MIV 2)

All numbers below are from real runs on 2026-09-19 against **Neon PostgreSQL** (ap-southeast-1, accessed over WAN from India), services running locally.

## Dataset

| Table | Rows | Load method | Load time |
|---|---|---|---|
| events | 50,014 (45,013 PUBLISHED, 5,001 DRAFT) | `BulkEventSeeder` — JDBC batches of 500 (`--spring.profiles.active=bulk`) | 13.1 s (~3,800 rows/s) |
| bookings | 200,003 | `BulkBookingSeeder` — JDBC batches of 1,000; event refs fetched **through the event-service API** (40 pages × 500), never the events DB | 50.7 s (~3,950 rows/s) |
| users | 52 (2 demo + 50 load-test, password `bulk1234`) | same seeder | < 0.5 s |

Bulk seeding uses `JdbcTemplate` batches instead of JPA `saveAll`: 200k entities through the EntityManager means 200k dirty-check snapshots; plain batched JDBC is an order of magnitude faster for synthetic data with no business rules.

## Finding 1 — events filtered by city (the main browse query)

Query (what the JPA `Specification` produces for `GET /api/events?city=Mumbai&size=20`):

```sql
SELECT * FROM events
WHERE status = 'PUBLISHED' AND lower(city) = lower('Mumbai')
ORDER BY start_time ASC LIMIT 20 OFFSET 0
```

**Before** (no supporting index):

```
Limit  (actual time=10.390..10.394 rows=20)
  ->  Sort  (actual time=10.388..10.390)  Sort Key: start_time  Sort Method: top-N heapsort
        ->  Seq Scan on events  (actual time=0.012..9.735 rows=5005)
              Filter: ((status = 'PUBLISHED') AND (lower(city) = 'mumbai'))
              Rows Removed by Filter: 45009        Buffers: shared hit=1283
Execution Time: 10.4 ms
```

Every request read the **whole 50k-row table** (1,283 pages) to return 20 rows.

**Fix** — an *expression* index matching exactly what the code generates (the `Specification` compares `lower(city)`, so a plain `(city, ...)` index would never be used):

```sql
CREATE INDEX idx_events_city_start_time ON events (lower(city), start_time);
ANALYZE events;
```

**After**:

```
Limit  (actual time=0.088..0.167 rows=20)
  ->  Index Scan using idx_events_city_start_time on events  (actual time=0.087..0.164 rows=20)
        Index Cond: (lower(city) = 'mumbai')   Filter: (status = 'PUBLISHED')
        Buffers: shared hit=20 read=3
Execution Time: 0.231 ms
```

**10.4 ms → 0.23 ms (45×)**, and the `Sort` node is gone entirely — a composite index on `(equality_column, order_column)` delivers rows already in order, so PostgreSQL skips sorting.

## Finding 2 — "my bookings"

Query behind `GET /api/bookings/me` (`findByUserIdOrderByCreatedAtDesc`):

```sql
SELECT * FROM bookings WHERE user_id = 3 ORDER BY created_at DESC
```

**Before**: `Gather Merge` + 2 parallel workers, each Seq Scanning and Sorting — 24.6–30.8 ms for ~3,972 rows of that user.

**Fix**: `CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at); ANALYZE bookings;`

**After**: `Bitmap Heap Scan` using the index + one small quicksort — **5.4–5.8 ms (~5×)**.

## The honest part: where endpoint time actually went

| Endpoint (client-measured, 5 runs) | Before indexes | After indexes | DB time before → after |
|---|---|---|---|
| `GET /api/events?city=Mumbai&size=20` | 173–276 ms | 177–299 ms | 10.4 ms → **0.23 ms** |
| `GET /api/bookings/me` | 406–1,122 ms | 468–812 ms | 30.8 ms → **5.8 ms** |

Endpoint wall time barely moved — because it was never dominated by the database. Decomposition (from `RequestTimingFilter` + EXPLAIN):

- **events endpoint**: ~175 ms total ≈ WAN round-trips to Singapore + HTTP/Jackson. DB is now 0.2 ms — the next optimization is *co-locating app and DB*, not touching the query.
- **bookings/me**: ~500 ms ≈ serializing **~4,000 bookings into one multi-MB JSON response**. The real fix is *pagination* (events list already has it; `/me` deliberately doesn't yet) — noted as the top follow-up.

Lesson worth saying out loud in an interview: **measure first**. Two plausible-sounding "obvious" fixes (index, query rewrite) were not what this endpoint needed; the measurement said network and payload size.

## N+1 check

`show-sql: true` was watched during list endpoints: the paged events query and my-bookings each execute **exactly one SELECT** — the schema has no lazy `@ManyToOne` collections (event references were deliberately denormalized into booking rows across the service boundary in MIV 1), so N+1 cannot occur on these paths. If relations existed, the fixes in order of preference: DTO projection → `@EntityGraph` / `JOIN FETCH` — never blanket `fetch = EAGER` (that trades a visible N+1 for permanent hidden over-fetching).

## Known limitation (documented, not fixed)

`?search=term` runs `lower(name) LIKE '%term%'` — a **leading-wildcard** LIKE cannot use a btree index, so search always seq-scans. The proper fix is a `pg_trgm` GIN index; deliberately not added since search isn't the hot path in this dataset and it would obscure the main story.

## Resilience — event-service outage drill (measured)

Config: retry (3 attempts, 150 ms apart, **connection-refused only** — provably never delivered, so even the state-changing `/reserve` can be retried safely) composed **inside** a circuit breaker (10-call window, min 5 calls, 50% failure threshold, 10 s open wait, 3 half-open probes). Business 404/409s are `ignoreExceptions` — a sold-out event must never trip infrastructure protection.

Observed with event-service killed:

| Phase | Client-visible result | Time |
|---|---|---|
| Healthy booking | 201 | 1,051 ms (includes WAN reserve) |
| Outage, calls 1–4 | 503 (3 connect attempts + 2×150 ms backoff each) | ~330 ms each |
| 5th recorded call: failure rate 80% ≥ 50% → **circuit OPENS** | | |
| Calls 5–7 (breaker open) | 503 `circuit breaker open - failing fast` | **3–4 ms** |
| event-service restarted + 10 s wait (OPEN → HALF_OPEN) | probe call succeeds → **circuit CLOSED** | 201 |

No thread was held hostage by timeouts once the circuit opened, no half-created bookings, and recovery required no restart of booking-service. Log evidence: `RequestTimingFilter` lines + `circuit breaker OPEN - failing fast` warnings in booking-service logs.

## Reproduce

```bash
# bulk data (idempotent; skips if already loaded)
java -jar event-service/target/event-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=bulk
java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=bulk   # needs event-service up

# explain plans (any SQL client, e.g. Neon console)
EXPLAIN ANALYZE SELECT * FROM events WHERE status='PUBLISHED' AND lower(city)=lower('Mumbai') ORDER BY start_time LIMIT 20;
EXPLAIN ANALYZE SELECT * FROM bookings WHERE user_id=3 ORDER BY created_at DESC;
```
