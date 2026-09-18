package com.eventbrite.event.concurrency;

import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.Event;
import com.eventbrite.event.entity.EventStatus;
import com.eventbrite.event.repository.EventRepository;
import com.eventbrite.event.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE headline test of this service:
 *
 *   given    a PUBLISHED event with 1 seat remaining
 *   when     20 threads reserve 1 seat simultaneously
 *   then     exactly 1 succeeds, availableSeats == 0, never negative
 *
 * Safety comes from @Transactional + @Version on Event. Remove @Version and
 * this test fails with overbooking - which is the whole lesson.
 */
@SpringBootTest
class EventReserveConcurrencyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    private Long eventId;

    @BeforeEach
    void setUp() {
        Event event = new Event("Last Seat Race " + System.nanoTime(), "capacity 1",
                Category.TECH, "Anywhere", "Bengaluru",
                LocalDateTime.now().plusDays(1).withHour(18), LocalDateTime.now().plusDays(1).withHour(21),
                BigDecimal.TEN, 1);
        event.setStatus(EventStatus.PUBLISHED);
        eventId = eventRepository.save(event).getId();
    }

    @Test
    void onlyOneReservationWinsWhenTwentyThreadsRaceForTheLastSeat() throws Exception {
        int threads = 20;
        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> successes = Collections.synchronizedList(new ArrayList<>());
        List<String> failureTypes = Collections.synchronizedList(new ArrayList<>());

        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // everyone waits at the gate...
                    eventService.reserveSeats(eventId, 1);
                    successes.add(1);
                } catch (Exception e) {
                    failureTypes.add(e.getClass().getSimpleName());
                }
            }));
        }

        ready.await();       // ...so all threads hit the service at the same instant
        start.countDown();   // GO

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, successes.size(), "exactly one reservation must win");
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertEquals(0, event.getAvailableSeats(), "seats must reach 0, never negative");
        assertEquals(19, failureTypes.size());

        System.out.println(">> race result: 1 success, " + failureTypes.size() + " failures, mix="
                + failureTypes.stream().distinct().toList());
    }

    @Test
    void reserveOnDraftEventIsRejected() {
        Event draft = new Event("Draft " + System.nanoTime(), "not live yet",
                Category.ART, "Nowhere", "Pune",
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2),
                BigDecimal.ONE, 10);
        Long draftId = eventRepository.save(draft).getId(); // constructor default = DRAFT

        assertThrows(com.eventbrite.event.exception.InvalidEventStateException.class,
                () -> eventService.reserveSeats(draftId, 1));
    }
}
