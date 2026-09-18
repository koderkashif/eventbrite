package com.eventbrite.booking.service;

import com.eventbrite.booking.dto.AuthResponse;
import com.eventbrite.booking.dto.LoginRequest;
import com.eventbrite.booking.dto.RegisterRequest;
import com.eventbrite.booking.entity.Role;
import com.eventbrite.booking.entity.User;
import com.eventbrite.booking.exception.DuplicateEmailException;
import com.eventbrite.booking.repository.UserRepository;
import com.eventbrite.booking.security.AppUserPrincipal;
import com.eventbrite.booking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return u; // id stays null; irrelevant for these assertions
        });
        when(jwtService.issueToken(any(User.class))).thenReturn("token-123");

        AuthResponse response = authService.register(
                new RegisterRequest("New User", "new@example.com", "password123"));

        assertEquals("token-123", response.token());
        assertEquals("new@example.com", response.user().email());
        assertEquals("USER", response.user().role()); // self-registration is never ADMIN
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.register(
                new RegisterRequest("Someone", "taken@example.com", "password123")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User("Demo", "demo@eventbrite.com", "{bcrypt}hash", Role.USER);
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(withId(user, 7L)), null);

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(userRepository.findById(7L)).thenReturn(Optional.of(withId(user, 7L)));
        when(jwtService.issueToken(any(User.class))).thenReturn("token-xyz");

        AuthResponse response = authService.login(new LoginRequest("demo@eventbrite.com", "demo1234"));

        assertEquals("token-xyz", response.token());
        assertEquals("demo@eventbrite.com", response.user().email());
    }

    /** Tests need a user with an id; JPA normally supplies it. */
    private static User withId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
