package com.foodshare.entity;

import jakarta.persistence.*;
import java.time.Instant;

// One row = "this recipient claimed this donation but did not pick it up in time, so the claim was released".
@Entity
@Table(name = "claim_releases")
public class ClaimRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Donation donation;

    @ManyToOne(optional = false)
    private User recipient;

    @Column(nullable = false)
    private Instant claimedAt;

    @Column(nullable = false)
    private Instant claimDeadline;   // the deadline that was missed

    @Column(nullable = false)
    private Instant releasedAt;

    public ClaimRelease() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Donation getDonation() { return donation; }
    public void setDonation(Donation donation) { this.donation = donation; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public Instant getClaimDeadline() { return claimDeadline; }
    public void setClaimDeadline(Instant claimDeadline) { this.claimDeadline = claimDeadline; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
