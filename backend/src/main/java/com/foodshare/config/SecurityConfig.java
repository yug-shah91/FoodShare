package com.foodshare.config;

import com.foodshare.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    // true only in local development (see application.properties). Off in the "prod" profile.
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    // This is the main place that says "who can open which URL".
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
            // CSRF protection is off ONLY because authentication is a Bearer token in the Authorization header:
            // browsers never add that header by themselves, so another website cannot make a logged-in user's
            // browser send an authenticated request. If login ever moves to cookies (e.g. HttpOnly JWT cookie),
            // turn CSRF protection back on (CookieCsrfTokenRepository + SameSite cookies).
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> { if (h2ConsoleEnabled) h.frameOptions(f -> f.disable()); })  // H2 console page needs this
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/**").permitAll();
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();   // local development only
                }
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                auth.requestMatchers(HttpMethod.POST, "/api/donations").hasRole("DONOR");
                auth.requestMatchers("/api/donations/*/cancel").hasRole("DONOR");
                auth.requestMatchers("/api/donations/mine").hasRole("DONOR");
                auth.requestMatchers("/api/donations/claimed", "/api/donations/claim-history").hasRole("RECIPIENT");
                auth.requestMatchers("/api/donations/*/claim").hasRole("RECIPIENT");
                auth.requestMatchers("/api/donations/*/feedback").hasRole("RECIPIENT");
                auth.requestMatchers("/api/donations/*/complete").hasAnyRole("RECIPIENT", "DONOR");
                auth.requestMatchers("/api/donations/*/verify-pickup-otp").hasAnyRole("RECIPIENT", "DONOR", "ADMIN");
                auth.requestMatchers("/api/donations/map", "/api/donations/impact").authenticated();
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Allows the React app (running on another port) to call this API
    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
