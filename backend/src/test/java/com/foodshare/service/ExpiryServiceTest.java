package com.foodshare.service;

import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.ClaimReleaseRepository;
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

// Runs against an in-memory H2 database (Spring replaces the real datasource in @DataJpaTest).
// "now" is passed in, so every boundary moment is tested exactly.
@DataJpaTest
@Import({ExpiryService.class, ExpiryJob.class})
class ExpiryServiceTest {

    @Autowired ExpiryJob expiryJob;
    @Autowired ExpiryService expiryService;
    @Autowired DonationRepository donations;
    @Autowired UserRepository users;
    @Autowired ClaimReleaseRepository releases;

    private final Instant now = Instant.parse("2026-11-03T12:00:00Z");
    private User donor;
    private User recipient;

    @BeforeEach
    void setUp() {
        donor = users.save(new User("donor", "x", "Donor", User.Role.DONOR));
        recipient = users.save(new User("recipient", "x", "Recipient", User.Role.RECIPIENT));
    }

    private Donation donation(Donation.Status status, Instant expiresAt, Instant claimDeadline) {
        Donation d = new Donation();
        d.setFoodName("Food");
        d.setCategory(Donation.Category.COOKED_MEALS);
        d.setQuantity(5);
        d.setStorage(Donation.Storage.ROOM_TEMPERATURE);
        d.setAddress("Somewhere");
        d.setPreparedAt(now.minus(2, ChronoUnit.HOURS));
        d.setExpiresAt(expiresAt);
        d.setStatus(status);
        d.setDonor(donor);
        if (status == Donation.Status.CLAIMED) {
            d.setClaimedBy(recipient);
            d.setClaimedAt(now.minus(2, ChronoUnit.HOURS));
            d.setClaimDeadline(claimDeadline);
        }
        return donations.saveAndFlush(d);
    }

    private Donation reload(Donation d) {
        return donations.findById(d.getId()).orElseThrow();
    }

    @Test
    void activeFoodThatExpiresBecomesExpired() {
        Donation d = donation(Donation.Status.ACTIVE, now.minusSeconds(1), null);
        expiryJob.cleanUp(now);
        assertThat(reload(d).getStatus()).isEqualTo(Donation.Status.EXPIRED);
    }

    @Test
    void activeFoodExpiringExactlyNowIsExpired() {
        Donation d = donation(Donation.Status.ACTIVE, now, null);
        expiryJob.cleanUp(now);
        assertThat(reload(d).getStatus()).isEqualTo(Donation.Status.EXPIRED);
    }

    @Test
    void activeFoodThatIsNotYetExpiredIsUntouched() {
        Donation d = donation(Donation.Status.ACTIVE, now.plusSeconds(1), null);
        expiryJob.cleanUp(now);
        assertThat(reload(d).getStatus()).isEqualTo(Donation.Status.ACTIVE);
    }

    @Test
    void claimedFoodThatExpiresBecomesExpiredAndKeepsTheRecipient() {
        Donation d = donation(Donation.Status.CLAIMED, now.minusSeconds(1), now.plusSeconds(600));
        expiryJob.cleanUp(now);
        Donation after = reload(d);
        assertThat(after.getStatus()).isEqualTo(Donation.Status.EXPIRED);
        assertThat(after.getClaimedBy().getId()).isEqualTo(recipient.getId());
        assertThat(releases.count()).isZero();
    }

    @Test
    void claimDeadlineBeforeFoodExpiryReleasesTheClaimAndKeepsAHistoryRow() {
        Donation d = donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.minusSeconds(1));
        expiryJob.cleanUp(now);

        Donation after = reload(d);
        assertThat(after.getStatus()).isEqualTo(Donation.Status.ACTIVE);
        assertThat(after.getClaimedBy()).isNull();
        assertThat(after.getClaimDeadline()).isNull();

        assertThat(releases.count()).isEqualTo(1);
        assertThat(releases.countByRecipientId(recipient.getId())).isEqualTo(1);
    }

    @Test
    void claimDeadlineAndFoodExpiryAtTheSameMomentEndsAsExpiredNotActive() {
        Donation d = donation(Donation.Status.CLAIMED, now, now);
        expiryJob.cleanUp(now);
        assertThat(reload(d).getStatus()).isEqualTo(Donation.Status.EXPIRED);
        assertThat(releases.count()).isZero();
    }

    @Test
    void claimWithTimeLeftIsUntouched() {
        Donation d = donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.plusSeconds(1));
        expiryJob.cleanUp(now);
        assertThat(reload(d).getStatus()).isEqualTo(Donation.Status.CLAIMED);
    }

    @Test
    void completedAndCancelledListingsAreNeverChanged() {
        Donation done = donation(Donation.Status.COMPLETED, now.minusSeconds(1), null);
        Donation cancelled = donation(Donation.Status.CANCELLED, now.minusSeconds(1), null);
        expiryJob.cleanUp(now);
        assertThat(reload(done).getStatus()).isEqualTo(Donation.Status.COMPLETED);
        assertThat(reload(cancelled).getStatus()).isEqualTo(Donation.Status.CANCELLED);
    }

    @Test
    void runningTheCleanUpTwiceChangesNothingTheSecondTime() {
        Donation expired = donation(Donation.Status.ACTIVE, now.minusSeconds(1), null);
        Donation released = donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.minusSeconds(1));
        Donation coincide = donation(Donation.Status.CLAIMED, now, now);

        expiryJob.cleanUp(now);
        expiryJob.cleanUp(now);
        expiryJob.cleanUp(now.plusSeconds(60));   // and a later run, as the real scheduler would do

        assertThat(reload(expired).getStatus()).isEqualTo(Donation.Status.EXPIRED);
        assertThat(reload(released).getStatus()).isEqualTo(Donation.Status.ACTIVE);
        assertThat(reload(coincide).getStatus()).isEqualTo(Donation.Status.EXPIRED);
        assertThat(releases.count()).isEqualTo(1);   // the release was recorded once, not once per run
    }

    @Test
    void releasingTheSameClaimTwiceOnlyWorksOnce() {
        // Two servers read the same abandoned claim (same version). Only one of the two updates may win.
        Donation d = donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.minusSeconds(1));
        Long version = d.getVersion();

        int first = donations.releaseAbandonedClaim(d.getId(), version, Donation.Status.ACTIVE, Donation.Status.CLAIMED, now);
        int second = donations.releaseAbandonedClaim(d.getId(), version, Donation.Status.ACTIVE, Donation.Status.CLAIMED, now);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void aStaleCopyOfAClaimThatWasJustReleasedWritesNoSecondHistoryRow() {
        donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.minusSeconds(1));
        expiryService.releaseAbandonedBatch(now, 10);
        expiryService.releaseAbandonedBatch(now, 10);   // "the other server" arrives a moment later
        assertThat(releases.count()).isEqualTo(1);
    }

    @Test
    void manyAbandonedClaimsAreReleasedInSmallBatches() {
        for (int i = 0; i < 5; i++) donation(Donation.Status.CLAIMED, now.plus(1, ChronoUnit.HOURS), now.minusSeconds(10 - i));

        assertThat(expiryService.releaseAbandonedBatch(now, 2)).isEqualTo(2);   // a full batch: there may be more
        assertThat(releases.count()).isEqualTo(2);
        assertThat(expiryService.releaseAbandonedBatch(now, 2)).isEqualTo(2);
        assertThat(expiryService.releaseAbandonedBatch(now, 2)).isEqualTo(1);   // last, smaller batch: done
        assertThat(releases.count()).isEqualTo(5);
        assertThat(donations.countByStatus(Donation.Status.ACTIVE)).isEqualTo(5);
    }
}
