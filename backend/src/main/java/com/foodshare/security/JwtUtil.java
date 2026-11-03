package com.foodshare.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Creates and reads JWT tokens. A token is a signed text that proves "this user logged in".
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEV_SECRET_START = "dev-only-secret";

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        if (secret.startsWith(DEV_SECRET_START)) {
            log.warn("=====================================================================");
            log.warn(" Using the DEVELOPMENT JWT secret. Fine on your laptop, NEVER in production.");
            log.warn(" Set the JWT_SECRET environment variable and use the 'prod' profile.");
            log.warn("=====================================================================");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    // Returns the username inside the token, or null if the token is invalid/expired
    public String getUsername(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
