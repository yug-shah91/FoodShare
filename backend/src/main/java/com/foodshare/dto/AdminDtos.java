package com.foodshare.dto;

public class AdminDtos {

    public record UserSummary(Long id, String username, String fullName, String role, long activityCount) {}

    public record Stats(
            long totalListings,
            long activeListings,
            long claimedListings,
            long completedListings,
            long expiredListings,
            long mealsSaved,          // servings that were actually picked up (COMPLETED only)
            double co2SavedKg) {}      // rough estimate, not a measured value
    // (the users and donations tables come from their own paged endpoints, see AdminController)
}
