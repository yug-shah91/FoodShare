package com.foodshare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Data sent to / received from the login and register APIs.
public class AuthDtos {

    public record LoginRequest(
            @NotBlank @Size(max = 30, message = "Username is too long (max 30)") String username,
            @NotBlank @Size(max = 64, message = "Password is too long (max 64)") String password) {}

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters")
            @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username can only contain letters, numbers and underscore")
            String username,
            @NotBlank
            @Size(min = 6, max = 64, message = "Password must be 6 to 64 characters")
            String password,
            @NotBlank @Size(max = 60, message = "Name is too long (max 60)") String fullName,
            @NotBlank
            @Pattern(regexp = "(?i)DONOR|RECIPIENT", message = "Role must be DONOR or RECIPIENT")
            String role,
            @Size(max = 20) String phone,
            @Size(max = 100) String organizationName,
            @Size(max = 50) String donorType) {}

    public record LoginResponse(
            String token,
            String username,
            String fullName,
            String role,
            String phone,
            String organizationName,
            String donorType,
            String badge,
            Double rating,
            Integer karmaPoints) {}
}
