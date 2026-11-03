package com.foodshare.repository;

import com.foodshare.entity.Donation;
import com.foodshare.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    Page<Donation> findByStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(Donation.Status status, Instant now, Pageable page);
    List<Donation> findByStatusAndExpiresAtAfterOrderByCreatedAtDesc(Donation.Status status, Instant now);

    Page<Donation> findByDonorOrderByCreatedAtDescIdDesc(User donor, Pageable page);
    Page<Donation> findByClaimedByOrderByCreatedAtDescIdDesc(User recipient, Pageable page);
    Page<Donation> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    List<Donation> findByStatusAndClaimDeadlineLessThanEqualOrderByClaimDeadlineAscIdAsc(
            Donation.Status status, Instant now, Pageable page);

    long countByStatus(Donation.Status status);
    long countByDonor(User donor);
    long countByClaimedBy(User recipient);

    @Query("select count(distinct d.donor.id) from Donation d")
    long countDistinctDonors();

    @Query("select d.donor.id, count(d) from Donation d where d.donor.id in :ids group by d.donor.id")
    List<Object[]> countDonationsPerDonor(@Param("ids") Collection<Long> ids);

    @Query("select d.claimedBy.id, count(d) from Donation d where d.claimedBy.id in :ids group by d.claimedBy.id")
    List<Object[]> countClaimsPerRecipient(@Param("ids") Collection<Long> ids);

    @Query("select sum(d.quantity) from Donation d where d.status = :status")
    Long sumQuantityByStatus(@Param("status") Donation.Status status);

    // ---------------------------------------------------------------------------------
    // ATOMIC UPDATES
    // ---------------------------------------------------------------------------------

    // Recipient claims: only if still ACTIVE and not expired. Generates and stores 6-digit OTP.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :newStatus, d.claimedBy = :user, d.claimedAt = :now, " +
           "d.claimDeadline = :deadline, d.pickupOtp = :otp where d.id = :id and d.status = :oldStatus and d.expiresAt > :now")
    int claimIfActive(@Param("id") Long id, @Param("user") User user, @Param("now") Instant now,
                      @Param("deadline") Instant deadline, @Param("otp") String otp,
                      @Param("newStatus") Donation.Status newStatus, @Param("oldStatus") Donation.Status oldStatus);

    // Complete with OTP verification (by donor or recipient)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :newStatus, d.completedAt = :now where d.id = :id and d.status = :oldStatus " +
           "and d.pickupOtp = :otp and d.claimDeadline > :now and d.expiresAt > :now")
    int completeWithOtp(@Param("id") Long id, @Param("otp") String otp, @Param("now") Instant now,
                        @Param("newStatus") Donation.Status newStatus, @Param("oldStatus") Donation.Status oldStatus);

    // Recipient completes directly
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :newStatus, d.completedAt = :now where d.id = :id and d.status = :oldStatus " +
           "and d.claimedBy = :user and d.claimDeadline > :now and d.expiresAt > :now")
    int completeIfClaimed(@Param("id") Long id, @Param("user") User user, @Param("now") Instant now,
                          @Param("newStatus") Donation.Status newStatus, @Param("oldStatus") Donation.Status oldStatus);

    // Donor cancels: only my own listing, only if still ACTIVE and not expired
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :newStatus where d.id = :id and d.status = :oldStatus " +
           "and d.donor = :donor and d.expiresAt > :now")
    int cancelIfActive(@Param("id") Long id, @Param("donor") User donor, @Param("now") Instant now,
                       @Param("newStatus") Donation.Status newStatus, @Param("oldStatus") Donation.Status oldStatus);

    // Scheduled clean-up
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :expired where d.status in (:active, :claimed) and d.expiresAt <= :now")
    int markExpired(@Param("expired") Donation.Status expired, @Param("active") Donation.Status active,
                    @Param("claimed") Donation.Status claimed, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Donation d set d.status = :active, d.claimedBy = null, d.claimedAt = null, d.claimDeadline = null, d.pickupOtp = null " +
           "where d.id = :id and d.version = :version and d.status = :claimed and d.claimDeadline <= :now")
    int releaseAbandonedClaim(@Param("id") Long id, @Param("version") Long version,
                              @Param("active") Donation.Status active, @Param("claimed") Donation.Status claimed,
                              @Param("now") Instant now);
}
