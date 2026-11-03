package com.foodshare.service;

import com.foodshare.entity.ClaimRelease;
import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import com.foodshare.repository.ClaimReleaseRepository;
import com.foodshare.repository.DonationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// The database work of the clean-up. Every public method is ONE short transaction.
//
// IMPORTANT: these methods must be called from ANOTHER bean (ExpiryJob), never from inside this class.
// Spring adds @Transactional through a proxy around the bean; a method that calls another method of its own
// class skips the proxy, so the @Transactional on the called method would silently do nothing.
@Service
public class ExpiryService {

    private final DonationRepository donationRepository;
    private final ClaimReleaseRepository claimReleaseRepository;

    public ExpiryService(DonationRepository donationRepository, ClaimReleaseRepository claimReleaseRepository) {
        this.donationRepository = donationRepository;
        this.claimReleaseRepository = claimReleaseRepository;
    }

    // Food whose "consume before" time passed becomes EXPIRED (ACTIVE or CLAIMED).
    // This must run BEFORE releaseAbandonedBatch: a claim whose deadline and food expiry are the same moment
    // (the deadline is never later than the expiry) must end as EXPIRED, not go back to ACTIVE.
    @Transactional
    public int markExpired(Instant now) {
        return donationRepository.markExpired(Donation.Status.EXPIRED, Donation.Status.ACTIVE,
                Donation.Status.CLAIMED, now);
    }

    // Releases up to "batchSize" claims whose pickup deadline passed, so someone else can claim the food again.
    // Returns how many candidates it looked at (the caller repeats while that equals batchSize).
    //
    // Safe when several servers run this at the same moment:
    //   * The release is an atomic "update ... where id = ? and version = ? and status = CLAIMED". The database
    //     lets exactly ONE server win each claim; the others get 0 rows changed (their UPDATE waits for the
    //     winner's row lock, then no longer matches).
    //   * Only the winner writes the history row. The history row and the release are in the same transaction,
    //     so either both happen or neither does.
    //   * V5__unique_claim_release.sql adds a UNIQUE (donation, claimed_at) key as a last safety net.
    // Memory and lock time are bounded by batchSize.
    @Transactional
    public int releaseAbandonedBatch(Instant now, int batchSize) {
        List<Donation> candidates = donationRepository
                .findByStatusAndClaimDeadlineLessThanEqualOrderByClaimDeadlineAscIdAsc(
                        Donation.Status.CLAIMED, now, PageRequest.of(0, batchSize));

        for (Donation d : candidates) {
            // read everything we need BEFORE the update (the update clears the persistence context)
            User recipient = d.getClaimedBy();
            Instant claimedAt = d.getClaimedAt();
            Instant deadline = d.getClaimDeadline();

            int released = donationRepository.releaseAbandonedClaim(d.getId(), d.getVersion(),
                    Donation.Status.ACTIVE, Donation.Status.CLAIMED, now);
            if (released == 0) continue;   // another server released it, or the recipient just picked it up

            d.setStatus(Donation.Status.ACTIVE);
            d.setClaimedBy(null);
            d.setClaimedAt(null);
            d.setClaimDeadline(null);
            d.setPickupOtp(null);

            ClaimRelease r = new ClaimRelease();
            r.setDonation(d);
            r.setRecipient(recipient);
            r.setClaimedAt(claimedAt);
            r.setClaimDeadline(deadline);
            r.setReleasedAt(now);
            claimReleaseRepository.save(r);
        }
        return candidates.size();
    }
}
