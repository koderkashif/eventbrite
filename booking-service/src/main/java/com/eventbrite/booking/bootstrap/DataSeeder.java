package com.eventbrite.booking.bootstrap;

import com.eventbrite.booking.entity.Role;
import com.eventbrite.booking.entity.User;
import com.eventbrite.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds a demo admin and a demo user so the app is usable the moment it starts. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(new User("Admin", "admin@eventbrite.com",
                passwordEncoder.encode("admin123"), Role.ADMIN));
        userRepository.save(new User("Demo User", "demo@eventbrite.com",
                passwordEncoder.encode("demo1234"), Role.USER));

        log.info("Seeded users -> admin@eventbrite.com / admin123 (ADMIN), demo@eventbrite.com / demo1234 (USER)");
    }
}
