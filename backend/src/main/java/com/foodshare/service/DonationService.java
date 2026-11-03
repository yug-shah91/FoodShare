package com.foodshare.service;

import com.foodshare.dto.DonationDtos.*;
import com.foodshare.dto.PageResponse;
import com.foodshare.entity.ClaimRelease;
import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.ClaimReleaseRepository;
import com.foodshare.repository.DonationRepository;
import com.foodshare.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DonationService {

    private static final int CLAIM_MINUTES = 120;
    private static final int MAX_PREPARED_HOURS_AGO = 48;
    private static final int MAX_EXPIRY_DAYS_AHEAD = 7;

    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final ClaimReleaseRepository claimReleaseRepository;

    public DonationService(DonationRepository donationRepository, UserRepository userRepository,
                           ClaimReleaseRepository claimReleaseRepository) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.claimReleaseRepository = claimReleaseRepository;
    }

    // Donor creates a new listing
    @Transactional
    public DonationResponse create(CreateRequest request, String username) {
        Instant now = Instant.now();
        if (request.preparedAt().isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw bad("Preparation time cannot be in the future");
        }
        if (request.preparedAt().isBefore(now.minus(MAX_PREPARED_HOURS_AGO, ChronoUnit.HOURS))) {
            throw bad("Preparation time cannot be more than " + MAX_PREPARED_HOURS_AGO + " hours ago");
        }
        if (!request.expiresAt().isAfter(request.preparedAt())) {
            throw bad("Consume-before time must be after preparation time");
        }
        if (request.expiresAt().isAfter(now.plus(MAX_EXPIRY_DAYS_AHEAD, ChronoUnit.DAYS))) {
            throw bad("Consume-before time cannot be more than " + MAX_EXPIRY_DAYS_AHEAD + " days from now");
        }

        User donor = getUser(username);
        Donation d = new Donation();
        d.setFoodName(request.foodName().trim());
        d.setCategory(request.category());
        d.setQuantity(request.quantity());
        d.setStorage(request.storage());
        d.setDietaryType(request.dietaryType() != null && !request.dietaryType().isBlank() ? request.dietaryType() : "VEG");
        d.setArea(request.area().trim());
        d.setAddress(request.address().trim());

        // Default or chosen coordinates (defaults around city center if not provided)
        d.setLatitude(request.latitude() != null ? request.latitude() : 22.7196);
        d.setLongitude(request.longitude() != null ? request.longitude() : 75.8577);

        d.setNotes(request.notes());
        d.setAllergens(request.allergens());
        d.setPreparedAt(request.preparedAt());
        d.setExpiresAt(request.expiresAt());
        d.setDonor(donor);

        // Award karma points to donor
        donor.setKarmaPoints((donor.getKarmaPoints() != null ? donor.getKarmaPoints() : 100) + 25);
        userRepository.save(donor);

        return toResponse(donationRepository.save(d));
    }

    private Pageable pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }

    // Public active listings (paged)
    @Transactional(readOnly = true)
    public PageResponse<PublicDonationResponse> getActive(int page, int size) {
        return PageResponse.of(donationRepository
                .findByStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(Donation.Status.ACTIVE, Instant.now(), pageOf(page, size)),
                this::toPublicResponse);
    }

    // All active listings for map view
    @Transactional(readOnly = true)
    public List<PublicDonationResponse> getActiveMap() {
        return donationRepository
                .findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(Donation.Status.ACTIVE, Instant.now())
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    // Donor's own listings
    @Transactional(readOnly = true)
    public PageResponse<DonationResponse> getMine(String username, int page, int size) {
        return PageResponse.of(donationRepository
                .findByDonorOrderByCreatedAtDescIdDesc(getUser(username), pageOf(page, size)), this::toResponse);
    }

    // Recipient's claimed listings
    @Transactional(readOnly = true)
    public PageResponse<DonationResponse> getClaimed(String username, int page, int size) {
        return PageResponse.of(donationRepository
                .findByClaimedByOrderByCreatedAtDescIdDesc(getUser(username), pageOf(page, size)), this::toResponse);
    }

    // Recipient's missed pickups
    @Transactional(readOnly = true)
    public PageResponse<ClaimHistoryResponse> getClaimHistory(String username, int page, int size) {
        return PageResponse.of(claimReleaseRepository
                .findByRecipientOrderByReleasedAtDescIdDesc(getUser(username), pageOf(page, size)),
                this::toHistoryResponse);
    }

    // Recipient claims food -> Generates 6-digit OTP
    @Transactional
    public DonationResponse claim(Long id, String username) {
        User recipient = getUser(username);
        Donation d = getDonation(id);

        Instant now = Instant.now();
        Instant deadline = now.plus(CLAIM_MINUTES, ChronoUnit.MINUTES);
        if (deadline.isAfter(d.getExpiresAt())) {
            deadline = d.getExpiresAt();
        }

        // Generate a secure 6-digit verification code
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        int updated = donationRepository.claimIfActive(id, recipient, now, deadline, otp,
                Donation.Status.CLAIMED, Donation.Status.ACTIVE);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sorry, this donation was already claimed by someone else, or it has expired");
        }

        recipient.setKarmaPoints((recipient.getKarmaPoints() != null ? recipient.getKarmaPoints() : 50) + 10);
        userRepository.save(recipient);

        return toResponse(getDonation(id));
    }

    // Complete directly by recipient (or backwards compatibility)
    @Transactional
    public DonationResponse complete(Long id, String username) {
        User user = getUser(username);
        Donation d = getDonation(id);

        int updated = donationRepository.completeIfClaimed(id, user, Instant.now(),
                Donation.Status.COMPLETED, Donation.Status.CLAIMED);
        if (updated == 0) {
            if (d.getClaimedBy() != null && !d.getClaimedBy().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your pickup");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This pickup cannot be completed: it is not currently claimed by you, or its pickup deadline or consume-before time has passed");
        }
        return toResponse(getDonation(id));
    }

    // Verify 6-digit OTP at pickup handover (can be submitted by Donor or Recipient)
    @Transactional
    public DonationResponse verifyPickupOtp(Long id, String otp, String username) {
        User caller = getUser(username);
        Donation d = getDonation(id);

        // Security check: only donor of this donation or recipient who claimed it can verify
        boolean isDonor = d.getDonor() != null && d.getDonor().getId().equals(caller.getId());
        boolean isRecipient = d.getClaimedBy() != null && d.getClaimedBy().getId().equals(caller.getId());
        if (!isDonor && !isRecipient && caller.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to verify this pickup");
        }

        if (d.getStatus() != Donation.Status.CLAIMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This donation is not currently in CLAIMED status");
        }

        if (d.getPickupOtp() == null || !d.getPickupOtp().trim().equals(otp.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 6-digit OTP. Please verify the code with the recipient.");
        }

        Instant now = Instant.now();
        if (d.getClaimDeadline() != null && now.isAfter(d.getClaimDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The pickup deadline for this claim has already passed.");
        }
        if (now.isAfter(d.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This food has passed its consume-before time.");
        }

        int updated = donationRepository.completeWithOtp(id, otp.trim(), now,
                Donation.Status.COMPLETED, Donation.Status.CLAIMED);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup confirmation failed. It may have been updated already.");
        }

        // Award bonus karma
        if (d.getDonor() != null) {
            User donor = d.getDonor();
            donor.setKarmaPoints((donor.getKarmaPoints() != null ? donor.getKarmaPoints() : 100) + 50);
            userRepository.save(donor);
        }

        return toResponse(getDonation(id));
    }

    // Submit recipient rating & feedback after completed pickup
    @Transactional
    public DonationResponse addFeedback(Long id, FeedbackRequest feedbackReq, String username) {
        User recipient = getUser(username);
        Donation d = getDonation(id);

        if (d.getClaimedBy() == null || !d.getClaimedBy().getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient who collected this food can leave feedback");
        }
        if (d.getStatus() != Donation.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback can only be left on completed pickups");
        }

        d.setRating(feedbackReq.rating());
        d.setFeedback(feedbackReq.feedback());
        donationRepository.save(d);

        // Adjust donor rating
        User donor = d.getDonor();
        if (donor != null) {
            double currentRating = donor.getRating() != null ? donor.getRating() : 5.0;
            double updatedRating = Math.round(((currentRating * 4.0 + feedbackReq.rating()) / 5.0) * 10.0) / 10.0;
            donor.setRating(updatedRating);
            userRepository.save(donor);
        }

        return toResponse(d);
    }

    // Cancel own listing
    @Transactional
    public DonationResponse cancel(Long id, String username) {
        User donor = getUser(username);
        Donation d = getDonation(id);
        if (!d.getDonor().getId().equals(donor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your donation");
        }

        int updated = donationRepository.cancelIfActive(id, donor, Instant.now(),
                Donation.Status.CANCELLED, Donation.Status.ACTIVE);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only active, unexpired donations can be cancelled (someone may have just claimed it)");
        }
        return toResponse(getDonation(id));
    }

    // Global community impact stats
    @Transactional(readOnly = true)
    public ImpactStatsResponse getImpactStats() {
        Long completedQuantity = donationRepository.sumQuantityByStatus(Donation.Status.COMPLETED);
        long totalMeals = completedQuantity != null ? completedQuantity : 0;
        // ~2.5 kg CO2 saved per meal rescued from landfill
        double co2Saved = Math.round(totalMeals * 2.5 * 10.0) / 10.0;
        long active = donationRepository.countByStatus(Donation.Status.ACTIVE);
        long donors = donationRepository.countDistinctDonors();
        return new ImpactStatsResponse(totalMeals, co2Saved, active, donors);
    }

    // Helpers
    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Donation getDonation(Long id) {
        return donationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));
    }

    private ClaimHistoryResponse toHistoryResponse(ClaimRelease r) {
        Donation d = r.getDonation();
        return new ClaimHistoryResponse(
                r.getId(), d.getId(), d.getFoodName(), d.getCategory().name(), d.getQuantity(),
                d.getStorage().name(), d.getDonor() != null ? d.getDonor().getFullName() : "Donor",
                d.getExpiresAt(), d.getStatus().name(),
                r.getClaimedAt(), r.getClaimDeadline(), r.getReleasedAt());
    }

    private static String areaOf(Donation d) {
        return d.getArea() == null || d.getArea().isBlank() ? "Area not specified" : d.getArea();
    }

    private PublicDonationResponse toPublicResponse(Donation d) {
        long minutesLeft = Math.max(0, Duration.between(Instant.now(), d.getExpiresAt()).toMinutes());
        User donor = d.getDonor();
        return new PublicDonationResponse(
                d.getId(), d.getFoodName(), d.getCategory().name(), d.getDietaryType(), d.getQuantity(),
                d.getStorage().name(), areaOf(d), d.getLatitude(), d.getLongitude(), d.getAllergens(),
                d.getPreparedAt(), d.getExpiresAt(), minutesLeft, d.getStatus().name(),
                donor != null ? donor.getFullName() : "Donor",
                donor != null ? donor.getDonorType() : "Individual",
                donor != null ? donor.getOrganizationName() : null,
                donor != null && donor.getRating() != null ? donor.getRating() : 4.9,
                donor != null && donor.getBadge() != null ? donor.getBadge() : "Community Donor",
                d.getCreatedAt());
    }

    public DonationResponse toResponse(Donation d) {
        long minutesLeft = Math.max(0, Duration.between(Instant.now(), d.getExpiresAt()).toMinutes());
        User donor = d.getDonor();
        User recipient = d.getClaimedBy();
        return new DonationResponse(
                d.getId(), d.getFoodName(), d.getCategory().name(), d.getDietaryType(), d.getQuantity(),
                d.getStorage().name(), areaOf(d), d.getAddress(), d.getLatitude(), d.getLongitude(),
                d.getNotes(), d.getAllergens(),
                d.getPreparedAt(), d.getExpiresAt(), minutesLeft, d.getStatus().name(),
                donor != null ? donor.getFullName() : "Donor",
                donor != null ? donor.getPhone() : null,
                donor != null ? donor.getDonorType() : "Individual",
                donor != null ? donor.getOrganizationName() : null,
                donor != null && donor.getRating() != null ? donor.getRating() : 4.9,
                donor != null && donor.getBadge() != null ? donor.getBadge() : "Community Donor",
                recipient != null ? recipient.getFullName() : null,
                recipient != null ? recipient.getPhone() : null,
                d.getClaimedAt(),
                d.getClaimDeadline(),
                d.getPickupOtp(),
                d.getCompletedAt(),
                d.getRating(),
                d.getFeedback(),
                d.getCreatedAt());
    }
}
