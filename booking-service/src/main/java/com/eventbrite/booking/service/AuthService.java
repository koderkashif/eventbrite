package com.eventbrite.booking.service;

import com.eventbrite.booking.dto.AuthResponse;
import com.eventbrite.booking.dto.LoginRequest;
import com.eventbrite.booking.dto.RegisterRequest;
import com.eventbrite.booking.entity.Role;
import com.eventbrite.booking.entity.User;
import com.eventbrite.booking.exception.DuplicateEmailException;
import com.eventbrite.booking.mapper.UserMapper;
import com.eventbrite.booking.repository.UserRepository;
import com.eventbrite.booking.security.AppUserPrincipal;
import com.eventbrite.booking.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Registration auto-logs-in (token returned immediately). */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        User user = userRepository.save(new User(
                request.name(), request.email(),
                passwordEncoder.encode(request.password()), // never store raw passwords
                Role.USER));
        return new AuthResponse(jwtService.issueToken(user), UserMapper.toResponse(user));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalStateException("Authenticated user disappeared"));
        return new AuthResponse(jwtService.issueToken(user), UserMapper.toResponse(user));
    }
}
