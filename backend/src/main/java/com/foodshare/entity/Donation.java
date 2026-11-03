package com.foodshare.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.Instant;

// One row in the "donations" table (a food listing).
@Entity
@Table(name = "donations")
public class Donation {

    public enum Category { COOKED_MEALS, BAKERY_ITEMS, VEGETABLES_FRUITS, DAIRY_PRODUCTS, PACKAGED_FOOD }
    public enum Storage { ROOM_TEMPERATURE, REFRIGERATED, FROZEN }

    // ACTIVE    = available to claim
    // CLAIMED   = a recipient reserved it, pickup pending
    // COMPLETED = picked up
    // EXPIRED   = the "consume before" time passed
    // CANCELLED = donor cancelled it
    public enum Status { ACTIVE, CLAIMED, COMPLETED, EXPIRED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String foodName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Min(1)
    @Column(nullable = false)
    private int quantity;          // number of servings

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Storage storage;

    // PUBLIC, approximate place the donor chooses to show everybody, e.g. "Vijay Nagar, Indore".
    @Column(length = 100)
    private String area;

    // PRIVATE: the exact pickup address. Only the donor and the recipient who claimed the food ever receive it.
    @Column(nullable = false)
    private String address;

    @Column(length = 300)
    private String notes;

    private String allergens;      // e.g. "Contains nuts, milk"

    @Column(length = 30)
    private String dietaryType = "VEG"; // VEG, NON_VEG, VEGAN, JAIN

    // Map Geolocation coordinates (e.g. 22.7533, 75.8937 for Indore)
    private Double latitude;
    private Double longitude;

    // Instant = an exact moment in time (UTC). It does not depend on the server's timezone.
    @Column(nullable = false)
    private Instant preparedAt;   // when the food was made (told by the donor)

    @Column(nullable = false)
    private Instant expiresAt;    // "consume before" time (told by the donor)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @ManyToOne(optional = false)
    private User donor;            // who created it

    @ManyToOne
    private User claimedBy;        // which recipient claimed it (null until claimed)

    private Instant claimedAt;
    private Instant claimDeadline;   // recipient must pick up before this, else it is released

    // Secure 6-digit OTP generated upon claiming for physical handover verification
    @Column(length = 6)
    private String pickupOtp;

    private Instant completedAt;

    private Integer rating;          // 1 to 5 stars given after pickup

    @Column(length = 300)
    private String feedback;        // Thank-you note / review

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public Donation() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }

    public String getDietaryType() { return dietaryType; }
    public void setDietaryType(String dietaryType) { this.dietaryType = dietaryType; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getPreparedAt() { return preparedAt; }
    public void setPreparedAt(Instant preparedAt) { this.preparedAt = preparedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public User getDonor() { return donor; }
    public void setDonor(User donor) { this.donor = donor; }

    public User getClaimedBy() { return claimedBy; }
    public void setClaimedBy(User claimedBy) { this.claimedBy = claimedBy; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public Instant getClaimDeadline() { return claimDeadline; }
    public void setClaimDeadline(Instant claimDeadline) { this.claimDeadline = claimDeadline; }

    public String getPickupOtp() { return pickupOtp; }
    public void setPickupOtp(String pickupOtp) { this.pickupOtp = pickupOtp; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
