package com.eventbrite.booking.bootstrap;

import com.eventbrite.booking.client.EventServiceClient.BulkEventRef;
import com.eventbrite.booking.client.EventServiceClient.BulkEventsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates ~200,000 synthetic bookings (and 50 load-test users) for performance work.
 * Activate with:  java -jar booking-service.jar --spring.profiles.active=bulk
 *
 * Event references are fetched from event-service THROUGH ITS API (never its database) -
 * the microservice boundary applies to seeder code too. Users all share one bcrypt
 * hash computed once (same password -> same hash is fine and saves 50 slow bcrypt runs).
 *
 * Deliberate simplification: these synthetic bookings do NOT decrement event available
 * seats (historical data; seat accounting is not what we're load-testing here).
 */
@Component
@Profile("bulk")
public class BulkBookingSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BulkBookingSeeder.class);

    private static final int TOTAL_BOOKINGS = 200_000;
    private static final int FETCH_PAGES = 40;      // 40 x 500 = up to 20k distinct events
    private static final int PAGE_SIZE = 500;
    private static final int USERS = 50;
    private static final int BATCH = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final PasswordEncoder passwordEncoder;

    public BulkBookingSeeder(JdbcTemplate jdbcTemplate,
                             @org.springframework.beans.factory.annotation.Qualifier("eventServiceRestClient") RestClient restClient,
                             PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Integer bookings = jdbcTemplate.queryForObject("select count(*) from bookings", Integer.class);
        if (bookings != null && bookings >= 100_000) {
            log.info("Bulk seed skipped: {} bookings already present", bookings);
            return;
        }

        List<BulkEventRef> events = fetchEvents();
        if (events.isEmpty()) {
            log.warn("Bulk seed aborted: event-service returned no events (is it running? is bulk data seeded?)");
            return;
        }

        seedUsers();
        seedBookings(events);
    }

    private List<BulkEventRef> fetchEvents() {
        List<BulkEventRef> events = new ArrayList<>();
        for (int page = 0; page < FETCH_PAGES; page++) {
            BulkEventsPage body = restClient.get()
                    .uri("/api/events?page={page}&size={size}&sort=startTime,asc", page, PAGE_SIZE)
                    .retrieve()
                    .body(BulkEventsPage.class);
            if (body == null || body.content() == null || body.content().isEmpty()) {
                break;
            }
            events.addAll(body.content());
        }
        log.info("Fetched {} published events from event-service for booking generation", events.size());
        return events;
    }

    private void seedUsers() {
        Integer users = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        if (users != null && users >= USERS) {
            return;
        }
        String hash = passwordEncoder.encode("bulk1234"); // one bcrypt run, reused
        List<Object[]> batch = new ArrayList<>(USERS);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= USERS; i++) {
            batch.add(new Object[]{"Load User " + i, "load-" + i + "@bulk.local", hash, "USER", now.minusDays(90)});
        }
        jdbcTemplate.batchUpdate(
                "insert into users (name, email, password_hash, role, created_at) values (?, ?, ?, ?, ?)", batch);
        log.info("Seeded {} load-test users (password: bulk1234)", USERS);
    }

    private void seedBookings(List<BulkEventRef> events) {
        String sql = """
                insert into bookings (booking_reference, user_id, event_id, event_name, quantity,
                                      price_per_ticket, total_amount, status, idempotency_key, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', null, ?, ?)
                """;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batch = new ArrayList<>(BATCH);
        long startMs = System.currentTimeMillis();

        for (int i = 1; i <= TOTAL_BOOKINGS; i++) {
            BulkEventRef event = events.get(rnd.nextInt(events.size()));
            int quantity = 1 + rnd.nextInt(0, 4);
            BigDecimal total = event.ticketPrice().multiply(BigDecimal.valueOf(quantity));
            LocalDateTime createdAt = now.minusMinutes(rnd.nextLong(0, 90L * 24 * 60)).withSecond(0).withNano(0);
            batch.add(new Object[]{
                    "BKB-" + (1_000_000 + i),
                    1L + rnd.nextInt(0, USERS),           // existing demo users occupy low ids
                    event.id(),
                    event.name(),
                    quantity,
                    event.ticketPrice(),
                    total,
                    createdAt,
                    createdAt
            });

            if (batch.size() == BATCH) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
                if (i % 25_000 == 0) {
                    log.info("bulk bookings: {}/{} ({} ms elapsed)", i, TOTAL_BOOKINGS,
                            System.currentTimeMillis() - startMs);
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("Bulk seed DONE: {} bookings inserted in {} ms", TOTAL_BOOKINGS,
                System.currentTimeMillis() - startMs);
    }
}
