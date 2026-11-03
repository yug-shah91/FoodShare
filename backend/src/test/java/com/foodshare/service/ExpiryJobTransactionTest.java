package com.foodshare.service;

import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.ClaimReleaseRepository;
import com.foodshare.repository.DonationRepository;
import com.foodshare.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Tests the REAL scheduler entry point (ExpiryJob.run, the method with @Scheduled), with NO transaction around the test.
//
// Why a separate class: @DataJpaTest normally wraps every test in a transaction of its own. Inside that transaction the
// bulk "update ..." queries work even if the production code forgot @Transactional, so such tests cannot notice a
// missing transaction. Here NOT_SUPPORTED switches the test transaction off: if ExpiryJob -> ExpiryService did not open
// its own transactions, the bulk updates would fail with "TransactionRequiredException".
// Because nothing is rolled back automatically, the test cleans up after itself.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ExpiryService.class, ExpiryJob.class})
class ExpiryJobTransactionTest {

    @Autowired ExpiryJob expiryJob;
    @Autowired DonationRepository donations;
    @Autowired ClaimReleaseRepository releases;
    @Autowired UserRepository users;

    private User donor;
    private User recipient;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        donor = users.save(new User("donor", "x", "Donor", User.Role.DONOR));
        recipient = users.save(new User("recipient", "x", "Recipient", User.Role.RECIPIENT));
    }

    @AfterEach
    void cleanDatabase() {
        releases.deleteAll();
        donations.deleteAll();
        users.deleteAll();
    }

    private Donation save(Donation.Status status, Instant expiresAt, Instant claimDeadline) {
        Donation d = new Donation();
        d.setFoodName("Food");
        d.setCategory(Donation.Category.COOKED_MEALS);
        d.setQuantity(5);
        d.setStorage(Donation.Storage.ROOM_TEMPERATURE);
        d.setArea("Area");
        d.setAddress("Somewhere");
        d.setPreparedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        d.setExpiresAt(expiresAt);
        d.setStatus(status);
        d.setDonor(donor);
        if (status == Donation.Status.CLAIMED) {
            d.setClaimedBy(recipient);
            d.setClaimedAt(Instant.now().minus(3, ChronoUnit.HOURS));
            d.setClaimDeadline(claimDeadline);
        }
        return donations.save(d);
    }

    @Test
    void theScheduledMethodWorksWithoutAnyTransactionAroundIt() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();   // the test really has none

        Donation expiring = save(Donation.Status.ACTIVE, Instant.now().minusSeconds(5), null);
        Donation missed = save(Donation.Status.CLAIMED, Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().minusSeconds(5));

        expiryJob.run();   // exactly what the scheduler calls every minute

        assertThat(donations.findById(expiring.getId()).orElseThrow().getStatus()).isEqualTo(Donation.Status.EXPIRED);
        Donation released = donations.findById(missed.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(Donation.Status.ACTIVE);
        assertThat(released.getClaimedBy()).isNull();
        assertThat(releases.count()).isEqualTo(1);
    }

    @Test
    void runningTheScheduledMethodAgainDoesNotDuplicateHistory() {
        save(Donation.Status.CLAIMED, Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().minusSeconds(5));
        expiryJob.run();
        expiryJob.run();
        assertThat(releases.count()).isEqualTo(1);
    }
}
