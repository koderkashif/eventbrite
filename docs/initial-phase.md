# Architecture:

```text
React
  |
  v
Spring Boot REST APIs
  |
  +-----------------------+
  |                       |
Event Service         Booking Service
  |                       |
PostgreSQL            PostgreSQL
                          |
                    Notification Service
                     (optional async)
```

Start as one Spring Boot application, then extract `booking-service` into a separate microservice. That progression itself gives you something valuable to discuss in the interview: **monolith → service boundary → microservice integration**.

Focus on these 12 things, in this order:

1. **Java fundamentals that appear constantly in backend interviews.** Be comfortable with `List`, `Set`, `Map`, streams, lambdas, `Optional`, exceptions, interfaces, inheritance/composition, records/DTOs, `equals/hashCode`, immutability, generics, and basic concurrency. You don't need hundreds of LeetCode questions, but you should be able to manipulate collections without an AI agent.

2. **Build proper Spring Boot REST APIs.** Implement:

   ```http
   POST   /events
   GET    /events
   GET    /events/{id}
   PUT    /events/{id}
   DELETE /events/{id}

   POST   /bookings
   GET    /bookings/{id}
   GET    /users/{userId}/bookings
   DELETE /bookings/{id}
   ```

   Learn `@RestController`, `@Service`, `@Repository`, dependency injection, `@RequestBody`, `@PathVariable`, `@RequestParam`, HTTP status codes, DTOs, and controller/service/repository separation.

3. **Master Spring validation and exception handling.** Use Bean Validation:

   ```java
   @NotBlank
   @Email
   @Min
   @Future
   ```

   Then create:

   ```java
   @RestControllerAdvice
   ```

   with consistent error responses:

   ```json
   {
     "timestamp": "...",
     "status": 404,
     "error": "EVENT_NOT_FOUND",
     "message": "Event 123 does not exist"
   }
   ```

   Interviewers frequently ask how you handle validation and exceptions globally.

4. **Get genuinely comfortable with PostgreSQL and JPA.** Create tables for `users`, `events`, `bookings`, and perhaps `venues`. Understand `@Entity`, `@Id`, `@GeneratedValue`, `@OneToMany`, `@ManyToOne`, lazy vs eager loading, JPQL, native queries, indexes, joins, transactions, and the N+1 problem. Don't hide completely behind Spring Data repositories—write several SQL queries yourself.

5. **Solve the hardest business problem: prevent overbooking.** Give an event:

   ```text
   capacity = 100
   ```

   Then simulate 20 people simultaneously trying to purchase the last seat. Understand why this is unsafe:

   ```java
   if (event.getAvailableSeats() > 0) {
       event.setAvailableSeats(event.getAvailableSeats() - 1);
   }
   ```

   Implement a safe solution using a transaction plus optimistic locking (`@Version`) or database/pessimistic locking. This single feature lets you discuss **transactions, concurrency, race conditions, isolation and database consistency**—excellent backend interview material.

6. **Split Booking into a real microservice.** Eventually have:

   ```text
   event-service :8081
   booking-service :8082
   ```

   Booking service calls Event service:

   ```text
   booking-service
        |
        | GET /events/{id}
        v
   event-service
   ```

   Learn service-to-service REST communication using Spring's HTTP client, timeouts, failure handling, and what happens when Event Service is unavailable.

7. **Understand microservice failure scenarios rather than merely creating multiple Spring projects.** Be able to answer: What happens if service B is down? What if a network request times out? What if Booking succeeds but another operation fails? Why not use one giant distributed transaction? When would you retry? When shouldn't you retry? What is idempotency? Add an `Idempotency-Key` to booking creation so retrying a request doesn't create two bookings.

8. **Add authentication using Spring Security + JWT.** Have `USER` and `ADMIN`. Users can book events; admins can create/update events. Understand the authentication flow:

   ```text
   Login
     ↓
   validate credentials
     ↓
   JWT generated
     ↓
   React stores/uses token
     ↓
   Authorization: Bearer <token>
     ↓
   Spring Security filter
     ↓
   Controller
   ```

   You don't need to become a Spring Security expert, but you should understand the flow rather than pasting configuration blindly.

9. **Add meaningful automated tests.** Write unit tests for service-layer logic with JUnit + Mockito, controller/API tests with MockMvc, and ideally an integration test against PostgreSQL/Testcontainers. Especially test the important case:

   ```text
   given 1 seat remaining
   when 5 simultaneous booking attempts occur
   then exactly 1 booking succeeds
   ```

   Being able to explain what you unit-test versus integration-test is valuable.

10. **Build only enough React to prove full-stack ability.** Have an event list page, event details, booking form, "My Bookings", login, and an admin create-event page. Know `useState`, `useEffect`, components, props, forms, routing, API calls with `fetch`/Axios, loading/error states, and basic state management. Don't spend your preparation time learning advanced React animations or complex frontend architecture.

11. **Practice debugging and performance optimization inside the project.** Intentionally create and then fix an N+1 query, an unindexed search, a slow API, a null pointer, a duplicate booking, and a failing downstream service. Add timing/logging around requests. Use PostgreSQL `EXPLAIN` on at least one query and add an index such as:

    ```sql
    CREATE INDEX idx_events_event_date
    ON events(event_date);
    ```

    You want to be able to tell the interviewer about a concrete performance problem you found and how you diagnosed it.

12. **Dockerize it.** You don't need Kubernetes unless the interviewer specifically expects it. Being able to run:

    ```bash
    docker compose up
    ```

    and start React + PostgreSQL + event-service + booking-service is plenty for this JD. Understand environment variables, database URLs, ports and separate service configuration.

The most important thing is that you should be able to **draw and explain the complete request lifecycle** without AI:

```text
React
  ↓ POST /bookings
Controller
  ↓
DTO validation
  ↓
BookingService
  ↓
check Event Service
  ↓
@Transactional
  ↓
BookingRepository
  ↓
PostgreSQL
  ↓
DTO response
  ↓
HTTP 201
  ↓
React
```

