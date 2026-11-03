package com.foodshare.service;

import com.foodshare.dto.DonationDtos.DonationResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Real threads, real database transactions (no test-wide transaction, see ExpiryJobTransactionTest for why).
// The database must decide every race: exactly one winner, never two.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DonationService.class, ExpiryService.class})
class ConcurrentClaimTest {

    private static final int RECIPIENTS = 8;
    private static final int ROUNDS = 10;

    @Autowired DonationService service;
    @Autowired ExpiryService expiry;   // the Spring bean (with its transaction proxy), never "new ExpiryService(...)"
    @Autowired DonationRepository donations;
    @Autowired ClaimReleaseRepository releases;
    @Autowired UserRepository users;

    private User donor;
    private final List<User> recipients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        donor = users.save(new User("donor", "x", "Donor", User.Role.DONOR));
        for (int i = 0; i < RECIPIENTS; i++) recipients.add(users.save(new User("r" + i, "x", "R" + i, User.Role.RECIPIENT)));

        // Warm up Hibernate SQM AST plan cache sequentially
        Donation warmup = newDonation();
        try { service.claim(warmup.getId(), recipients.get(0).getUsername()); } catch (Exception ignored) {}
        donations.deleteById(warmup.getId());
    }

    @AfterEach
    void cleanDatabase() {
        recipients.clear();
        releases.deleteAll();
        donations.deleteAll();
        users.deleteAll();
    }

    private Donation newDonation() {
        Donation d = new Donation();
        d.setFoodName("Food");
        d.setCategory(Donation.Category.COOKED_MEALS);
        d.setQuantity(5);
        d.setStorage(Donation.Storage.ROOM_TEMPERATURE);
        d.setArea("Area");
        d.setAddress("Somewhere");
        d.setPreparedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        d.setExpiresAt(Instant.now().plus(5, ChronoUnit.HOURS));
        d.setDonor(donor);
        return donations.save(d);
    }

    // Starts all tasks at the same moment and returns for each one: null = it worked, otherwise the HTTP status it got
    private List<HttpStatus> raceAll(List<Callable<DonationResponse>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        for (Callable<DonationResponse> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    return null;
                } catch (ResponseStatusException e) {
                    return HttpStatus.valueOf(e.getStatusCode().value());
                }
            }));
        }
        ready.await();
        go.countDown();
        List<HttpStatus> results = new ArrayList<>();
        for (Future<HttpStatus> f : futures) results.add(f.get());
        pool.shutdown();
        return results;
    }

    @Test
    void manyRecipientsClaimingTheSameFoodAtOnceExactlyOneWins() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Donation d = newDonation();
            List<Callable<DonationResponse>> tasks = new ArrayList<>();
            for (User r : recipients) tasks.add(() -> service.claim(d.getId(), r.getUsername()));

            List<HttpStatus> results = raceAll(tasks);

            assertThat(results.stream().filter(s -> s == null).count()).as("winners in round " + round).isEqualTo(1);
            assertThat(results.stream().filter(s -> s == HttpStatus.CONFLICT).count()).isEqualTo(RECIPIENTS - 1);
            Donation after = donations.findById(d.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(Donation.Status.CLAIMED);
            assertThat(after.getClaimedBy()).isNotNull();
        }
    }

    @Test
    void claimAndCancelAtTheSameMomentExactlyOneWins() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Donation d = newDonation();
            List<Callable<DonationResponse>> tasks = List.of(
                    () -> service.claim(d.getId(), recipients.get(0).getUsername()),
                    () -> service.cancel(d.getId(), donor.getUsername()));

            List<HttpStatus> results = raceAll(tasks);

            assertThat(results.stream().filter(s -> s == null).count()).as("winners in round " + round).isEqualTo(1);
            assertThat(results.stream().filter(s -> s == HttpStatus.CONFLICT).count()).isEqualTo(1);
            Donation.Status finalStatus = donations.findById(d.getId()).orElseThrow().getStatus();
            // the winner decides the final state: it can never be "claimed AND cancelled"
            assertThat(finalStatus).isIn(Donation.Status.CLAIMED, Donation.Status.CANCELLED);
            assertThat(results.get(0) == null).isEqualTo(finalStatus == Donation.Status.CLAIMED);
        }
    }

    @Test
    void completeAndTheExpiryReleaseRacingAfterTheDeadlineLeaveOneConsistentResult() throws Exception {
        // a claim whose deadline JUST passed: the recipient presses "picked up" while the job releases it
        for (int round = 0; round < ROUNDS; round++) {
            Donation d = newDonation();
            d.setStatus(Donation.Status.CLAIMED);
            d.setClaimedBy(recipients.get(0));
            d.setClaimedAt(Instant.now().minus(2, ChronoUnit.HOURS));
            d.setClaimDeadline(Instant.now().plusMillis(50));
            donations.save(d);
            Thread.sleep(80);   // the deadline is now in the past, but complete() and the job may still both try

            List<Callable<DonationResponse>> tasks = List.of(
                    () -> service.complete(d.getId(), recipients.get(0).getUsername()),
                    () -> { expiry.releaseAbandonedBatch(Instant.now(), 10); return null; });
            raceAll(tasks);

            Donation after = donations.findById(d.getId()).orElseThrow();
            // complete() refuses after the deadline, so the claim must end as released, with exactly one history row
            assertThat(after.getStatus()).isEqualTo(Donation.Status.ACTIVE);
            assertThat(releases.count()).isEqualTo(round + 1L);
        }
    }
}
