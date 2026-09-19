# Concepts Explained — Your Learning Doc

This doc is **for you** (not for showing interviewers — that one is [`architecture.md`](architecture.md)). It explains every concept the project uses, from zero, in a way meant to stick. The best way to use it: read one concept, then open the files it mentions and find the idea in real code. Code you've *seen working* is code you'll remember.

Every concept follows the same shape: **what it is → analogy → how it works → where it lives in this project → the one line to remember.**

---

## Part A — Spring Boot foundations

### 1. Spring and Spring Boot

**What:** Spring is a framework that builds and wires your objects for you. Spring Boot is Spring plus conventions that make a service start with near-zero configuration.

**Analogy:** Running a Java program the plain way is cooking a full meal from scratch — you buy pans, gas, groceries. Spring Boot is a cloud kitchen that's already stocked: walk in with a recipe (your business logic), everything else (stove, knives, ingredients) is ready.

**How it works:** A Spring Boot app is one class with `main()` that calls `SpringApplication.run(...)`. That call starts the **IoC container** (next concept), which scans your packages, finds every class marked `@Component`/`@Service`/`@RestController`/`@Repository`, instantiates them, wires them together, starts the embedded web server (Tomcat), and begins answering HTTP requests — all without you writing any of that plumbing.

**Auto-configuration:** the magic people mean when they say "Boot". You add a *starter* dependency like `spring-boot-starter-web`, and Boot detects it on the classpath and configures what that starter implies (JSON serialization, an embedded server, MVC defaults). Same for `spring-boot-starter-data-jpa` → Hibernate + a datasource + HikariCP pool. You override anything you dislike in `application.yml`. **Starters are dependency bundles + auto-configuration triggers** — that's the whole trick.

**In this project:** `event-service/.../EventServiceApplication.java` and `booking-service/.../BookingServiceApplication.java` are each ~5 lines. Everything else in this doc exists inside that scaffolding. `application.yml` in each service holds the knobs (port, datasource, jwt secret).

**Remember:** *Spring manages objects; Boot configures Spring. You write business rules only.*

### 2. Inversion of Control and Dependency Injection

**What:** Your classes never build their own dependencies — they *ask for* them in the constructor, and Spring supplies them.

**Analogy:** A chef (your service) doesn't grow vegetables or raise chickens. The kitchen hands the chef exactly what the recipe needs. If tomorrow the supplier switches to organic tomatoes, the chef's recipe doesn't change — the kitchen swapped the ingredient. Swap a real database for a fake one in tests the same way.

**How it works:**
```java
public BookingService(BookingRepository bookingRepository, EventServiceClient eventServiceClient) {
    this.bookingRepository = bookingRepository;
    this.eventServiceClient = eventServiceClient;
}
```
There's no `new BookingRepository()` anywhere. At startup Spring created one instance ("bean") of each, saw `BookingService`'s constructor needs them, and passed them in. This is *inversion of* control: a plain program calls down into libraries; here the framework calls **into** your code after handing you your dependencies.

**Why it matters (interview favorite):** (1) testability — a test can construct `BookingService` with a fake repository; (2) swap implementations without touching consumers; (3) one shared instance with one configuration (e.g., one HTTP client, one pool).

**In this project:** every `@Service`, `@Controller`, `@Component` in both services uses constructor injection exactly like above. You'll never see `new` for a collaborator.

**Remember:** *"Don't call us, we'll call you — and here's what you asked for."*

### 3. Stereotype annotations: which label goes where

**What:** Labels that tell Spring "make and manage one instance of this."

| Annotation | Meaning | Example in this repo |
|---|---|---|
| `@Component` | generic Spring-managed object | `EventServiceClient` |
| `@Service` | business logic layer | `BookingService`, `EventService` |
| `@RestController` | handles HTTP | `EventController` |
| `@Repository` | data access | `EventRepository` |
| `@Configuration` + `@Bean` | I build objects myself, here's the recipe | `ResilienceConfig`, `RestClientConfig` |

`@Service`/`@Repository`/`@RestController` are all `@Component` underneath — the different names are documentation plus a few technical side effects (e.g. `@Repository` translates DB error types).

**`@Bean` methods** live inside `@Configuration` classes and are for objects you must *construct* (library objects with builders): `CircuitBreaker.of("eventService", config)` isn't yours to annotate, so you write a method that returns it and Spring manages the result. When two beans of the same type exist, `@Qualifier("eventServiceCircuitBreaker")` picks which one to inject — used in `EventServiceClient`'s constructor.

**Remember:** *annotate your classes; `@Bean` for library objects; `@Qualifier` when there's a choice.*

### 4. The layered architecture: controller → service → repository

**What:** Every request flows through three layers, each with one job.

```
HTTP  →  Controller        (translate: HTTP in/out, validate shape)
       → Service           (decide: business rules, transactions, orchestration)
       → Repository        (persist: talk to the database)
```

**Analogy:** A restaurant. Waiter (controller) takes the order in customer language. Chef (service) makes decisions — recipes, substitutions, timing. Storekeeper (repository) fetches ingredients. The waiter never touches the store; the chef never talks to the customer.

**In this project — one event read, traced:**
- `EventController.getById(id)` → receives `GET /api/events/5`, returns the DTO
- `EventService.getById(id)` → `@Transactional(readOnly=true)`, maps entity→DTO, throws `EventNotFoundException` if absent
- `EventRepository.findById(id)` → Spring Data generates the SQL

**The test for correct layering (interview question):** *could I swap the HTTP layer for a message queue?* If business rules live in the service, yes. If a controller computes seat availability, no.

**In this project** there's a deliberate sub-layer too: business rules that protect data live **on the entity itself** — `Event.reserveSeats()` throws if not PUBLISHED or seats insufficient (see `Event.java`). The service orchestrates; the entity guards its own invariants. Rich domain model, not anemic.

**Remember:** *controller translates, service decides, repository persists, entity guards its own rules.*

### 5. REST controllers

**What:** Classes that map URLs + HTTP methods to Java methods.

```java
@RestController
@RequestMapping("/api/events")
public class EventController {

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) { ... }
}
```

**The pieces:**
- `@RestController` = `@Controller` + every method's return value becomes the JSON response body (via Jackson)
- `@PathVariable` → piece of the URL (`/events/{id}` → `id=5`); `@RequestParam` → query string (`?city=Mumbai`); `@RequestBody` → parsed JSON request body
- Return a plain DTO → 200. Wrap in `ResponseEntity.status(CREATED)` when you need 201. `@ResponseStatus(NO_CONTENT)` for empty 204s.

**HTTP method conventions (worth reciting):**
| Method | Meaning | Success code | Idempotent? |
|---|---|---|---|
| GET | read, no side effects | 200 | yes |
| POST | create / non-repeatable action | 201 | no |
| PUT | full replace | 200 | yes |
| DELETE | remove | 204 | yes (deleting twice → still gone) |

*Idempotent* will come back in Part C — it's the anchor of the whole retry story.

**In this project:** `EventController` (events + internal reserve/release), `AuthController`, `BookingController` (reads the `Idempotency-Key` header alongside the JWT).

**Remember:** *controller methods are thin — if one has an `if` about business rules, that logic wants to live in the service.*

### 6. DTOs and mappers — why entities never leave the service

**What:** Data Transfer Objects are dedicated request/response shapes; mappers convert entity ↔ DTO.

**Analogy:** A restaurant gives you a **menu**, not a tour of the kitchen. The menu is curated, safe, and in the customer's language. Handing out entities is letting customers wander into the kitchen — they see internal fields, they could touch the stove.

**Three hard reasons:**
1. **Security:** `EventResponse` exposes exactly what the public should see. An entity round-tripped as JSON would expose (and let callers *set*) fields like `availableSeats` or `version`.
2. **Stability:** the DB schema and the API contract evolve independently — rename a column, keep the API field.
3. **Shape:** requests/responses rarely match tables. `BookingResponse` is flat; the `Booking` entity has fields no client should set (total is computed server-side!).

**In this project:** `dto/` package in each service: `CreateEventRequest` (validated input), `EventResponse` (output), `PageResponse<T>` (paged output wrapper), `CreateBookingRequest` (just `eventId` + `quantity` — note the client sends no price). `mapper/EventMapper`, `BookingMapper`, `UserMapper` are static utility classes doing the field shuffling. Requests are Java **records** — immutable, no boilerplate, perfect for in-and-out shapes.

**Remember:** *entities are for the DB, DTOs are for the wire, mappers are the translation desk — and the client never sets prices.*

### 7. Validation — `@Valid` and Bean Validation

**What:** Declarative rules on DTO fields, checked before your code runs.

```java
public record CreateBookingRequest(
    @NotNull @Positive Long eventId,
    @NotNull @Min(1) @Max(10) Integer quantity) {}
```

When the controller parameter is `@Valid @RequestBody CreateBookingRequest`, Spring checks the rules *before* calling your method. Failure throws `MethodArgumentNotValidException` → handled in `GlobalExceptionHandler` → **400** with a `fieldErrors` map telling exactly which field failed. The frontend's `getErrorMessage()` reads that same map and shows the first problem next to the form.

**Analogy:** airport check-in rejects an invalid passport at the counter — before security, before the gate. Each checkpoint rejects as early as it can, cheaply.

**In this project:** all `*Request` records in both services carry constraints; `ReserveSeatsRequest` validates quantity on the internal endpoint too (internal callers still don't get to skip rules).

**Remember:** *validate at the door (`@Valid`), handle the rejection centrally (next concept), and never write an `if (quantity < 1)` by hand in a service.*

### 8. Global exception handling — `@RestControllerAdvice`

**What:** One class that catches every exception from every controller and converts it to a consistent JSON error.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> notFound(EventNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }
    // ... one method per exception family
}
```

Every error in the system — both services — has the same shape:
```json
{ "timestamp": "...", "status": 404, "code": "EVENT_NOT_FOUND", "message": "...", "fieldErrors": null }
```

**Why this design:** controllers stay clean (no try/catch), business code just *throws* domain exceptions (`InsufficientSeatsException`) and the mapping exception→HTTP status lives in exactly one place. The frontend can rely on `error.response.data.message` and `fieldErrors` always existing.

**The status-code mapping used here (memorize it):**
| Situation | Status | Example |
|---|---|---|
| thing doesn't exist | 404 | event id unknown |
| request shape invalid | 400 | missing field, bad JSON |
| business rule refused | 409 Conflict | sold out, not PUBLISHED, duplicate email |
| no/invalid token | 401 | anonymous hitting a protected route |
| authenticated but not allowed | 403 | USER calling an ADMIN endpoint |
| dependency down | 503 | event-service unreachable / circuit open |

Gotcha learned building this: 401 vs 403 — *401 = I don't know who you are; 403 = I know you, and no.*

Another gotcha discovered live: `@PreAuthorize` denials are thrown **inside MVC** (they're 403 `AccessDeniedException`), not in the security filter — so they must be handled in this advice, not only in the security config. Both services do both.

**In this project:** `exception/GlobalExceptionHandler.java` in each service; one `ApiError` record; ~10 tiny domain exceptions that read like sentences (`IdempotencyKeyConflictException`).

**Remember:** *throw domain exceptions from anywhere; one advice class turns them into one error shape; 401=who?, 403=no, 409=rule.*

### 9. JPA and Hibernate — entities, the ORM, dirty checking

**What:** JPA is the *specification*, Hibernate the *implementation* that maps Java objects to database rows. You annotate a class; Hibernate generates the SQL.

```java
@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Enumerated(EnumType.STRING)
    private EventStatus status;
}
```

Read `@Column` as the **DB contract**: name, nullability, length, precision. `@Enumerated(EnumType.STRING)` stores `PUBLISHED` as text — never the default ORDINAL (ints), or reordering the enum silently corrupts every row.

**Lifecycle callbacks:** `@PrePersist void onCreate()` sets `createdAt`; `@PreUpdate` sets `updatedAt` — the entity maintains its own audit fields (`Event.java`, `Booking.java`).

**Dirty checking — the surprise everyone asks about.** Look at `EventService.update()`: setters are called, and there is **no `save()`**. Comment in the code says it: *inside `@Transactional`, dirty checking flushes the UPDATE on commit*. Hibernate tracks each loaded entity's original field values; at commit it diffs current vs original and writes an UPDATE only for changed rows. `save()` is for **new** entities; for loaded ones, committing is enough.

**Analogy:** a security guard (the *persistence context*) notes everyone's belongings when they enter. At exit (commit), only people whose belongings changed are searched again.

**In this project:** `Event`, `Booking`, `User` entities; both services' whole data layer is JPA. Notice `Booking` has `@PrePersist`-generated `bookingReference = "BK-" + UUID…` — receipt numbers belong to the entity, not the caller.

**Remember:** *entity class = table schema; loaded entities are watched — change a field, commit, done; enums as STRING.*

### 10. Spring Data JPA — repositories with no implementation

**What:** You declare an interface; Spring Data writes the query implementation at runtime.

```java
public interface EventRepository extends JpaRepository<Event, Long>,
                                          JpaSpecificationExecutor<Event> { }

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(String key);      // derived query
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

`JpaRepository<Event, Long>` hands you `findById`, `save`, `delete`, `findAll` for free. **Derived queries**: name a method by the field names and Spring parses the method name into SQL — `findByUserIdOrderByCreatedAtDesc` becomes `SELECT … WHERE user_id=? ORDER BY created_at DESC`. Great for fixed queries; the moment filters become *optional combinations*, you outgrow it (next concept: Specifications).

**In this project:** three repository interfaces, each under 15 lines, zero SQL written for them. The bulk loader (`BulkEventSeeder`) deliberately uses `JdbcTemplate` instead — see concept 32.

**Remember:** *interface in, queries out; method-name magic for fixed queries, Specifications for optional ones.*

### 11. Transactions — `@Transactional`

**What:** A group of DB operations that all succeed together or all leave no trace.

**Analogy:** UPI bank transfer — debit your account, credit the shop. Nobody accepts "debit done, credit failed." All-or-nothing is the whole point of money movement, and of a reservation.

**Mechanics (the part interviews probe):** `@Transactional` works by **proxy**. Spring wraps your service object in a proxy; a call to `reserveSeats()` actually calls the proxy, which starts the DB transaction, calls the real method, and commits if it returned normally / **rolls back if it threw a RuntimeException**. Consequences:
- `@Transactional` only works on calls **from outside** — a method calling its own `this.otherTransactionalMethod()` bypasses the proxy (no new transaction!). The classic trap.
- Checked exceptions don't roll back by default; runtime ones do.
- `@Transactional(readOnly = true)` hints Hibernate to skip dirty-checking snapshots — cheaper reads. Used on every read path in `EventService`.

**In this project — the most important transaction in the system** (`EventService.reserveSeats`):
```java
@Transactional
public EventResponse reserveSeats(Long id, int quantity) {
    Event event = getEntity(id);      // load (with version)
    event.reserveSeats(quantity);     // rules + change field
    return EventMapper.toResponse(event);   // commit → dirty-check UPDATE ... WHERE version = ?
}
```
Read-check-write inside one transaction + `@Version` (concept 12) = the overbooking defense.

**And the rule stated in `BookingService`'s header comment:** remote HTTP calls happen **OUTSIDE** local transactions. Holding a DB connection open across a 5-second network call starves the pool — the pool has only ~5-10 connections and they'd all be parked waiting on the network.

**Remember:** *proxy intercepts the call, commits on return, rolls back on runtime exception; never a network call inside; readOnly for reads.*

### 12. Optimistic locking — `@Version` (the star concept)

**What:** A version number on each row makes concurrent updates detect each other; the loser's update affects 0 rows and rolls back.

**Analogy (the Indian classic):** IRCTC Tatkal at 10:00:00. Thousands click Book on the last berth. The system doesn't lock the page while each person thinks — everyone proceeds *optimistically*, and exactly one click wins; everyone else gets "not available". Nobody's money is half-deducted.

**Mechanics:**
```java
@Version
private Long version;
```
Hibernate reads the row (version=7), you modify it, and the commit-time UPDATE becomes:
```sql
UPDATE events SET available_seats = 9, version = 8
WHERE id = 5 AND version = 7;
```
Two racing transactions both loaded version 7. First one updates → version becomes 8. Second one's `WHERE version = 7` matches **0 rows** → Spring throws `OptimisticLockingFailureException` → its whole transaction rolls back → user sees 409. Nobody ever writes on top of someone else's change.

**Optimistic vs pessimistic (interview duo):**
- *Optimistic* (`@Version`): assume conflicts are rare, detect at write, loser retries/fails. No locks held → high throughput. Loses badly if conflicts are constant (everyone retries forever).
- *Pessimistic* (`SELECT … FOR UPDATE`): lock the row on read, others block. Safe under heavy contention on the *same row*, but every request pays the lock cost.

Here: most events never reach the last seat — conflicts are rare — so optimistic wins. The one hot row (a sold-out event) produces a few 409s, which is the correct answer anyway.

**Verified, not theorized:** `EventReserveConcurrencyTest` fires 20 threads at 1 remaining seat → `1×201, 19×409`, `availableSeats` never negative.

**In this project:** `Event.version` (`Event.java` with a comment block explaining exactly this), `EventService.reserveSeats/releaseSeats`, the concurrency test.

**Remember:** *"everyone reads version 7, UPDATE carries WHERE version=7, one wins, the rest roll back — Tatkal."*

### 13. Dynamic filters (Specifications) and pagination (Pageable)

**What:** Compose a query from whichever filters arrived, plus LIMIT/OFFSET, without writing one method per combination.

**Problem:** `?category=TECH&city=Mumbai`, `?city=`, `?search=java&category=` — that's already a combinatorial explosion of derived-query methods.

**Solution — `Specification`:** a lambda that receives the query root, and builds predicates:
```java
private static Specification<Event> byFilters(Category category, String city, String search, EventStatus status) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (status != null)   predicates.add(cb.equal(root.get("status"), status));
        if (category != null) predicates.add(cb.equal(root.get("category"), category));
        if (city != null && !city.isBlank())
            predicates.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
        ...
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```
Each present parameter adds one condition; absent ones add nothing. One code path for all combinations. The repository gains this power by extending `JpaSpecificationExecutor<Event>` — that's where `findAll(spec, pageable)` comes from.

**Analogy:** Subway counter. One sandwich pipeline; you say what you want included, skip what you don't. You don't need a pre-defined menu item per combination.

**Pageable:** the controller just declares `@PageableDefault(size = 20, sort = "startTime") Pageable pageable` and Spring resolves `?page=0&size=20&sort=startTime,asc` from the query string. The response is wrapped in `PageResponse<T>` = `{content, page, size, totalElements, totalPages}` — the exact shape the frontend `Pagination` component renders. Deep paging note: OFFSET gets slower the deeper you go (must skip rows); fine at this scale, keyset pagination is the textbook next step.

**One performance detail that bit us (see `perf-notes.md`):** the spec compares `lower(city)` — so the index had to be an **expression index** on `lower(city)` (concept 29) or it would never be used. The query builder and the index must agree on the expression.

**In this project:** `EventService.byFilters()` + `EventController.listPublic/listAdmin` + `PageResponse`.

**Remember:** *Specification = build WHERE clauses à la carte; Pageable = LIMIT/OFFSET from the query string; index must match the exact expression.*

---

## Part B — Security

### 14. Authentication vs Authorization

**What:** Authentication = *who are you?* Authorization = *are you allowed?*

**Analogy:** Office building. Authentication = badge scan at the gate (identity). Authorization = the accounts floor rejects you unless you're Finance (permission). You can be authenticated (valid badge) and still unauthorized (wrong floor) — that's 401 vs 403 from concept 8.

**In this project:** authentication = JWT validation in `JwtAuthFilter`; authorization happens in two places — URL rules in `SecurityConfig` (`/api/auth/**` public, rest authenticated) and **method** rules `@PreAuthorize("hasRole('ADMIN')")` on mutating endpoints. Fine-grained ownership (`is this booking YOURS?`) is a business rule, checked in `BookingService.checkOwnership` — not everything fits role annotations.

**Remember:** *401 = who?, 403 = no; roles for classes of users, ownership checks in the service.*

### 15. The Spring Security filter chain

**What:** Incoming requests pass through an ordered chain of servlet filters before reaching any controller; security is a set of those filters.

```
request → [Timing filter] → [Csrf] → [JwtAuthFilter] → [authorize rules] → controller
```

**How it works here:**
1. `JwtAuthFilter` (our code) runs before the standard auth position, parses the Bearer token, and — if valid — puts identity into the **SecurityContext**: a `UsernamePasswordAuthenticationToken(userId, null, [ROLE_x])`. The *principal* is literally the userId Long.
2. The `authorizeHttpRequests` rules then check that context: no auth + protected path → 401 via the entry point.
3. Controllers read identity declaratively: `@AuthenticationPrincipal Long userId` — that's the same Long placed by the filter.
4. `SessionCreationPolicy.STATELESS` — no HTTP session is ever created. Every request re-authenticates from its token.

**Analogy:** airport: boarding pass check (identity → get a wristband), then each gate reads the wristband. Stateless = the wristband is re-scanned everywhere; nothing is remembered between visits.

**In this project:** two `SecurityConfig` classes — booking-service (JWT for everything except `/api/auth/**`) and event-service (public GETs, ADMIN mutations, API-key-protected internals, JWT for the rest).

**Remember:** *filters run before controllers; our filter's only job = valid token in → userId+role into the SecurityContext; stateless = nothing remembered between requests.*

### 16. Servlet filters — `OncePerRequestFilter`

**What:** Cross-cutting code that wraps every request/response, before and after the controller.

```java
public class RequestTimingFilter extends OncePerRequestFilter {
    protected void doFilterInternal(req, res, chain) {
        long start = System.nanoTime();
        chain.doFilter(req, res);                          // everything downstream, incl. controller
        log.info("{} {} -> {} [{} ms]", method, uri, status, elapsed);
    }
}
```

`OncePerRequestFilter` guarantees one execution per request (dispatches could otherwise double-run a plain Filter). This pattern is how MIV 2's performance decomposition was possible: one line per request, always on, showing exactly where time went (it revealed endpoint wall-time ≠ DB time — network + payload dominated).

**Security rule learned here:** filters must **never log headers or bodies** — that's where tokens and passwords travel. Log method, path, status, duration. Nothing else.

**In this project:** `RequestTimingFilter` in both services; `JwtAuthFilter` and `InternalApiKeyFilter` are the same pattern with security jobs.

**Remember:** *filter = wrap the whole request; once per request; log the line, never the payload.*

### 17. JWT — JSON Web Tokens

**What:** A signed, self-describing credential: server-issued, server-verifiable, no storage needed in between.

**Structure — three Base64 parts joined by dots:**
```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIzIiwicm9sZSI6IkFETUlOIiwiZXhwIjoxNz... . SflKxwRJSMeKKF2QT4f...
       header                    claims (payload)                        signature
     {alg: HS256}           {sub, role, email, exp, iat}          HMAC(secret, header.payload)
```
- **Header**: which algorithm.
- **Claims**: the facts — here `sub` = userId, plus `role`, `email`, `iat` (issued-at), `exp` (expiry).
- **Signature**: HMAC-SHA256 of the first two parts with a shared secret. Change one character of the claims → signature no longer matches → invalid. **Anyone can read the claims (it's just Base64 — never put secrets in them); nobody can change them without invalidating.**

**Analogy:** movie ticket. Issued once at the counter (login), has your details printed on it (claims), has a hologram (signature) the door staff verify against the theater's own hologram key (shared secret), and shows the showtime (expiry). Door staff don't phone the counter to check each ticket — the hologram is proof enough. That phone-call-free property is **statelessness**.

**Sessions vs JWT (the standard interview comparison):**
| | Session + cookie | JWT + header |
|---|---|---|
| State stored | server (memory/DB) | nowhere — claims travel with the token |
| Scaling | needs shared session store across instances | any instance verifies with the secret |
| Revocation | delete the session, instant | hard — must wait for expiry (or keep a blocklist) |
| Mobile/API clients | cookie plumbing | natural |

**Issue & validate in this project** (`security/JwtService.java`, both services — booking issues, both validate):
```java
// issue (booking-service only)
Jwts.builder()
    .subject(user.getId().toString())
    .claim("email", user.getEmail())
    .claim("role", user.getRole().name())
    .issuedAt(...).expiration(now + minutes)
    .signWith(key)          // key = Keys.hmacShaKeyFor(secret bytes)
    .compact();

// validate (both)
Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
```
`parse` throws on: expired, tampered, wrong key — caught in `JwtAuthFilter`, request stays anonymous → 401. The **principal is just the userId from `sub`** — no DB lookup per request, which is the entire point.

**Known trade-off (say it in interviews):** HS256 = one shared secret both services must hold; the cleaner design is RS256 (booking signs with a *private* key, event-service verifies with the *public* one — no secret sharing). Also: JWTs can't be individually revoked before expiry. Both noted as limitations in `architecture.md` §15.

**Remember:** *header.claims.signature; readable but unforgeable; sub=userId, exp=expiry; stateless = verify without asking anyone.*

### 18. Password storage — BCrypt and salts

**What:** Passwords are never stored, never logged — only a slow, salted, one-way hash.

**Why BCrypt specifically:** it's an *expensive* hash on purpose. One BCrypt check ≈ 100 ms of CPU. For you logging in: irrelevant. For an attacker trying a billion guesses: a billion × 100 ms = years. Fast hashes (SHA-256 alone, MD5) would let them brute-force at GPU speed. Also: **BCrypt salts automatically** — a random salt is baked into every hash, so two users with password `demo1234` get completely different stored hashes, killing rainbow tables.

**Analogy:** salt = a unique random masala ground into each password before cooking. Same dish, different kitchens, unrecognizably different output — and the masala mix is written on the packet (stored with the hash), so verification can repeat the recipe.

**In this project:** `SecurityConfig.passwordEncoder()` returns `PasswordEncoderFactories.createDelegatingPasswordEncoder()` — it stores the algorithm name *inline*: `{bcrypt}$2a$10$...`. Why delegate? Tomorrow you switch to argon2; new hashes get `{argon2}...`, old ones still verify as `{bcrypt}...` and can migrate on next login. One line of future-proofing.

**Flow:** register → `encoder.encode(raw)` stored. Login → `AuthenticationManager` (a `DaoAuthenticationProvider` wired with `CustomUserDetailsService` + the encoder) loads the user by email and runs `matches(raw, stored)` — constant-time comparison inside. Login never sees plaintext after the request object is gone.

**Remember:** *slow + salted + one-way; `{bcrypt}` prefix = delegating encoder = free future upgrades; plaintext passwords appear exactly nowhere.*

### 19. Method security and CSRF

**`@EnableMethodSecurity` + `@PreAuthorize`:**
```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<EventResponse> create(...) { ... }
```
Role comes from the authorities the `JwtAuthFilter` placed (`ROLE_` + token's role claim). URL rules guard *areas*; method rules guard *operations* — and live right next to the operation, impossible to forget when copying the method.

**CSRF — and why it's disabled here:** CSRF attacks ride **cookies**: a malicious site makes your browser send its cookies to your bank silently. This API uses **no cookies** — the JWT travels in an `Authorization` header, which a cross-site page cannot read or attach. No cookie surface → nothing to forge → `csrf(disable)` is the *correct* stateless-API setting, not laziness.

**In this project:** `@EnableMethodSecurity` on event-service; all mutations annotated; ownership checks (this booking is yours) in `BookingService` since roles can't express "owner".

**Remember:** *URL rules for areas, `@PreAuthorize` for operations; no cookies → no CSRF surface.*

---

## Part C — Microservices & distributed systems

### 20. Microservices vs the monolith

**What:** Monolith = one deployable containing everything. Microservices = several independently deployable services, each owning one business capability.

**The honest trade-off table (interviewers love the honesty):**
| | Monolith | Microservices |
|---|---|---|
| local calls | method calls — free, transactional | network calls — can fail, be slow, need resilience (Part C!) |
| data | one transaction across tables | cross-service consistency needs sagas/idempotency |
| deployment | ship everything to change anything | ship only the changed service |
| scaling | whole app scales | scale only the hot service |
| ops complexity | low | service discovery, tracing, more moving parts |

The killer line: **a distributed monolith** — services split *physically* but sharing a database — combines the drawbacks of both. Which is why the next concept is non-negotiable.

**In this project:** exactly two services (not fifteen — right-sized): event-service owns "what's happening and how many seats", booking-service owns "who you are and what you booked". The split is along the seam that matters: booking is high-write and needs identity; browsing is high-read.

**Remember:** *network calls can fail in a way method calls can't — everything else in Part C exists because of that one sentence.*

### 21. Database-per-service

**What:** Each service's data is private; the only door is its API.

**In this project:** Neon hosts two databases — `eventdb`, `bookingdb`. No credentials, JDBC URL, or SQL of one ever references the other. `booking-service` knows an event only as an **ID** and as whatever `GET`/`reserve` responses tell it.

**Why (the three real reasons):**
1. **Independent evolution** — event-service adds a column; booking-service doesn't know or care. Schema coupling is the tightest coupling there is.
2. **The right model per concern** — one service might want Postgres, another a document store.
3. **Encapsulation is enforced physically** — nobody can "just query" another service's tables under deadline pressure. The architecture survives busy weeks.

**The consequences you must be able to recite:** no foreign keys across services (`event_id` is a plain column in `bookings` — noted in the entity); no cross-service JOINs (booking-service *snapshots* the event name/price — concept 33); and cross-service writes can't be one transaction → **compensation** (concept 23) and **idempotency** (concept 24).

**Analogy:** two departments, each with its own register, exchanging formal letters (APIs). Fast? No — letters take time (network). But each department can reorganize its own files without asking anyone, and one department's filing mistake can't corrupt the other's register.

**Remember:** *IDs travel, data doesn't; no FK, no JOIN, no shared transaction across services — which is why sagas and snapshots exist.*

### 22. Calling another service: `RestClient` + timeouts

**What:** `RestClient` is Spring's modern synchronous HTTP client; timeouts are not optional accessories.

```java
// RestClientConfig (booking-service)
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(2));   // TCP connect must succeed in 2s
factory.setReadTimeout(Duration.ofSeconds(5));      // response must arrive in 5s
```

**Why both:** *connect* covers "is the server even reachable" (refused/unreachable); *read* covers "it answered the door but never finished the sentence" (slow query, GC pause, hung thread). Without timeouts a stuck downstream ties up your servlet thread *forever* — enough stuck threads and your own service is down while being perfectly healthy. **The dependency's outage becomes your outage.**

**Calling with error translation** (`EventServiceClient.doReserve`):
```java
restClient.post()
    .uri("/api/events/{id}/reserve", eventId)
    .body(Map.of("quantity", quantity))
    .retrieve()
    .onStatus(s -> s.value() == 404, (req,res) -> { throw new EventNotFoundException(eventId); })
    .onStatus(s -> s.value() == 409, (req,res) -> { /* parse body, throw InsufficientSeats or InvalidEventState */ })
    .body(EventDetails.class);
```
`onStatus` intercepts error statuses **before** they become generic exceptions, so downstream 404/409 become *our* domain exceptions and flow through the same `GlobalExceptionHandler` as everything else. The response is read into a small record (`EventDetails`) — not the whole `EventResponse` — because booking-service needs only `name`, `ticketPrice`, `availableSeats`. Consume the minimum.

**In this project:** all cross-service HTTP lives in ONE class (`EventServiceClient`) — one place to find, configure, wrap, and debug every remote interaction.

**Remember:** *timeouts are the seatbelt; onStatus turns their errors into your vocabulary; one client class per dependency.*

### 23. Partial failure & compensation — the hand-made saga

**What:** When a multi-service operation fails halfway, you undo the steps that succeeded. The undo sequence is a *saga* — compensation instead of a distributed transaction.

**The impossible alternative:** 2PC/XA would lock rows in eventdb while asking bookingdb "ready to commit?", then commit both. It works, but couples availability (either DB hiccuping blocks both) and is operationally notorious. At this scale, explicit compensation is simpler *and more debuggable*.

**In this project** (`BookingService.create`, read it with this lens):

```
reserve seats (event-service)  →  save booking (local DB)
        │ success, then local save throws?
        └─ catch: releaseSeats(eventId, quantity)   // undo step 1
           then rethrow → user gets 500, system left exactly as before
```
The `catch (RuntimeException e)` block *is* the saga's compensation step. Requirements that make it sound:
1. **Remote call outside any local transaction** (concept 11) — otherwise you'd hold a DB connection across the network and the ordering gets impossible anyway.
2. **Compensable operations** — `/release` exists precisely as the inverse of `/reserve` (event entity even caps the release at capacity).
3. **Ordering** — remote-first on cancel, too: `cancel()` releases seats *before* marking CANCELLED locally. If release fails, booking stays CONFIRMED and retryable — you never destroy the local record while the remote side still holds sold seats. *Fail in the direction that keeps the truth visible.*

**Analogy:** shopping with cash at three shops for one outfit. Card blocked at shop 3? You return items to shops 1-2 (compensate). You don't ask all shops to "hold a joint transaction" while you walk around (2PC).

**Remember:** *saga = do steps, undo in reverse on failure; compensate remote-first on cancel; no network call inside a transaction.*

### 24. Idempotency — same request twice, one effect

**What:** An operation is idempotent if doing it N times = doing it once. Since networks *will* deliver the same request twice (retries, double-clicks, lost responses retried by the user), state-changing POSTs need it engineered in.

**Analogy (the Indian classic):** UPI. Payment fails at the last moment, app says "retry with same reference number" — retrying a *new* payment would double-pay; retrying the same reference returns "already done". The reference number **is** the idempotency key.

**The three layers in this project** (`BookingService.create` + `Booking.idempotencyKey`):

1. **Client generates the key** — frontend's booking call sends `Idempotency-Key: <crypto.randomUUID()>` — one UUID per booking *click*, reused across retries of that click.
2. **App fast path** — `findByIdempotencyKey(key)` before doing anything; hit → return the original booking (with `replayed=true` so the caller knows), book nothing.
3. **DB unique constraint** — the last line of defense. Two same-key requests can pass the fast path simultaneously (that's the race window); both reserve seats, both try to INSERT, **one insert wins**, the loser gets `DataIntegrityViolationException` → catches it, **releases its now-unneeded reserved seats** (compensation again!), fetches and returns the winner's booking. Net effect: exactly one booking, seats correct, both callers get 200s.

**The distinction to internalize:** GET/PUT/DELETE are idempotent *by design*. POST is not — idempotency-keys are how you grant a POST idempotent *behavior for a specific retry*, without making the operation global-once (different key = genuinely different intended booking).

**In this project:** `Idempotency-Key` header on `POST /api/bookings`; `uk_booking_idempotency_key` constraint on the table; `IdempotencyKeyConflictException` when a key shows up with a *different* user (keys are user-scoped by check).

**Remember:** *"same key = same booking; app check for speed, DB constraint for the race; loser releases its seats."*

### 25. Retry — and why almost nothing is retried

**What:** Automatically re-attempt a failed call. The dangerous one — done carelessly it duplicates side effects.

**The question that decides everything: did the request reach the server?**
- **Connection refused** (TCP connect never happened) → request provably never arrived → retry is **always safe**, even for POST /reserve.
- **Read timeout** (connected, no answer in 5 s) → the server *may have succeeded* and only the response was lost → a blind retry could double-book → **do not blind-retry**; that case belongs to the idempotency key (concept 24).
- **Business 404/409** → the server answered; retrying the same wrong thing is pointless.

**In this project** (`ResilienceConfig` + `EventServiceRetryPolicy`): retry = 3 attempts, 150 ms apart, and `retryOnException` accepts **only** `ResourceAccessException` whose cause is `ConnectException` — literally "isDefinitelyNotDelivered". Everything else falls through untouched:

```java
public static boolean isDefinitelyNotDelivered(Throwable t) {
    return t instanceof ResourceAccessException && t.getCause() instanceof ConnectException;
}
```

**Analogy:** knocking on a friend's door. Doorbell broken / nobody home vs door never knocked. Only re-knock when you're certain the first knock never landed — if they might have heard you and started making chai, knocking again doesn't order a second chai (but for money operations, assume it might).

**Remember:** *retry only what provably never arrived; connect-refused yes, timeout no (that's idempotency's job), business errors never.*

### 26. Circuit breaker

**What:** Track recent failures to a dependency; past a threshold, stop calling it and fail instantly — then probe periodically to see if it recovered.

**Analogy:** friend whose phone has been dead all morning. First few calls: full rings (real attempts, wasted time). After enough, you stop calling and immediately say "unreachable" when someone asks (fail fast). Every 10 minutes you try once (probe). One answered call → back to normal. You don't spend the afternoon ringing a dead phone — and *you* stay free to do other things. That's the pattern protecting the **caller's** threads, not fixing the callee.

**Three states:**
```
        failure rate ≥ 50% over last 10 calls (min 5)
CLOSED ────────────────────────────────▶ OPEN
   ▲                                       │ wait 10 s
   │ 3 half-open probes all succeed        ▼
   └──────────────────── HALF_OPEN ◀───────┘   (3 probes; any failure → OPEN)
```

**In this project** (`ResilienceConfig.eventServiceCircuitBreaker`): window 10, min calls 5, threshold 50%, 10 s open wait, 3 probes, `automaticTransitionFromOpenToHalfOpenEnabled`. Two deliberate subtleties:

- **`ignoreExceptions` for 404/409 business errors** — event-service answering "sold out" is a *healthy* event-service. A rush on a sold-out concert must never trip infrastructure protection. Only transport trouble counts.
- **Retry inside the breaker** (see below) — not the other way around.

**Composition order — the bit worth saying slowly:**
```java
CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(connectRetry, call))
// = circuitBreaker( retry( call ) )
```
Retry **inside** means one logical call (with up to 3 attempts) = **one** sample in the breaker's window. Retry outside would count each attempt as a sample and trip the circuit 3× too fast on a transient blip. Order: timeouts (innermost) → retry → breaker (outermost) is the canonical stack, and this code is that stack, written out.

**Measured reality (perf-notes):** failing calls cost ~330 ms each (3 connect attempts + backoff). Circuit OPEN: **3–4 ms** flat, `CallNotPermittedException` → caught in `EventServiceClient` → user gets 503 instantly. After event-service restart + 10 s, first probe succeeded → CLOSED → 201s again, with **no restart of booking-service**.

**In this project:** config in `ResilienceConfig`, wrapping in `EventServiceClient.protectedCall()`, `CallNotPermittedException` translated to `EventServiceUnavailableException` (503).

**Remember:** *CLOSED→OPEN on ≥50% failures over 10 calls, OPEN = fail in 3 ms, HALF_OPEN = 3 probes; business errors never count; retry inside so one logical call = one sample.*

### 27. Connection pooling — HikariCP

**What:** A fixed pool of open DB connections borrowed per operation and returned — because opening a TCP+auth connection costs 10s of ms, and requests need one for ~1 ms of actual SQL.

**Analogy:** office cab shuttle. Five cabs loop all day. A request borrows a cab, does its DB trip, returns it. Alternative — a new cab bought per trip (new connection per query) — would bankrupt you at rush hour.

**Mechanics:** Boot's default pool is HikariCP. Defaults: max 10 connections. Key settings here: pool sized small (5, min-idle 2) for a laptop + Neon, and `keepalive-time: 240000` — **the Neon-specific lesson**: serverless Postgres scales to zero / kills idle connections around 5 minutes; Hikari pings each idle connection every 4 minutes so you never borrow a dead one. (Related: Neon's *pooled* endpoint is PgBouncer in transaction mode — for serverless *functions*. A long-lived Spring app runs its own pool, so the **direct/non-pooled** connection string is correct here.)

**The transaction connection (ties Part A together):** a `@Transactional` method borrows a connection at entry and returns it at commit. Now the concept-11 rule has a number attached: a 5-second remote call inside a transaction holds 1 of 5 pool slots for 5 seconds → **5 concurrent such requests = the entire pool = every other request queues**. That's why remote calls stay outside transactions.

**Remember:** *borrow, use, return; pool small; keepalive 4 min for Neon's scale-to-zero; a transaction = a borrowed connection the whole time.*

### 28. Service-to-service trust — the internal API key

**What:** `/reserve` and `/release` are powerful (they change sold inventory) but must never be user-callable — so they authenticate with a different credential than users do.

**Two deliberately separate trust paths:**
- Users → JWT (`Authorization: Bearer …`) — identity of a person.
- booking-service → event-service → `X-Internal-Api-Key` header — identity of a *service*.

booking-service does **not** forward the user's JWT on reserve calls: the decision to decrement seats is made by the *service*, and an internal endpoint shouldn't care which user triggered it. Mixing the two (forwarding user tokens service-to-service) couples every downstream endpoint to user-permission logic — a classic microservices anti-pattern.

**In this project:** `InternalApiKeyFilter` (event-service) — runs after `JwtAuthFilter`, matches `/api/events/*/reserve|release` POST paths with an `AntPathMatcher`, and 401s unless the header equals the configured `app.internal-api-key`. The SecurityConfig permits those paths to "anonymous" *because this filter is the actual gate*.

Honest limitation (documented): plain `.equals()` is not constant-time comparison; production-grade would use `MessageDigest.isEqual`. And a static shared secret has no rotation/per-service identity — mTLS or a token service would be the grown-up version.

**Remember:** *people present JWTs, services present API keys; never forward user identity to internal endpoints.*

---

## Part D — Database & performance

### 29. Indexes — B-tree, composite, expression

**What:** A sorted data structure Postgres maintains so queries can *find* rows without reading the whole table.

**Without index:** `Seq Scan` — read all 50,000 rows, keep the 20 that match. **With index:** descend a B-tree (height ~3-4 for millions of rows) → touch ~20 rows. That's the measured 10.4 ms → 0.23 ms.

**Analogy:** book index. "Photosynthesis, page 214" vs reading the book cover to cover looking for the word. The index costs pages of its own (disk, and a slight tax on every INSERT/UPDATE — the seeder loads *without* indexes for speed, then they're added).

**The three indexes in this project and the lesson each teaches:**

1. **Composite + order matters:** `idx_events_city_start_time ON events (lower(city), start_time)`.
   A composite index sorts like a phone directory sorted by *(surname, firstname)*: great for "surname = Patel" (or surname + firstname), useless for "firstname = Priya" alone. Rule: **equality columns first, range/sort columns after.** Bonus here: rows come out already in `start_time` order → the query's whole `Sort` node vanished from the plan.
2. **Expression index:** the code queries `lower(city) = lower('Mumbai')` — a plain `(city, …)` index indexes the *raw* value and would **never be used**; `lower(city)` needed its own index because the query's expression *is* `lower(city)`. Lesson: *the index must match the exact expression the query generates* — check what your ORM produces before indexing.
3. **Composite for filter+sort:** `idx_bookings_user_created ON bookings (user_id, created_at)` — serves "this user's bookings, newest first" as a walk down an already-sorted structure.

**Rules of thumb:** index the columns of your WHERE/JOIN/ORDER BY; every FK-ish column you filter by (`user_id`); don't index everything (write tax); always `ANALYZE` after big loads so the planner has fresh statistics.

**Remember:** *equality-first composite order; index the expression you actually query (`lower(city)`); index = sorted book index, not a faster table-read.*

### 30. `EXPLAIN ANALYZE` — reading a query plan

**What:** Show what Postgres *actually did* — which nodes, which index, how many rows, how many pages, real milliseconds. The "measure first" tool.

**The reading ritual (in order):**
1. **Bottom-up node tree:** each node's output feeds its parent. `Limit ← Sort ← Scan` means scan everything, sort it, take 20.
2. **Seq Scan on a big table** in a filtered query = suspicious (missing/unused index).
3. **`Rows Removed by Filter`** = rows read and thrown away. 45,009 removed to find 5,005 = the smoking gun.
4. **Buffers** (`shared hit/read`) = pages touched — the real cost currency, more stable than ms.
5. **`actual time`** per node — where the milliseconds went.
6. **Row estimates vs actual** — wildly off = stale statistics → `ANALYZE`.

**The before/after from this project** (perf-notes has both plans):
```
BEFORE: Limit ← Sort ← Seq Scan on events  (10.4 ms, 1,283 buffer pages, 45,009 rows removed)
AFTER:  Limit ← Index Scan using idx_events_city_start_time  (0.23 ms, 23 pages, Sort node GONE)
```

**The MIV 2 lesson that outranks all of this:** after the 45× DB win, the *endpoint* stayed ~175 ms — because WAN round-trips to Singapore and JSON serialization dominated, not the DB. **Two "obvious" fixes (index, rewrite) were not what the endpoint needed; the measurement said network and payload.** Measure first, always — this story is gold in interviews precisely because the index "didn't work" at the wall-clock level.

**In this project:** both plans, table stats, and the honest decomposition are in `docs/perf-notes.md` §Finding 1-2.

**Remember:** *read the plan bottom-up; Seq Scan + Rows Removed = smoking gun; DB time ≠ endpoint time — decompose before optimizing.*

### 31. The N+1 problem

**What:** 1 query for a list, then N queries — one per row — to fetch each row's related data. 100 bookings → 101 queries, each paying network+parse.

**Classic trigger:** JPA lazy `@ManyToOne`/`@OneToMany` accessed per entity while serializing. Symptoms: log full of repeated SELECTs, latency scales with list length.

**In this project: verified absent — and the reason is architectural.** Booking stores the event name/price as **snapshot columns** (concept 33) instead of a relation to an Event entity (impossible anyway — different database, concept 21). So my-bookings is exactly **one** SELECT, watched live with `show-sql: true` during MIV 2. No relations on the hot path → N+1 *cannot occur there*.

**The fix ladder, in preference order (recite it):**
1. **DTO projection** — select exactly the columns the response needs (`SELECT name, price FROM …`), no entities loaded at all.
2. **`@EntityGraph` / JOIN FETCH** — fetch the relation in the one query (`LEFT JOIN` under the hood).
3. **`@BatchSize`** — turn N queries into N/batch IN-clauses. Last resort.
4. ~~`fetch = EAGER`~~ — **never the answer**: trades a *visible* N+1 for permanent hidden over-fetching on every query in the app, including the ones that don't need the relation.

**Remember:** *list + per-row relation access = N+1; fix with projection or JOIN FETCH; EAGER is the trap answer.*

### 32. Bulk inserts — `JdbcTemplate` vs JPA `saveAll`

**What:** For loading 250,000 synthetic rows, plain JDBC batches beat JPA by an order of magnitude — and knowing *why* is the point.

**Why `saveAll` is slow here (ties back to concept 9):** the EntityManager must track every persisted entity for **dirty checking** — it snapshots each one's state at load/persist for the commit-time diff. 200k entities = 200k tracked snapshots + per-entity INSERT events + flush-time bookkeeping, all worthless for synthetic rows nobody will ever modify.

**`JdbcTemplate.batchUpdate`** sends multi-row INSERTs in wire-protocol batches — 500-1,000 statements per network round trip, no entity tracking at all.

**Numbers:** 50k events in 13.1 s, 200k bookings in 50.7 s (~3,800-3,950 rows/s) over a WAN to Singapore.

**The architectural detail worth copying:** `BulkBookingSeeder` doesn't read eventdb. It **pages event references through event-service's public API** (40 pages × 500) — bulk tooling respects the database-per-service boundary too. (It also hashes one bcrypt password once for 50 users — BCrypt's 100 ms × 50 would've added 5 s for no reason.)

**Also note:** seeders run under `@Profile("bulk")` — `--spring.profiles.active=bulk` — so they can never execute in normal runs or tests (concept 35's bigger sibling, config).

**Remember:** *JPA tracks what it loads; for write-only bulk loads, skip the tracking — JDBC batches; seeders behind a profile.*

### 33. Denormalization & snapshots — the booking receipt

**What:** `bookings` stores `event_name` and `price_per_ticket` *copied from* event-service at booking time, even though that "duplicates" data.

**Why:** (1) the JOIN is *impossible* — different database (concept 21); (2) more fundamentally, **a receipt must be immutable**. If the organizer renames "Sufi Night" → "Qawwali Evening" or raises the price tomorrow, your existing booking should still show what you actually booked, at what you actually paid. The "duplication" isn't a bug — the two tables store *different facts*: events stores current truth, bookings stores historical truth.

**Analogy:** the printed bill in your wallet vs the restaurant's current menu. You'd be alarmed if the bill *changed* when the menu updated.

**In this project:** set in `BookingService.create` from the reserve response; `total_amount` computed server-side (`pricePerTicket × quantity`) — never sent by the client (concept 6).

**Remember:** *snapshot = deliberate denormalization for immutability and decoupling; current state vs historical record are different facts.*

### 34. Money and enums — two small rules

**Money = `BigDecimal`, never `double`.** `0.1 + 0.2` in binary floating point = `0.30000000000000004` — doubles *cannot represent* most decimal values exactly, and money must be exact. `ticket_price` is `NUMERIC(10,2)` in the DB, `BigDecimal` in Java, computed server-side with `multiply`. (Interview bonus: if you must double, count paise as integer — but you don't must.)

**Enums stored as `STRING`.** `@Enumerated(EnumType.STRING)` writes `PUBLISHED`, not `1`. With ORDINAL (the default!), inserting `COMPLETED` before `CANCELLED` in the enum silently renumbers every existing row's meaning. String values survive refactoring; are readable in SQL (`WHERE status='PUBLISHED'` — the index and the spec code both rely on this); cost a few bytes more.

**In this project:** `Event.ticketPrice`, `Booking.pricePerTicket/totalAmount`; `EventStatus`, `Category`, `BookingStatus`, `Role` all STRING-enumerated.

**Remember:** *money in BigDecimal computed server-side; enums as strings, never ordinals.*

---

## Part E — React & the frontend

### 35. Vite, SPAs, and the dev proxy

**What:** Vite is the dev server + bundler. An SPA (single-page app) loads **one** HTML page, then JavaScript renders every "page" client-side — the server never navigates, the router does (concept 41).

**Why Vite:** dev server starts instantly and hot-reloads modules without rebuilding the bundle — edits appear in the browser in milliseconds.

**The dev proxy — the CORS-killer** (`frontend/vite.config.js`):
```js
server: {
  port: 5173,
  proxy: {
    '/api/auth':     { target: 'http://localhost:8082', changeOrigin: true },
    '/api/bookings': { target: 'http://localhost:8082', changeOrigin: true },
    '/api/events':   { target: 'http://localhost:8081', changeOrigin: true },
  },
}
```
The browser only ever talks to `http://localhost:5173` (one origin). When it requests `/api/events`, **Vite's dev server forwards it** to `:8081`. Why: browsers block cross-origin XHRs without CORS headers — two backend origins + a frontend origin = CORS config on both services. With the proxy: **zero CORS in dev**, and the frontend's paths (`/api/...`) stay relative and deployment-agnostic. (In production a reverse proxy/nginx plays the same role.)

**Analogy:** the building receptionist forwards internal calls to whatever floor — visitors dial one number.

**Remember:** *one origin from the browser's view; proxy routes by path prefix; CORS solved by topology, not headers.*

### 36. Components, props, JSX

**What:** A component is a function returning UI (JSX — HTML-looking syntax that compiles to JavaScript). Props are its read-only inputs.

```jsx
function EventCard({ event }) {          // props arrive as one object, destructured
  return (
    <div className="card">
      <h3>{event.name}</h3>
      <p>₹{event.ticketPrice}</p>
    </div>
  );
}
// used:  <EventCard event={event} key={event.id} />
```

**Mental model:** components are **functions of their inputs** — same props in, same JSX out. That single idea is all of React's correctness: UI = f(state), React re-runs f when inputs change and patches the DOM where the output differs (the *reconciliation* that makes the Virtual DOM useful).

**Props flow one way** — parent → child, never mutated by the child (immutable inputs, concept 37's rule). A component needing to *change* something owns **state** instead.

**In this project:** `frontend/src/components/` (`EventCard`, `Pagination`, `Navbar`, `ProtectedRoute`) + `pages/` (page-level components). Pages compose components; components stay generic.

**Remember:** *UI = f(props, state); props are read-only inputs; components compose like functions because they are functions.*

### 37. State — `useState` and one-way data flow

**What:** State is data a component owns and can change; changing it re-runs the component.

```jsx
const [page, setPage] = useState(0);     // [currentValue, setterFunction]
```
**Never** `page = 3` — that mutates a local variable React doesn't know about. `setPage(3)` tells React: "state changed, re-render with the new value." Reads are fresh **per render**; updates are queued and applied together (batched).

**One-way data flow — the loop that is React:**

```
state change → re-render → new JSX → React patches DOM → event handlers call setters → repeat
```
Data flows **down** (props/state → children); events flow **up** (child callbacks → parent setters). In `EventsPage`: `Pagination` (child) receives `page`+`totalPages` as props and *reports* clicks via `onChange={setPage}` — the parent owns the state, the child never touches it.

**In this project** (`EventsPage.jsx` — read it as the canonical example): four state cells — `data` (server response), `loading`, `error`, and `{page, filters}` (UI query state). `searchInput` is separate from `filters` deliberately: the field updates per keystroke, but the *query* only changes on submit (concept 39/40).

**Remember:** *setter, never assignment; data down, events up; one owner per piece of state.*

### 38. Lists and keys

**What:** Rendering arrays uses `.map()`, and every item needs a stable `key`.

```jsx
{data.content.map((event) => (
  <EventCard key={event.id} event={event} />
))}
```

**Why keys:** after a filter/page change, React sees a *different* array and must decide which old items match which new ones. Keys are the identity it matches on. With array-index keys, deleting item 2 makes old item 3 "become" key 2 — React patches the wrong component's DOM, misplacing input state and animations. `event.id` never changes for the same event → perfect key.

**Analogy:** classroom attendance by roll number vs by seat position. Reshuffle seats and seat-based records are chaos; roll numbers survive any reshuffle.

**Remember:** *`.map()` for lists, stable unique `key` per item — id, never array index.*

### 39. `useEffect` — data fetching and cleanup

**What:** `useEffect(fn, deps)` runs `fn` after render **when `deps` changed** — React's escape hatch from pure rendering, used here for API calls.

```jsx
useEffect(() => {
  let cancelled = false;                          // the cleanup pattern
  setLoading(true);
  listEvents({ page, size: PAGE_SIZE, sort: 'startTime,asc', ...filters })
    .then((res) => { if (!cancelled) { setData(res.data); setError(''); } })
    .catch((err) => !cancelled && setError(getErrorMessage(err)))
    .finally(()   => !cancelled && setLoading(false));
  return () => { cancelled = true; };             // runs before the NEXT effect
}, [page, filters]);                              // re-fetch when these change
```

**Why not fetch in the render body:** renders must be pure (they re-run for unrelated reasons — concept 36); effects are the sanctioned side-effect slot.

**The dependency array is the trigger:** page 0→1 or filters changing → effect re-runs → new fetch. Miss a dependency and you serve stale data forever.

**The cleanup flag — the bug this prevents:** user clicks page 2 then quickly page 3. Two fetches race. Without `cancelled`, whichever response arrives **last** wins — possibly page 2's, displayed under page 3's pagination. The cleanup sets `cancelled = true` for the *outdated* effect run, so only the current run can `setData`. (StrictMode dev double-invocation is the same defense.)

**In this project:** this exact pattern (loading + error + cancelled) is used by every fetching page — `EventsPage`, `EventDetailsPage`, `MyBookingsPage`, `AdminEventsPage`.

**Remember:** *effect = (deps changed) → run after render; always the cancelled-flag when two fetches can race; loading/error are state too.*

### 40. Controlled forms

**What:** Form inputs whose values live in React state — React is the single source of truth.

```jsx
const [searchInput, setSearchInput] = useState('');
<input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
```
`value` + `onChange` together = controlled: keystroke → `onChange` → `setSearchInput` → re-render → input shows new value. The DOM element never holds its own state.

**The pattern in `EventsPage.applyFilters` — search-on-submit:**
```jsx
function applyFilters(e) {
  e.preventDefault();                                   // stop the browser's form POST
  setPage(0);                                           // new search → back to page 1
  setFilters({ search: searchInput, category: ..., city: ... });
}
```
`searchInput` updates per keystroke (responsive field) but only on submit does it graduate into `filters` — which is the useEffect dependency. So you type freely; the *query* fires once. (Per-keystroke server search would fire an API call per character — this design avoids that entirely.)

Login/Register pages are controlled too — and they read `error.response.data.fieldErrors` from concept 7's validation responses, closing the loop between backend validation and frontend display.

**Remember:** `value` + `onChange` = controlled; keep per-keystroke state separate from committed query state; `preventDefault` on submit.

### 41. Context and `useAuth` — global state without prop-drilling

**What:** Context broadcasts a value to an entire subtree without threading it through every intermediate component's props.

**The problem it kills:** without it, `App → Layout → Navbar → UserMenu` each pass `user`/`logout` down as props nobody uses except the last — *prop drilling*.

```jsx
const AuthContext = createContext(null);              // the pipe

export function AuthProvider({ children }) {          // broadcaster (wraps App)
  const [user, setUser] = useState(() => {            // lazy init: run once, from localStorage
    try { return JSON.parse(localStorage.getItem('user') || 'null'); }
    catch { return null; }
  });
  // login/register call the API, persist({token, user}), setUser
  // logout clears localStorage + setUser(null)
  const value = { user, isAdmin: user?.role === 'ADMIN', login, register, logout };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {                           // receiver (any component)
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
```

**Two details doing real work:**
- **Lazy `useState` initializer** (a function) — runs once on mount, not on every render: restores the session from localStorage so a refresh doesn't log you out.
- **The context value is an object** `{user, isAdmin, login, register, logout}` — stable API surface; any component gets everything with one `useAuth()` call.

**Analogy:** office PA system vs passing a memo through thirty people. (Context is for *ambient*, rarely-changing data — auth, theme, locale. High-frequency data still belongs to the component that owns it; context changes re-render all consumers.)

**In this project:** `AuthContext.jsx` + `AuthProvider` wrapping `<App />` in `main.jsx`; consumed by `Navbar` (login/logout state), `ProtectedRoute`, and the pages.

**Remember:** *Provider broadcasts, useContext receives; lazy-init state restores the session; auth is the canonical context use-case.*

### 42. React Router and protected routes

**What:** Client-side routing — the URL changes, JavaScript swaps the page component, no server round trip.

```jsx
<Routes>
  <Route path="/" element={<EventsPage />} />
  <Route path="/events/:id" element={<EventDetailsPage />} />   {/* :id = URL param */}
  <Route path="/login" element={<LoginPage />} />
  <Route path="/my-bookings" element={<ProtectedRoute><MyBookingsPage /></ProtectedRoute>} />
  <Route path="/admin/events" element={<ProtectedRoute adminOnly><AdminEventsPage /></ProtectedRoute>} />
  <Route path="*" element={<Navigate to="/" replace />} />      {/* catch-all redirect */}
</Routes>
```
Pages read the param with `useParams()` (`/events/42` → `{id: '42'}`) and navigate with `useNavigate()` (after booking → `/confirmation/${id}`).

**ProtectedRoute — the declarative guard** (read it, it's 15 lines):
```jsx
export default function ProtectedRoute({ adminOnly = false, children }) {
  const { user, isAdmin } = useAuth();
  if (!user)                    return <Navigate to="/login" replace />;
  if (adminOnly && !isAdmin)    return <Navigate to="/" replace />;
  return children;
}
```
Guarding = *rendering a redirect instead of the page*. `replace` keeps the forbidden page out of history (Back doesn't bounce you into a loop).

**The line that must be said (in code reviews and interviews alike):** this is **UX, not security** — it hides pages from honest users. The API is the real boundary: a USER calling `POST /api/events` gets 403 from `@PreAuthorize` regardless of what the UI shows. Frontend guards and backend rules are the same policy implemented twice, by necessity.

**Remember:** *routes are component swaps; guard = redirect instead of render; the backend is the only real security boundary.*

### 43. Axios instance, interceptors, and the token in localStorage

**What:** One configured axios client every API module shares; an interceptor attaches the JWT to every request.

```js
const api = axios.create({ baseURL: '/api' });       // ONE instance, ONE base config

api.interceptors.request.use((config) => {           // runs before every request
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```
No `fetch(url, {headers: {Authorization: …}})` repeated anywhere. Login stores the token; every subsequent call is authenticated automatically; logout removes it and the interceptor simply stops attaching. (A response interceptor is the natural extension — e.g. global 401 → auto-logout.)

**Helpers in the same file:** `getErrorMessage(error)` — the uniform extraction chain `fieldErrors → message → axios message → generic` (matching the backend's `ApiError` shape from concept 8); `cleanParams(params)` — drops `''`/null/undefined so empty filter fields don't become `?city=` hitting enum parsing.

**localStorage — the honest trade-off:** survives refresh (that's the job), synchronous, ~5 MB, **readable by any JavaScript on the page** → XSS steals it. Alternatives: `httpOnly` cookie (JS can't read it; but then CSRF comes back and CORS/cookie plumbing returns) — for this showcase, localStorage + no `dangerouslySetInnerHTML` + React's auto-escaping is the standard pragmatic call. Say the trade-off out loud; pretending it's free is the red flag.

**In this project:** `api/client.js` (instance + helpers), `api/auth.js`, `api/events.js`, `api/bookings.js` (thin wrappers — `bookings.js` generates the `crypto.randomUUID()` idempotency key per booking click, concept 24's client half).

**Remember:** *one instance, interceptors for cross-cutting auth; uniform error extraction; localStorage = pragmatic XSS-fragile storage, know why.*

---

## The capstone — one Book click, end to end

Recite this until it's one breath. It touches every layer of this doc.

1. **Click "Book"** → `EventDetailsPage` calls `bookings.js`, which generates `crypto.randomUUID()` as the **Idempotency-Key** (24) and POSTs `/api/bookings` (36-43).
2. Vite **proxy** forwards to :8082 (35). `JwtAuthFilter` validates the **JWT** from localStorage (17, 15) → SecurityContext holds `userId` + role.
3. `BookingController` → `BookingService.create` (4). **Idempotency fast path**: key seen? return the original (24).
4. `EventServiceClient` wraps the call: **circuit breaker outside, retry inside** (26, 25) → `RestClient` POST with 2 s/5 s timeouts (22) + `X-Internal-Api-Key` (28).
5. event-service: `@Transactional` loads the event, entity rules check PUBLISHED + seats (4, 9), `@Version` optimistic lock guarantees one winner among racers (12), dirty-checked UPDATE (9), commit.
6. Back in booking-service: `Booking` saved with **price/name snapshot**, total computed server-side (33, 34), unique constraint on the key as the last defense (24); on any failure → **compensation** releases the seats (23).
7. 201 → React state updates → confirmation page shows `BK-XXXX` (37-39).
8. If event-service were down the whole time: timeouts → retries → breaker OPENS → **3 ms 503** to the user, no half-bookings anywhere (25, 26).

## Self-test (cover the answers, point into the code)

1. Why can't the booking INSERT and the seat UPDATE be one transaction? *(different databases — 21; what we do instead — 23)*
2. Two users, same event, 1 seat left, simultaneous. Walk both requests to their outcomes. *(12)*
3. User double-clicks Book; network dies after the server saved. What does the retry do? *(24 — key replays original)*
4. Why is connect-refused safe to retry but read-timeout not? *(25)*
5. What exactly makes the circuit open, and what does a request cost once it's open? *(26 — ≥50% of last 10, min 5; ~3 ms)*
6. Why does `EventService.update` have no `save()` call? *(9 — dirty checking)*
7. Why `lower(city)` in the *index*, not `city`? *(29/13 — match the expression the spec generates)*
8. Why does `Bookings` store `event_name`? *(33 — receipts are immutable; JOIN impossible)*
9. 401 vs 403 vs 409 — one example each from this app. *(8, 14)*
10. Why is CSRF disabled? *(19 — no cookies)*
11. What re-renders `EventsPage`, and what prevents a stale fetch from winning? *(39 — deps array + cancelled flag)*
12. Is the admin route guard security? *(42 — UX only; `@PreAuthorize` is the boundary)*
13. Why does the bulk seeder use `JdbcTemplate` and page events *through the API*? *(32 — no dirty-check snapshots; 21 — boundary respected)*
14. `pool size 5` + remote call inside a transaction — do the math. *(27 — 5 stuck requests = pool exhausted)*
15. DB query got 45× faster but the endpoint didn't. Why? *(30 — WAN + JSON payload; measure first)*
