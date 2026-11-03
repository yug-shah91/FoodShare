package com.foodshare.repository;

import com.foodshare.entity.ClaimRelease;
import com.foodshare.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimReleaseRepository extends JpaRepository<ClaimRelease, Long> {
    long countByRecipientId(Long recipientId);

    // One recipient's missed pickups, newest release first (id = unique tie-breaker, so pages never overlap).
    // The entity graph loads the donation and its donor in the same query (no extra query per row).
    @EntityGraph(attributePaths = {"donation", "donation.donor"})
    Page<ClaimRelease> findByRecipientOrderByReleasedAtDescIdDesc(User recipient, Pageable page);
}
