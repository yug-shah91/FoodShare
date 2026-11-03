package com.foodshare.service;

import com.foodshare.dto.DonationDtos.ClaimHistoryResponse;
import com.foodshare.dto.PageResponse;
import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.DonationRepository;
import com.foodshare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

// A missed pickup must end up in the recipient's claim history (and only in theirs), newest first, paged.
@DataJpaTest
@Import({DonationService.class, ExpiryService.class, ExpiryJob.class})
class ClaimHistoryTest {

    @Autowired DonationService donationService;
    @Autowired ExpiryJob expiryJob;
    @Autowired DonationRepository donations;
    @Autowired UserRepository users;

    private final Instant now = Instant.parse("2026-11-03T12:00:00Z");
    private User donor;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        donor = users.save(new User("donor", "x", "Donor", User.Role.DONOR));
        alice = users.save(new User("alice", "x", "Alice", User.Role.RECIPIENT));
        bob = users.save(new User("bob", "x", "Bob", User.Role.RECIPIENT));
    }

    private Donation claimed(String name, User recipient, Instant claimedAt, Instant deadline) {
        Donation d = new Donation();
        d.setFoodName(name);
        d.setCategory(Donation.Category.COOKED_MEALS);
        d.setQuantity(4);
        d.setStorage(Donation.Storage.REFRIGERATED);
        d.setAddress("Secret street 1");
        d.setPreparedAt(now.minus(5, ChronoUnit.HOURS));
        d.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        d.setStatus(Donation.Status.CLAIMED);
        d.setDonor(donor);
        d.setClaimedBy(recipient);
        d.setClaimedAt(claimedAt);
        d.setClaimDeadline(deadline);
        return donations.saveAndFlush(d);
    }

    @Test
    void missedPickupAppearsInTheRecipientsHistoryWithAllTheTimes() {
        Instant claimedAt = now.minus(3, ChronoUnit.HOURS);
        Instant deadline = now.minus(1, ChronoUnit.HOURS);
        Donation d = claimed("Soup", alice, claimedAt, deadline);

        expiryJob.cleanUp(now);

        PageResponse<ClaimHistoryResponse> page = donationService.getClaimHistory("alice", 0, 20);
        assertThat(page.items()).hasSize(1);
        ClaimHistoryResponse h = page.items().get(0);
        assertThat(h.donationId()).isEqualTo(d.getId());
        assertThat(h.foodName()).isEqualTo("Soup");
        assertThat(h.donorName()).isEqualTo("Donor");
        assertThat(h.claimedAt()).isEqualTo(claimedAt);
        assertThat(h.missedDeadline()).isEqualTo(deadline);
        assertThat(h.releasedAt()).isEqualTo(now);
        assertThat(h.donationStatus()).isEqualTo("ACTIVE");   // it is available to others again
    }

    @Test
    void historyIsPrivateToTheRecipientWhoMissedIt() {
        claimed("Soup", alice, now.minus(3, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        expiryJob.cleanUp(now);
        assertThat(donationService.getClaimHistory("bob", 0, 20).items()).isEmpty();
    }

    @Test
    void claimsStillInTimeAreNotInTheHistory() {
        claimed("Soup", alice, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        expiryJob.cleanUp(now);
        assertThat(donationService.getClaimHistory("alice", 0, 20).items()).isEmpty();
    }

    @Test
    void newestReleaseFirstAndPaged() {
        for (int i = 0; i < 3; i++) {
            claimed("Food" + i, alice, now.minus(5, ChronoUnit.HOURS), now.minus(4, ChronoUnit.HOURS));
            expiryJob.cleanUp(now.plus(i, ChronoUnit.MINUTES));   // released at now+0, now+1, now+2 minutes
        }
        PageResponse<ClaimHistoryResponse> first = donationService.getClaimHistory("alice", 0, 2);
        assertThat(first.items()).extracting(ClaimHistoryResponse::foodName).containsExactly("Food2", "Food1");
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.hasMore()).isTrue();

        PageResponse<ClaimHistoryResponse> second = donationService.getClaimHistory("alice", 1, 2);
        assertThat(second.items()).extracting(ClaimHistoryResponse::foodName).containsExactly("Food0");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void eachRecipientGetsTheirOwnEntryWhenTheSameDonationIsMissedTwice() {
        Donation d = claimed("Soup", alice, now.minus(5, ChronoUnit.HOURS), now.minus(4, ChronoUnit.HOURS));
        expiryJob.cleanUp(now);
        // claimed again (by Bob), missed again
        d = donations.findById(d.getId()).orElseThrow();
        d.setStatus(Donation.Status.CLAIMED);
        d.setClaimedBy(bob);
        d.setClaimedAt(now.plus(1, ChronoUnit.HOURS));
        d.setClaimDeadline(now.plus(2, ChronoUnit.HOURS));
        donations.saveAndFlush(d);
        expiryJob.cleanUp(now.plus(3, ChronoUnit.HOURS));

        assertThat(donationService.getClaimHistory("alice", 0, 20).items()).hasSize(1);
        assertThat(donationService.getClaimHistory("bob", 0, 20).items()).hasSize(1);
    }
}
