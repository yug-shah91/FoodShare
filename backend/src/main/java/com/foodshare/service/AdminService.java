package com.foodshare.service;

import com.foodshare.dto.AdminDtos.*;
import com.foodshare.dto.DonationDtos.DonationResponse;
import com.foodshare.dto.PageResponse;
import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.DonationRepository;
import com.foodshare.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final DonationService donationService;

    public AdminService(DonationRepository donationRepository, UserRepository userRepository,
                        DonationService donationService) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.donationService = donationService;
    }

    // The numbers at the top of the dashboard (the tables below them are paged: see getUsers / getDonations)
    @Transactional(readOnly = true)
    public Stats getStats() {
        // Meals saved = servings that were really picked up (COMPLETED only).
        // A CLAIMED listing is only a reservation, so it is not counted.
        // The database adds them up, so we do not load every donation into memory.
        Long completedServings = donationRepository.sumQuantityByStatus(Donation.Status.COMPLETED);
        long meals = completedServings == null ? 0 : completedServings;
        double co2 = Math.round(meals * 0.26 * 10) / 10.0;   // rough estimate: ~0.26 kg CO2 per meal

        return new Stats(
                donationRepository.count(),
                donationRepository.countByStatus(Donation.Status.ACTIVE),
                donationRepository.countByStatus(Donation.Status.CLAIMED),
                donationRepository.countByStatus(Donation.Status.COMPLETED),
                donationRepository.countByStatus(Donation.Status.EXPIRED),
                meals, co2);
    }

    // One page of users (oldest first). Activity counts come from 2 grouped queries for just these users.
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> getUsers(int page, int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(clampPage(page), clampSize(size), Sort.by("id")));
        List<Long> ids = users.getContent().stream().map(User::getId).toList();

        Map<Long, Long> donationCounts = ids.isEmpty() ? Map.of() : toMap(donationRepository.countDonationsPerDonor(ids));
        Map<Long, Long> claimCounts = ids.isEmpty() ? Map.of() : toMap(donationRepository.countClaimsPerRecipient(ids));
        return PageResponse.of(users, u -> toSummary(u, donationCounts, claimCounts));
    }

    // One page of donations, newest first (createdAt, then id as a tie-breaker: see DonationRepository)
    @Transactional(readOnly = true)
    public PageResponse<DonationResponse> getDonations(int page, int size) {
        return PageResponse.of(donationRepository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(clampPage(page), clampSize(size))),
                donationService::toResponse);
    }

    private int clampPage(int page) {
        return Math.max(page, 0);
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 50);
    }

    // turns rows like [userId, count] into a Map: userId -> count
    private Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private UserSummary toSummary(User u, Map<Long, Long> donationCounts, Map<Long, Long> claimCounts) {
        long count = switch (u.getRole()) {
            case DONOR -> donationCounts.getOrDefault(u.getId(), 0L);
            case RECIPIENT -> claimCounts.getOrDefault(u.getId(), 0L);
            case ADMIN -> 0L;
        };
        return new UserSummary(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(), count);
    }
}
