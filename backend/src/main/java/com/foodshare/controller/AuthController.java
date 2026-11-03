package com.foodshare.controller;

import com.foodshare.dto.AuthDtos.*;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // getRemoteAddr() is the real client IP. Behind a reverse proxy, server.forward-headers-strategy=native
        // (see application-prod.properties) makes Tomcat fill it in from X-Forwarded-For, but only when the
        // request really comes from a trusted proxy. We never read the X-Forwarded-For header ourselves,
        // because a client could fake it.
        return authService.login(request, http.getRemoteAddr());
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        // same IP rule as login: getRemoteAddr(), never the X-Forwarded-For header itself
        return authService.register(request, http.getRemoteAddr());
    }
}
