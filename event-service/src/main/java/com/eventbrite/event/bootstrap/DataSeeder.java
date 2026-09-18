package com.eventbrite.event.bootstrap;

import com.eventbrite.event.entity.Category;
import com.eventbrite.event.entity.Event;
import com.eventbrite.event.entity.EventStatus;
import com.eventbrite.event.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Seeds demo events on an empty database so the app is presentable the moment it starts. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final EventRepository repository;

    public DataSeeder(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        LocalDateTime base = LocalDateTime.now().plusDays(3);
        List<Event> events = List.of(
                published("Bengaluru Tech Summit 2026", "Talks on Java, Spring Boot and scaling backends",
                        Category.TECH, "Palace Grounds", "Bengaluru", base.withHour(9), base.withHour(17).plusDays(1), "150.00", 500),
                published("Sufi & Ghazal Evening", "An evening of classical Sufi and ghazal music",
                        Category.MUSIC, "NMACC", "Mumbai", base.plusDays(2).withHour(19), base.plusDays(2).withHour(22), "35.00", 300),
                published("Startup Pitch Day", "Founders pitch to a panel of investors",
                        Category.BUSINESS, "T-Hub", "Hyderabad", base.plusDays(4).withHour(10), base.plusDays(4).withHour(14), "0.00", 120),
                published("Mumbai Street Food Festival", "Street food from every corner of the city",
                        Category.FOOD, "NSCI Dome", "Mumbai", base.plusDays(5).withHour(12), base.plusDays(5).withHour(23), "50.00", 2000),
                published("React vs Vue: Live Debate", "Frontend frameworks argue it out",
                        Category.TECH, "India Habitat Centre", "Delhi", base.plusDays(7).withHour(17), base.plusDays(7).withHour(19), "20.00", 150),
                published("Delhi Half Marathon", "Run past the city's landmarks",
                        Category.SPORTS, "India Gate", "Delhi", base.plusDays(10).withHour(6), base.plusDays(10).withHour(12), "25.00", 5000),
                published("Microservices Workshop", "Hands-on: split a monolith in one day",
                        Category.TECH, "91springboard", "Bengaluru", base.plusDays(12).withHour(9), base.plusDays(12).withHour(17), "75.00", 40),
                published("Contemporary Art Exhibition", "Emerging Indian artists",
                        Category.ART, "Jehangir Art Gallery", "Mumbai", base.plusDays(14).withHour(10), base.plusDays(14).withHour(20), "10.00", 250),
                published("Indie Music Concert", "Independent bands, one big night",
                        Category.MUSIC, "antiSOCIAL", "Mumbai", base.plusDays(16).withHour(19), base.plusDays(16).withHour(23), "45.00", 800),
                published("Cricket Legends T20", "Retired stars, one last tournament",
                        Category.SPORTS, "Wankhede Stadium", "Mumbai", base.plusDays(19).withHour(15), base.plusDays(19).withHour(19), "500.00", 33000),
                published("Cloud Cost Optimization Bootcamp", "Cut your AWS bill in half",
                        Category.TECH, "Online", "Online", base.plusDays(21).withHour(14), base.plusDays(21).withHour(18), "60.00", 200),
                published("Baking Masterclass", "French patisserie techniques",
                        Category.FOOD, "Culinary Studio", "Pune", base.plusDays(23).withHour(11), base.plusDays(23).withHour(15), "85.00", 24),
                published("Leadership in Engineering", "For senior devs moving to EM roles",
                        Category.BUSINESS, "Online", "Online", base.plusDays(26).withHour(16), base.plusDays(26).withHour(18), "30.00", 300),
                draft("AI in Fintech Panel", "Panel discussion - not announced yet",
                        Category.TECH, "JW Marriott", "Bengaluru", base.plusDays(30).withHour(18), base.plusDays(30).withHour(21), "100.00", 200)
        );

        repository.saveAll(events);
        log.info("Seeded {} demo events (13 published, 1 draft)", events.size());
    }

    private Event published(String name, String description, Category category, String venue, String city,
                            LocalDateTime start, LocalDateTime end, String price, int capacity) {
        return event(name, description, category, venue, city, start, end, price, capacity, EventStatus.PUBLISHED);
    }

    private Event draft(String name, String description, Category category, String venue, String city,
                        LocalDateTime start, LocalDateTime end, String price, int capacity) {
        return event(name, description, category, venue, city, start, end, price, capacity, EventStatus.DRAFT);
    }

    private Event event(String name, String description, Category category, String venue, String city,
                        LocalDateTime start, LocalDateTime end, String price, int capacity, EventStatus status) {
        Event e = new Event(name, description, category, venue, city, start, end, new BigDecimal(price), capacity);
        e.setStatus(status);
        return e;
    }
}
