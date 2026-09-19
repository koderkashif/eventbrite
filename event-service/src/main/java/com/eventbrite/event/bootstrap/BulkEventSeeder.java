package com.eventbrite.event.bootstrap;

import com.eventbrite.event.entity.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates ~50,000 synthetic events for performance work. Activate with:
 *   java -jar event-service.jar --spring.profiles.active=bulk
 *
 * Uses JdbcTemplate batches instead of JPA saveAll(): 50k entities through the
 * EntityManager means 50k dirty-check snapshots; plain batched JDBC is ~10x faster
 * and this data has no business logic to honor. Right tool for the job.
 */
@Component
@Profile("bulk")
public class BulkEventSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BulkEventSeeder.class);

    private static final int TOTAL = 50_000;
    private static final int BATCH = 500;

    private static final String[] CITIES =
            {"Bengaluru", "Mumbai", "Delhi", "Hyderabad", "Pune", "Chennai", "Kolkata", "Ahmedabad", "Jaipur", "Online"};
    private static final Category[] CATEGORIES = Category.values();
    private static final String[] NAME_PREFIXES =
            {"Tech Talk", "Live Music Night", "Community Run", "Business Breakfast", "Food Tasting", "Art Walk"};

    private final JdbcTemplate jdbcTemplate;

    public BulkEventSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from events", Integer.class);
        if (count != null && count >= TOTAL) {
            log.info("Bulk seed skipped: {} events already present", count);
            return;
        }

        String sql = """
                insert into events (name, description, category, venue, city, start_time, end_time,
                                    ticket_price, capacity, available_seats, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;

        LocalDateTime now = LocalDateTime.now();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Object[]> batch = new ArrayList<>(BATCH);
        long startMs = System.currentTimeMillis();

        for (int i = 1; i <= TOTAL; i++) {
            int catIdx = i % CATEGORIES.length;
            LocalDateTime start = now.plusMinutes(rnd.nextLong(60, 365L * 24 * 60)).withSecond(0).withNano(0);
            batch.add(new Object[]{
                    NAME_PREFIXES[catIdx] + " #" + i,
                    "Synthetic event generated for load testing",
                    CATEGORIES[catIdx].name(),
                    "Venue " + (i % 20 + 1),
                    CITIES[i % CITIES.length],
                    start,
                    start.plusHours(2 + rnd.nextInt(0, 5)),
                    BigDecimal.valueOf(rnd.nextInt(0, 200_000), 2), // 0.00 - 2000.00
                    10 + rnd.nextInt(0, 4990),
                    0, // fresh synthetic events: nothing booked yet
                    (i % 10 == 0) ? "DRAFT" : "PUBLISHED",          // 90% published
                    now.minusDays(rnd.nextLong(0, 30)),
                    now.minusDays(rnd.nextLong(0, 30))
            });

            if (batch.size() == BATCH) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
                if ((i % 10_000) == 0) {
                    log.info("bulk events: {}/{} ({} ms elapsed)", i, TOTAL, System.currentTimeMillis() - startMs);
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("Bulk seed DONE: {} events inserted in {} ms", TOTAL, System.currentTimeMillis() - startMs);
    }
}
