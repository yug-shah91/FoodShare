import { prettify, formatDate } from "../api.js";

// One claim that was released because the pickup deadline passed.
// (No address here on purpose: the food may be available to, or claimed by, somebody else again.)
const NOW_TEXT = {
  ACTIVE: "Available to claim again",
  CLAIMED: "Now claimed by someone else",
  COMPLETED: "Picked up by someone else",
  EXPIRED: "This food has expired",
  CANCELLED: "The donor cancelled this donation",
};

export default function ClaimHistoryCard({ entry }) {
  const h = entry;
  return (
    <div className="card donation">
      <div className="donation-top">
        <h3>{h.foodName}</h3>
        <span className="badge status-expired">MISSED</span>
      </div>

      <p className="muted">
        {prettify(h.category)} · {h.quantity} servings · {prettify(h.storage)}
      </p>
      <p>✅ You claimed it: {formatDate(h.claimedAt)}</p>
      <p>⏰ Pickup deadline: <b>{formatDate(h.missedDeadline)}</b></p>
      <p>↩️ Released: {formatDate(h.releasedAt)}</p>
      <p className="muted">{NOW_TEXT[h.donationStatus] || prettify(h.donationStatus)}</p>
      <p className="muted">Donor: {h.donorName}</p>
    </div>
  );
}
