package com.foodshare.security;

import com.foodshare.entity.User;
import com.foodshare.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Runs on every request. If the request has "Authorization: Bearer <token>",
// it checks the token and tells Spring Security who the user is.
//
// A missing, invalid, expired or orphaned token (user deleted) all end the same way: the request stays
// UNAUTHENTICATED, and Spring Security answers 401 on every protected URL (see SecurityConfig). The filter does not
// answer 401 itself, because the public URLs (/api/auth/**) must keep working even if a browser still sends an old token.
// The reason is logged at DEBUG level (never the token itself) so a support person can tell the cases apart.
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String username = jwtUtil.getUsername(header.substring(7));
            if (username == null) {
                log.debug("Ignoring an invalid or expired Bearer token on {} {}", request.getMethod(), request.getRequestURI());
            } else {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user == null) {
                    log.debug("Ignoring a valid token for a user that no longer exists");
                } else {
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                    var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
