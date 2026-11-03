package com.foodshare.service;

import com.foodshare.dto.AuthDtos.*;
import com.foodshare.entity.User;
import com.foodshare.repository.UserRepository;
import com.foodshare.security.JwtUtil;
import com.foodshare.security.LoginRateLimiter;
import com.foodshare.security.RegistrationRateLimiter;
import com.foodshare.security.TooManyAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;   // slows down password guessing (per IP, per account+IP, per account)
    private final RegistrationRateLimiter registrationLimiter;   // slows down mass account creation (per IP)

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       LoginRateLimiter rateLimiter, RegistrationRateLimiter registrationLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
        this.registrationLimiter = registrationLimiter;
    }

    // "  Yash " -> "yash", so "Yash" and "yash" are the same user
    private String normalize(String username) {
        // Locale.ROOT: the result must not depend on the server's language settings
        // (for example, in a Turkish locale "I".toLowerCase() would give a dotless "ı")
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = normalize(request.username());

        // Refuse BEFORE checking the password, so a blocked client learns nothing about the password.
        long wait = rateLimiter.secondsUntilAllowed(username, clientIp);
        if (wait > 0) {
            throw new TooManyAttemptsException(wait);
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            // Unknown usernames are counted exactly like known ones, so the limiter does not reveal which users exist.
            rateLimiter.recordFailure(username, clientIp);
            if (rateLimiter.accountUnderAttack(username)) {
                // soft signal only (nobody is blocked by it): many recent failures for this account from various IPs
                log.warn("Possible distributed password guessing against account '{}'", username.replaceAll("[^a-z0-9_]", "?"));
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
        }

        rateLimiter.recordSuccess(username, clientIp);
        return makeResponse(user);
    }

    // (no @Transactional here on purpose, so a duplicate-username error is thrown inside the try/catch)
    public LoginResponse register(RegisterRequest request, String clientIp) {
        // Throttle BEFORE any database or password-hashing work. Every attempt counts, successful or not.
        long wait = registrationLimiter.tryAcquire(clientIp);
        if (wait > 0) {
            throw new TooManyAttemptsException(wait, "Too many registration attempts from your network.");
        }

        String username = normalize(request.username());

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already taken");
        }

        // Only DONOR or RECIPIENT can self-register. Nobody can make themselves ADMIN.
        User.Role role;
        try {
            role = User.Role.valueOf(request.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be DONOR or RECIPIENT");
        }
        if (role == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be DONOR or RECIPIENT");
        }

        User user = new User(username, passwordEncoder.encode(request.password()),
                request.fullName().trim(), role);
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }
        if (request.organizationName() != null && !request.organizationName().isBlank()) {
            user.setOrganizationName(request.organizationName().trim());
        }
        if (request.donorType() != null && !request.donorType().isBlank()) {
            user.setDonorType(request.donorType().trim());
        } else if (role == User.Role.DONOR) {
            user.setDonorType("Individual Donor");
        }
        user.setBadge(role == User.Role.DONOR ? "Community Donor" : "Food Rescuer");
        user.setRating(5.0);
        user.setKarmaPoints(role == User.Role.DONOR ? 150 : 50);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // two people registered the same username at the exact same moment;
            // the database "unique" rule on the username column stopped the second one
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already taken");
        }
        registrationLimiter.recordCreated();   // feeds the "registration spike" warning in the log
        return makeResponse(user);
    }

    private LoginResponse makeResponse(User user) {
        String token = jwtUtil.generateToken(user.getUsername());
        return new LoginResponse(
                token,
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getPhone(),
                user.getOrganizationName(),
                user.getDonorType(),
                user.getBadge(),
                user.getRating(),
                user.getKarmaPoints()
        );
    }
}
