# Event Booking System — MIV Build Plan

The idea is to build this project in three versions.

Each version should be complete enough to use on its own. The first version will cover the main application and core backend/frontend concepts. The second version will focus on failures, debugging and performance. The third version will add testing, packaging and production-style tooling.

The goal is to practice the important parts of Java, Spring Boot, PostgreSQL, REST APIs, microservices and React without adding technologies just for the sake of making the project bigger.

---

# MIV 1 — Core Application

## Summary

MIV 1 is the main working version of the application.

It will have a React frontend, two Spring Boot services and PostgreSQL databases. Users can register, log in, browse events and book tickets. Admin users can manage events.

The important parts in this version are the normal Spring Boot request flow, REST APIs, database work, authentication, authorization, transactions, pagination, idempotency and handling multiple users trying to book the same seats at the same time.

## Basic architecture

```text
React Frontend
      |
      | REST / JSON
      |
  -------------------------
  |                       |
Event Service        Booking Service
  |                       |
PostgreSQL             PostgreSQL
```

The Event Service owns event-related data.

The Booking Service owns booking data and handles users/authentication for now.

The Booking Service talks to the Event Service through REST instead of reading the Event Service database directly.

---

## Event Service

The Event Service manages events and ticket availability.

An event can contain:

```text
id
name
description
category
venue
city
startTime
endTime
ticketPrice
capacity
availableSeats
status
createdAt
updatedAt
version
```

Event statuses:

```text
DRAFT
PUBLISHED
CANCELLED
COMPLETED
```

Main APIs:

```http
POST   /api/events
GET    /api/events
GET    /api/events/{id}
PUT    /api/events/{id}
DELETE /api/events/{id}
```

Only admins can create, update or remove events.

Normal users can browse published events and view event details.

The Spring Boot code should follow a simple structure:

```text
Controller
Service
Repository
Entity
DTO
Mapper
```

Use separate request and response DTOs instead of returning JPA entities directly.

For example:

```text
CreateEventRequest
UpdateEventRequest
EventResponse
```

---

## Event validation

Invalid event data should be rejected before it reaches the database.

Examples:

```text
name cannot be empty
capacity must be greater than zero
ticket price cannot be negative
start time must be in the future
end time must be after start time
available seats cannot be greater than capacity
```

Use normal Bean Validation annotations where they fit:

```java
@NotBlank
@NotNull
@Positive
@Future
```

Validation errors should return a clear API response instead of a generic server error.

---

## Pagination, sorting and filtering

The event API should not return every event in one response.

Support pagination:

```http
GET /api/events?page=0&size=20
```

Sorting:

```http
GET /api/events?page=0&size=20&sort=startTime,asc
```

And basic filtering/search:

```http
GET /api/events?category=TECH
GET /api/events?city=Toronto
GET /api/events?search=java
```

A paginated response can look like:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

This should use Spring Data pagination rather than doing pagination manually.

---

## Users and authentication

Users should be able to register and log in.

User data can contain:

```text
id
name
email
passwordHash
role
createdAt
```

Roles:

```text
USER
ADMIN
```

Basic requirements:

```text
email must be unique
password must be hashed
login returns a JWT
protected APIs require a valid JWT
React sends the JWT with protected requests
```

A normal login flow:

```text
Login request
      ↓
credentials checked
      ↓
JWT created
      ↓
React receives token
      ↓
React sends Authorization header
      ↓
Spring Security validates JWT
      ↓
request continues
```

Passwords should be stored using BCrypt or another proper password encoder supported by Spring Security.

---

## Authorization

A normal user can:

```text
view events
book tickets
view their own bookings
cancel their own bookings
```

An admin can also:

```text
create events
update events
delete or cancel events
```

A normal user should not be able to access admin APIs.

A user also should not be able to open or cancel someone else's booking.

---

## Booking Service

The Booking Service is the main business-flow service in the project.

A booking can contain:

```text
id
bookingReference
userId
eventId
quantity
pricePerTicket
totalAmount
status
idempotencyKey
createdAt
updatedAt
```

Booking statuses:

```text
PENDING
CONFIRMED
CANCELLED
FAILED
```

Main APIs:

```http
POST   /api/bookings
GET    /api/bookings/{id}
GET    /api/bookings/me
DELETE /api/bookings/{id}
```

A booking request only needs information such as:

```json
{
  "eventId": 123,
  "quantity": 2
}
```

The backend should calculate the actual price. The frontend should not be trusted to send the final amount.

A normal booking flow:

```text
User submits booking
        ↓
JWT is validated
        ↓
BookingController
        ↓
BookingService
        ↓
check idempotency key
        ↓
call Event Service
        ↓
check event exists
        ↓
check event is bookable
        ↓
check enough seats are available
        ↓
reserve/reduce seats
        ↓
calculate total amount
        ↓
save booking
        ↓
return confirmation
```

---

## Communication between the two services

The Booking Service should call the Event Service through HTTP.

```text
Booking Service
      |
      | REST
      v
Event Service
```

For example, Booking Service may need:

```text
event ID
ticket price
event status
available seats
```

Each service should own its own database.

Booking Service should not directly connect to or query the Event Service database.

---

## Transactions

Use transactions around operations that need to succeed or fail as one unit.

For example, booking-related database changes should not leave partial data if something fails halfway through.

The main concepts to use here are:

```text
transaction
commit
rollback
@Transactional
```

`@Transactional` should be used where it is actually needed, not added to every service method.

It is also important that transaction handling and concurrency handling are treated as separate problems. A transaction alone does not automatically stop two users from reading the same available-seat value at the same time.

---

## Preventing overbooking

This is one of the main backend problems in the first version.

Imagine:

```text
availableSeats = 1

User A tries to book 1 seat
User B tries to book 1 seat
```

Both requests could read:

```text
availableSeats = 1
```

before either one updates the row.

If nothing protects that update, two bookings could be created for one seat.

Use a proper concurrency solution such as optimistic locking with:

```java
@Version
```

or a suitable database locking approach.

The final result should always be:

```text
1 seat available

multiple users try to book

only one succeeds

availableSeats becomes 0

availableSeats never becomes negative
```

---

## Idempotent booking

Creating a booking should support an idempotency key.

Example:

```http
POST /api/bookings
Idempotency-Key: abc-123
```

This handles cases where the same request reaches the backend more than once.

For example:

```text
user clicks Book
request becomes slow
user clicks Book again
```

The first request should create the booking.

A second request with the same idempotency key should return the existing booking instead of creating another one.

```text
request 1
→ create booking
→ reduce seats

request 2 with same key
→ return previous booking
→ do not create another booking
→ do not reduce seats again
```

Use a database uniqueness constraint as part of the protection rather than relying only on application code.

---

## Exception handling

Use one place for API error handling.

For example:

```java
@RestControllerAdvice
```

Custom exceptions can include:

```text
EventNotFoundException
BookingNotFoundException
InsufficientSeatsException
InvalidEventStateException
UnauthorizedBookingAccessException
```

Return a consistent error structure:

```json
{
  "timestamp": "...",
  "status": 409,
  "code": "INSUFFICIENT_SEATS",
  "message": "Only 1 ticket is available"
}
```

Use HTTP status codes properly:

```text
200 OK
201 Created
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
500 Internal Server Error
503 Service Unavailable
```

---

## PostgreSQL and JPA

Use PostgreSQL for both services.

Main database concepts to practice:

```text
primary keys
unique constraints
indexes
transactions
JPA entities
Spring Data repositories
Hibernate
basic SQL
```

Add useful indexes rather than indexing everything.

Good candidates are fields used often in searches or lookups, such as:

```text
event start time
event city
booking user ID
booking event ID
```

---

## React frontend

Keep the UI simple and functional.

Pages:

```text
Register

Login

Events
- event list
- search
- filters
- pagination

Event Details
- event information
- ticket quantity
- booking button

Booking Confirmation

My Bookings
- user's bookings
- cancel booking

Admin
- create event
- edit event
- delete/cancel event
```

Main React concepts used in this version:

```text
components
props
useState
useEffect
forms
React Router
API calls
JWT handling
loading states
error states
conditional rendering
```

There is no need to add Redux unless the application later becomes complicated enough to actually need it.

---

## Unit tests

Write tests for important business rules instead of trying to maximize test coverage.

Examples:

```text
registration succeeds
duplicate email is rejected
login succeeds
normal user cannot call admin API
invalid event is rejected
booking succeeds
missing event is handled
not enough seats is handled
user cannot cancel another user's booking
same idempotency key does not create two bookings
```

The actual multi-threaded database concurrency test can be added later in MIV 3 when integration testing is set up.

---

## MIV 1 is complete when

A normal user can:

```text
register
→ login
→ browse events
→ search/filter/paginate
→ open event
→ book tickets
→ view booking
→ view My Bookings
→ cancel booking
```

An admin can:

```text
login
→ create event
→ update event
→ delete/cancel event
```

The backend should also already handle:

```text
authentication
authorization
validation
pagination
transactions
concurrent seat updates
idempotent requests
consistent API errors
```

---

# MIV 2 — Failures, Debugging and Performance

## Summary

MIV 2 works on the same application rather than adding a lot of new product features.

The main focus is what happens when one service fails, when network calls are slow, and when the database contains enough data for inefficient queries to become noticeable.

This version covers:

```text
timeouts
failure handling
retries
circuit breaker
logging
query profiling
indexes
JPA query problems
performance measurement
```

---

## Handling Event Service failures

The Booking Service depends on the Event Service.

Test what happens when Event Service is unavailable.

```text
Booking Service
      ↓
Event Service is down
```

The Booking Service should not wait indefinitely or expose a raw Java stack trace.

Configure sensible:

```text
connection timeout
read timeout
```

And translate downstream failures into a controlled API response such as:

```http
503 Service Unavailable
```

A failed Event Service call also should not result in a half-created booking.

Example:

```text
Booking request
      ↓
Event Service unavailable
      ↓
timeout
      ↓
Booking Service handles failure
      ↓
503 returned
      ↓
no invalid booking stored
```

---

## Retry behavior

Add retry behavior only where it makes sense.

For example, retrying a failed read request may be reasonable:

```text
GET event
   ↓
temporary network problem
   ↓
retry
```

State-changing requests need more care.

If a request succeeded on the other service but the response was lost, blindly retrying it could perform the operation twice.

The idempotency work from MIV 1 should help when dealing with this kind of problem.

---

## Circuit breaker

After normal timeout and failure handling is working, add Resilience4j.

The basic circuit breaker states are:

```text
CLOSED
OPEN
HALF_OPEN
```

Normal flow:

```text
Event Service starts failing
        ↓
Booking Service sees repeated failures
        ↓
circuit opens
        ↓
calls fail quickly instead of repeatedly hitting Event Service
        ↓
after some time, limited calls are tried again
        ↓
service recovers
```

Keep the configuration simple. The useful part is seeing how the behavior changes when a dependent service stays unhealthy.

---

## Generate a larger dataset

Performance work needs enough data to expose problems.

Create a script or seed process that can generate something like:

```text
50,000–100,000 events
200,000+ bookings
```

These numbers are only targets. The actual amount can depend on the machine.

The main point is to stop testing database performance using a few dozen rows.

---

## Find an actual slow endpoint

Pick an API that performs filtering, sorting or pagination.

For example:

```http
GET /api/events?city=Toronto&page=0&size=20
```

Measure how long it takes.

Check the SQL generated by JPA/Hibernate.

Do not start changing indexes or queries until there is an actual measurement to compare against.

---

## PostgreSQL query profiling

Use:

```sql
EXPLAIN ANALYZE
```

Check what PostgreSQL is doing.

Basic things to look at:

```text
sequential scan
index scan
rows processed
execution time
```

If a useful index is missing, add it.

For example:

```sql
CREATE INDEX idx_event_city_start_time
ON events(city, start_time);
```

Run the same query again and compare the result.

Keep the before and after numbers somewhere in the project notes.

---

## JPA/Hibernate performance problems

Check the SQL being executed instead of assuming JPA is doing everything efficiently.

A common problem to investigate is N+1 queries.

Example:

```text
1 query loads 100 records

then

another query runs for every record
```

Depending on the case, fix it with something such as:

```text
JOIN FETCH
@EntityGraph
DTO projection
```

Avoid solving every relationship problem by switching everything to `EAGER`.

---

## Measure application performance

Add enough timing/logging to see where time is being spent.

Useful measurements:

```text
event list request
event details request
booking creation
Event Service HTTP call
database query
```

Example:

```text
Event lookup: 12 ms
Event Service call: 40 ms
Booking database operation: 9 ms
Complete booking request: 85 ms
```

These should come from actual runs of the application.

The goal is to be able to separate:

```text
network time
application time
database time
```

instead of treating a slow API as one unknown problem.

---

## Better logging

Improve logs enough that a failed booking can be followed without digging through random output.

Useful information can include:

```text
booking reference
event ID
user ID
request path
downstream status
exception type
request duration
```

Avoid logging:

```text
passwords
JWT tokens
credentials
other sensitive values
```

Keep logging simple at this stage. Correlation IDs across services can be added in MIV 3.

---

## MIV 2 is complete when

A failure scenario can be reproduced:

```text
stop Event Service
      ↓
attempt booking
      ↓
configured timeout happens
      ↓
Booking Service handles the error
      ↓
clean 503 response
      ↓
no broken booking is created
```

And a performance problem can be worked through:

```text
large dataset
      ↓
find slow API
      ↓
measure it
      ↓
inspect SQL
      ↓
run EXPLAIN ANALYZE
      ↓
find query/index problem
      ↓
make change
      ↓
measure again
```

---

# MIV 3 — Integration Testing, Packaging and Tooling

## Summary

MIV 3 adds the pieces that make the project easier to run, test and inspect as a complete system.

The main additions are:

```text
integration testing
Testcontainers
Docker
Docker Compose
Swagger/OpenAPI
database migrations
correlation IDs
```

Async messaging can also be added here if there is enough time.

---

## Integration testing

Add tests that use several parts of the real application together instead of mocking every dependency.

Examples:

```text
Controller + Service + Repository
Spring Boot + PostgreSQL
authentication + protected API
Booking Service + database
```

The integration tests should cover the important flows rather than every endpoint.

---

## Testcontainers

Use Testcontainers to start PostgreSQL automatically during integration tests.

Flow:

```text
JUnit test starts
      ↓
PostgreSQL container starts
      ↓
Spring Boot connects to it
      ↓
real JPA/SQL code runs
      ↓
tests complete
      ↓
container is removed
```

This makes database-related tests much closer to the real application.

---

## Concurrency integration test

Use the real database to prove the seat-locking solution from MIV 1.

Scenario:

```text
availableSeats = 1

10 concurrent booking attempts
```

Expected:

```text
1 succeeds
9 fail cleanly
availableSeats = 0
availableSeats never becomes negative
```

This should run repeatedly without occasionally producing two successful bookings.

---

## Idempotency integration test

Test duplicate booking requests against the real database.

Scenario:

```text
same user
same request
same Idempotency-Key
multiple attempts
```

Expected:

```text
one booking exists
seat count changes once
later requests return the existing booking
```

---

## Docker

Create Dockerfiles for:

```text
event-service
booking-service
frontend
```

Use Docker Compose to run:

```text
frontend
event-service
booking-service
event-postgres
booking-postgres
```

The whole system should start using:

```bash
docker compose up
```

The useful Docker concepts for this project are:

```text
Dockerfile
image
container
port mapping
environment variables
Docker network
service hostname
database volume
```

Kubernetes is outside the scope of this project.

---

## Swagger / OpenAPI

Add OpenAPI documentation for the backend.

Document the important endpoints:

```text
registration/login
events
bookings
admin event management
```

Include:

```text
request models
response models
JWT authentication
pagination parameters
validation errors
status codes
```

Swagger should allow the APIs to be tested without using the React frontend.

---

## Database migrations

Add Flyway or Liquibase.

Example:

```text
V1__create_users.sql
V2__create_events.sql
V3__create_bookings.sql
V4__add_indexes.sql
```

Database schema changes should now be tracked through migrations rather than relying on Hibernate to automatically change the database structure.

---

## Correlation IDs

Add a request ID that can follow one request across multiple services.

Example:

```text
requestId = abc123
```

Flow:

```text
React
   ↓
Booking Service
   ↓
Event Service
```

Logs from both services should contain the same ID.

This makes it easier to find everything related to one request when several users are using the application.

---

## Async notification — optional

If the rest of the project is working well, add asynchronous messaging.

After a successful booking:

```text
Booking Service
      ↓
BOOKING_CONFIRMED
      ↓
Kafka or RabbitMQ
      ↓
Notification Service
```

The Notification Service can start by simply logging a message:

```text
Booking confirmation sent for ABC123
```

The useful part here is separating the main booking operation from work that does not need to finish before the API response is returned.

---

## Transactional outbox — optional

If async messaging is added, another problem appears:

```text
booking saved successfully
message publishing fails
```

One way to handle that is the transactional outbox pattern.

Inside one database transaction:

```text
save booking
+
save outgoing event
```

Then another process reads the outgoing events and publishes them to the message broker.

This should only be added after the simpler messaging version is working.

---

## Redis — optional and low priority

Only add Redis if there is something that is genuinely worth caching.

A possible example is:

```text
GET /events/{id}
```

for frequently viewed events.

If Redis is added, cover:

```text
cache hit
cache miss
TTL
cache invalidation
```

If there is no clear use for it, leave it out.

---

## MIV 3 is complete when

Someone can clone the repository and get the complete project running with a small number of steps.

Ideally:

```text
clone repository
      ↓
docker compose up
      ↓
open React application
      ↓
use the system
      ↓
open Swagger
      ↓
run unit tests
      ↓
run integration tests
```

The integration tests should cover the important behavior:

```text
authentication
authorization
database persistence
booking creation
idempotency
concurrency
```

---

# Build order

The versions should be done in order:

```text
MIV 1
  ↓
MIV 2
  ↓
MIV 3
  ↓
optional extras
```

Do not stop halfway through MIV 1 to add Kafka, Redis, Docker or other infrastructure.

The project should stay focused on a few important flows rather than becoming a collection of unrelated technologies.

The main flow around which most of the project is built is:

```text
User
 ↓
React
 ↓
Booking API
 ↓
Authentication
 ↓
Booking Service
 ↓
Idempotency check
 ↓
Event Service
 ↓
Availability check
 ↓
Concurrency-safe seat update
 ↓
PostgreSQL
 ↓
Booking saved
 ↓
Response returned
```

Most of the useful Java, Spring Boot, REST, PostgreSQL and React practice in the project comes from building this flow properly and then improving how it behaves under errors and load.
