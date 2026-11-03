package com.foodshare.dto;

import com.foodshare.entity.Donation;
import jakarta.validation.constraints.*;

import java.time.Instant;

public class DonationDtos {

    // What the donor sends when creating a listing
    public record CreateRequest(
            @NotBlank @Size(max = 100, message = "Food name is too long (max 100)") String foodName,
            @NotNull Donation.Category category,
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1000, message = "Quantity cannot be more than 1000") int quantity,
            @NotNull Donation.Storage storage,
            @Size(max = 30) String dietaryType,
            @NotBlank @Size(max = 100, message = "Area is too long (max 100)") String area,
            @NotBlank @Size(max = 200, message = "Address is too long (max 200)") String address,
            Double latitude,
            Double longitude,
            @Size(max = 300, message = "Notes are too long (max 300)") String notes,
            @Size(max = 200, message = "Allergen text is too long (max 200)") String allergens,
            @NotNull(message = "Preparation time is required") Instant preparedAt,
            @NotNull(message = "Consume-before time is required")
            @Future(message = "Consume-before time must be in the future") Instant expiresAt) {}

    // What everybody who browses the available food gets.
    // Contains approximate area and coordinates for map pins, donor profile details (name, org, rating, badges),
    // but NO private address and NO private notes.
    public record PublicDonationResponse(
            Long id,
            String foodName,
            String category,
            String dietaryType,
            int quantity,
            String storage,
            String area,
            Double latitude,
            Double longitude,
            String allergens,
            Instant preparedAt,
            Instant expiresAt,
            long minutesLeft,
            String status,
            String donorName,
            String donorType,
            String donorOrg,
            Double donorRating,
            String donorBadge,
            Instant createdAt) {}

    // The full listing: for its donor, the recipient who claimed it, and admins.
    // Contains the exact address, notes, contact phone numbers, and the secure pickupOtp!
    public record DonationResponse(
            Long id,
            String foodName,
            String category,
            String dietaryType,
            int quantity,
            String storage,
            String area,
            String address,
            Double latitude,
            Double longitude,
            String notes,
            String allergens,
            Instant preparedAt,
            Instant expiresAt,
            long minutesLeft,
            String status,
            String donorName,
            String donorPhone,
            String donorType,
            String donorOrg,
            Double donorRating,
            String donorBadge,
            String claimedByName,
            String claimedByPhone,
            Instant claimedAt,
            Instant claimDeadline,
            String pickupOtp,
            Instant completedAt,
            Integer rating,
            String feedback,
            Instant createdAt) {}

    // Verification request with the 6-digit OTP
    public record VerifyOtpRequest(
            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
            String otp) {}

    // Post-pickup rating & gratitude note
    public record FeedbackRequest(
            @Min(value = 1, message = "Rating must be between 1 and 5")
            @Max(value = 5, message = "Rating must be between 1 and 5")
            int rating,
            @Size(max = 300, message = "Feedback cannot exceed 300 characters")
            String feedback) {}

    // Impact & Community rescue statistics
    public record ImpactStatsResponse(
            long totalMealsRescued,
            double co2SavedKg,
            long activeListings,
            long totalDonorsCount) {}

    // One missed pickup in a recipient's claim history.
    public record ClaimHistoryResponse(
            Long id,
            Long donationId,
            String foodName,
            String category,
            int quantity,
            String storage,
            String donorName,
            Instant expiresAt,
            String donationStatus,
            Instant claimedAt,
            Instant missedDeadline,
            Instant releasedAt) {}
}
