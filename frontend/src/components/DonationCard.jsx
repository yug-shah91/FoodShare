import { useState } from "react";
import { prettify, formatDate, timeLeft } from "../api.js";
import PickupPassModal from "./PickupPassModal.jsx";
import FeedbackModal from "./FeedbackModal.jsx";

const DIET_ICONS = {
  VEG: "🌱 Pure Veg",
  NON_VEG: "🥩 Non-Veg",
  VEGAN: "🥑 Vegan",
  JAIN: "✨ Jain",
};

export default function DonationCard({
  donation,
  userRole = "RECIPIENT",
  distanceKm = null,
  onReload = null,
  children,
}) {
  const [showPass, setShowPass] = useState(false);
  const [showFeedback, setShowFeedback] = useState(false);

  const d = donation;
  const isOpen = d.status === "ACTIVE" || d.status === "CLAIMED";
  const urgent = isOpen && d.minutesLeft < 120;
  const isClaimedByMe = d.status === "CLAIMED" && d.pickupOtp;

  return (
    <>
      <div className={`card donation-card-v2 ${urgent ? "card-urgent" : ""}`}>
        {/* Top Badges Header */}
        <div className="card-top-header">
          <div className="tags-cluster">
            {d.dietaryType && (
              <span className={`badge-diet ${d.dietaryType.toLowerCase()}`}>
                {DIET_ICONS[d.dietaryType] || d.dietaryType}
              </span>
            )}
            <span className="badge category-badge">{prettify(d.category)}</span>
          </div>

          {isOpen && (
            <span className={`badge-time ${urgent ? "time-urgent urgent-pulse" : "time-ok"}`}>
              ⏳ {timeLeft(d.minutesLeft)}
            </span>
          )}
        </div>

        {/* Title & Servings */}
        <h3 className="card-food-title">{d.foodName}</h3>
        <p className="card-food-specs">
          <strong>{d.quantity} servings</strong> · <span>{prettify(d.storage)}</span>
          {distanceKm !== null && <span className="distance-pill">📍 {distanceKm} km away</span>}
        </p>

        {/* Freshness Bar */}
        {isOpen && (
          <div className="freshness-bar-track">
            <div
              className={`freshness-bar-fill ${urgent ? "fill-urgent" : "fill-ok"}`}
              style={{
                width: `${Math.min(100, Math.max(8, (d.minutesLeft / (24 * 60)) * 100))}%`,
              }}
            />
          </div>
        )}

        {/* Donor Details Card */}
        <div className="card-donor-profile">
          <div className="donor-avatar-mini">{d.donorName ? d.donorName.charAt(0) : "D"}</div>
          <div className="donor-info-col">
            <div className="donor-name-row">
              <span className="donor-name">{d.donorName}</span>
              <span className="donor-star-badge">⭐ {d.donorRating || 4.9}</span>
            </div>
            {d.donorOrg && <span className="donor-org-sub">🏢 {d.donorOrg}</span>}
            <div className="donor-tag-strip">
              <span className="donor-type-tag">{d.donorType || "Food Donor"}</span>
              {d.donorBadge && <span className="donor-honor-tag">🏅 {d.donorBadge}</span>}
            </div>
          </div>
        </div>

        {/* Location Details */}
        <div className="card-location-box">
          {d.address ? (
            <div>
              <p className="location-text">
                <strong>📍 Pickup:</strong> {d.address}
              </p>
              {d.notes && <p className="notes-text">📝 {d.notes}</p>}
            </div>
          ) : (
            <div>
              <p className="location-text">
                <strong>📍 Area:</strong> {d.area}
              </p>
              <p className="muted small">Exact address and donor contact revealed once claimed.</p>
            </div>
          )}
        </div>

        {/* Allergen alert */}
        {d.allergens && (
          <p className="allergens-text">
            <span>⚠️</span> <b>Allergens:</b> {d.allergens}
          </p>
        )}

        {/* Timestamps */}
        <div className="card-times-meta">
          <span>🍳 Cooked: {formatDate(d.preparedAt)}</span>
          <span>⏳ Expiry: {formatDate(d.expiresAt)}</span>
        </div>

        {/* Claimed Status Alert & OTP Pass Shortcut */}
        {d.status === "CLAIMED" && (
          <div className="claimed-status-panel">
            {isClaimedByMe ? (
              <div className="pickup-pass-callout">
                <div className="pass-callout-otp">
                  <span className="pass-callout-label">Your Pickup Code:</span>
                  <span className="pass-callout-code">{d.pickupOtp}</span>
                </div>
                <button
                  type="button"
                  className="btn btn-view-pass"
                  onClick={() => setShowPass(true)}
                >
                  🎫 Open Digital Pickup Pass &amp; Directions
                </button>
              </div>
            ) : (
              <div className="donor-claimed-view">
                <span className="badge status-claimed">RESERVED BY RECIPIENT</span>
                {d.claimedByName && (
                  <p className="small-recipient-note">
                    Claimed by <b>{d.claimedByName}</b>
                    {d.claimedByPhone && ` (${d.claimedByPhone})`}
                  </p>
                )}
                <p className="muted small">Deadline: {formatDate(d.claimDeadline)}</p>
              </div>
            )}
          </div>
        )}

        {/* Completed Feedback Details */}
        {d.status === "COMPLETED" && (
          <div className="completed-panel">
            <span className="badge status-completed">✓ RESCUED &amp; COMPLETED</span>
            {d.rating ? (
              <div className="feedback-received">
                <span>⭐ {d.rating}/5</span> {d.feedback && <i>"{d.feedback}"</i>}
              </div>
            ) : userRole === "RECIPIENT" ? (
              <button
                type="button"
                className="btn btn-feedback-trigger"
                onClick={() => setShowFeedback(true)}
              >
                ⭐ Rate &amp; Thank Donor
              </button>
            ) : null}
          </div>
        )}

        {/* Bottom Action Footer */}
        <div className="donation-bottom-v2">
          <span className={`badge status-${d.status.toLowerCase()}`}>{d.status}</span>
          <div className="card-actions-slot">{children}</div>
        </div>
      </div>

      {/* Modals */}
      {showPass && <PickupPassModal donation={d} onClose={() => setShowPass(false)} />}
      {showFeedback && (
        <FeedbackModal
          donation={d}
          onClose={() => setShowFeedback(false)}
          onSuccess={() => onReload && onReload()}
        />
      )}
    </>
  );
}
