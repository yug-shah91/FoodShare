package com.foodshare.entity;

import jakarta.persistence.*;

// One row in the "users" table.
@Entity
@Table(name = "users")
public class User {

    public enum Role { DONOR, RECIPIENT, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;   // stored as a BCrypt hash, never plain text

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Contact & Profile details
    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String organizationName;

    @Column(length = 50)
    private String donorType; // e.g. "Restaurant", "Bakery", "Supermarket", "Individual", "Community Kitchen"

    @Column(length = 60)
    private String badge;

    private Double rating = 4.9;

    private Integer karmaPoints = 100;

    public User() {}

    public User(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public User(String username, String password, String fullName, Role role, String phone, String organizationName, String donorType, String badge) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.phone = phone;
        this.organizationName = organizationName;
        this.donorType = donorType;
        this.badge = badge;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getDonorType() { return donorType; }
    public void setDonorType(String donorType) { this.donorType = donorType; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getKarmaPoints() { return karmaPoints; }
    public void setKarmaPoints(Integer karmaPoints) { this.karmaPoints = karmaPoints; }
}
