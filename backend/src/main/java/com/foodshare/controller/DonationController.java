package com.foodshare.controller;

import com.foodshare.dto.DonationDtos.*;
import com.foodshare.dto.PageResponse;
import com.foodshare.service.DonationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    // Public active listings (paged)
    @GetMapping
    public PageResponse<PublicDonationResponse> getActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return donationService.getActive(page, size);
    }

    // Interactive map markers: returns all active donations with GPS coordinates and public info
    @GetMapping("/map")
    public List<PublicDonationResponse> getActiveMap() {
        return donationService.getActiveMap();
    }

    // Global community impact statistics (meals saved, CO2 reduced, active listings)
    @GetMapping("/impact")
    public ImpactStatsResponse getImpactStats() {
        return donationService.getImpactStats();
    }

    // Donor creates listing
    @PostMapping
    public DonationResponse create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        return donationService.create(request, auth.getName());
    }

    // Donor's own listings
    @GetMapping("/mine")
    public PageResponse<DonationResponse> mine(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return donationService.getMine(auth.getName(), page, size);
    }

    // Recipient's claimed pickups
    @GetMapping("/claimed")
    public PageResponse<DonationResponse> claimed(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return donationService.getClaimed(auth.getName(), page, size);
    }

    // Recipient's claim history (released/missed)
    @GetMapping("/claim-history")
    public PageResponse<ClaimHistoryResponse> claimHistory(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return donationService.getClaimHistory(auth.getName(), page, size);
    }

    // Recipient claims food -> Generates 6-digit OTP
    @PostMapping("/{id}/claim")
    public DonationResponse claim(@PathVariable Long id, Authentication auth) {
        return donationService.claim(id, auth.getName());
    }

    // Complete pickup directly
    @PostMapping("/{id}/complete")
    public DonationResponse complete(@PathVariable Long id, Authentication auth) {
        return donationService.complete(id, auth.getName());
    }

    // Secure OTP Verification: Donor or Recipient enters the 6-digit OTP to confirm handover
    @PostMapping("/{id}/verify-pickup-otp")
    public DonationResponse verifyPickupOtp(
            @PathVariable Long id,
            @Valid @RequestBody VerifyOtpRequest request,
            Authentication auth) {
        return donationService.verifyPickupOtp(id, request.otp(), auth.getName());
    }

    // Recipient feedback & rating after successful pickup
    @PostMapping("/{id}/feedback")
    public DonationResponse addFeedback(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request,
            Authentication auth) {
        return donationService.addFeedback(id, request, auth.getName());
    }

    // Donor cancels listing
    @PostMapping("/{id}/cancel")
    public DonationResponse cancel(@PathVariable Long id, Authentication auth) {
        return donationService.cancel(id, auth.getName());
    }
}
