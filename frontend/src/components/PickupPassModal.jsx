import { useState, useEffect } from "react";
import QrCode from "./QrCode.jsx";
import InteractiveMap from "./InteractiveMap.jsx";
import { formatDate } from "../api.js";

export default function PickupPassModal({ donation, onClose }) {
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(Date.now());

  // Ticking timer for real-time countdown to deadline
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const d = donation;
  const deadlineMs = d.claimDeadline ? new Date(d.claimDeadline).getTime() : 0;
  const diffMs = Math.max(0, deadlineMs - now);

  const hours = Math.floor(diffMs / (1000 * 60 * 60));
  const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((diffMs % (1000 * 60)) / 1000);

  const pad = (n) => String(n).padStart(2, "0");
  const timeFormatted = `${hours > 0 ? hours + "h " : ""}${pad(minutes)}m ${pad(seconds)}s`;
  const isUrgent = diffMs < 15 * 60 * 1000; // < 15 mins

  function copyOtp() {
    if (!d.pickupOtp) return;
    navigator.clipboard.writeText(d.pickupOtp);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  }

  const phoneHref = d.donorPhone ? `tel:${d.donorPhone.replace(/[^0-9+]/g, "")}` : null;
  const waHref = d.donorPhone
    ? `https://wa.me/${d.donorPhone.replace(/[^0-9]/g, "")}?text=${encodeURIComponent(
        `Hi ${d.donorName}, I am picking up the food donation: ${d.foodName}`
      )}`
    : null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card pickup-pass-card" onClick={(e) => e.stopPropagation()}>
        <div className="pass-header">
          <div className="pass-title-group">
            <span className="pass-tag">🎫 DIGITAL PICKUP PASS</span>
            <h2>{d.foodName}</h2>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <div className="pass-countdown-bar">
          <span>⏳ Pickup Window:</span>
          <span className={`countdown-timer ${isUrgent ? "urgent-pulse" : ""}`}>
            {diffMs <= 0 ? "⚠️ Window Expired" : `${timeFormatted} remaining`}
          </span>
          <span className="deadline-date">Deadline: {formatDate(d.claimDeadline)}</span>
        </div>

        {/* OTP Highlight Section */}
        <div className="otp-hero-box">
          <p className="otp-hero-label">YOUR PICKUP VERIFICATION CODE</p>
          <div className="otp-digit-display">
            {(d.pickupOtp || "------").split("").map((digit, i) => (
              <span key={i} className="otp-digit">
                {digit}
              </span>
            ))}
          </div>
          <button className="btn btn-copy-otp" onClick={copyOtp}>
            {copied ? "✓ Copied to Clipboard!" : "📋 Copy 6-Digit Code"}
          </button>
          <p className="otp-hero-hint">
            🔒 Show or speak this 6-digit OTP to the donor upon pickup to confirm handover.
          </p>
        </div>

        {/* QR Code and Voucher Meta */}
        <div className="pass-body-grid">
          <div className="pass-qr-side">
            <QrCode value={`FOODSHARE-CLAIM-${d.id}-${d.pickupOtp}`} size={130} />
            <span className="qr-caption">Pass #{d.id}</span>
          </div>

          <div className="pass-donor-side">
            <h4>Donor Information</h4>
            <div className="donor-profile-pill">
              <div className="donor-avatar">{d.donorName.charAt(0)}</div>
              <div>
                <strong>{d.donorName}</strong>
                {d.donorOrg && <div className="donor-org-name">🏢 {d.donorOrg}</div>}
                <div className="donor-tags">
                  <span className="badge role">{d.donorType || "Verified Donor"}</span>
                  <span className="donor-star">⭐ {d.donorRating || 4.9}</span>
                  {d.donorBadge && <span className="badge-flair">🏅 {d.donorBadge}</span>}
                </div>
              </div>
            </div>

            {d.donorPhone ? (
              <div className="pass-contact-actions">
                <a href={phoneHref} className="btn btn-contact btn-call">
                  📞 Call Donor
                </a>
                <a href={waHref} target="_blank" rel="noopener noreferrer" className="btn btn-contact btn-wa">
                  💬 WhatsApp
                </a>
              </div>
            ) : (
              <p className="muted small">Donor contact is available at the pickup address.</p>
            )}
          </div>
        </div>

        {/* Pickup Location & Directions */}
        <div className="pass-location-section">
          <h4>📍 Exact Pickup Address</h4>
          <p className="pass-exact-address">
            <strong>{d.address}</strong> {d.area && `(${d.area})`}
          </p>
          {d.notes && (
            <div className="pass-pickup-notes">
              <strong>📝 Donor's Pickup Instructions:</strong> {d.notes}
            </div>
          )}

          {d.latitude && d.longitude && (
            <div className="pass-map-container">
              <InteractiveMap mode="viewSingle" singleItem={d} zoom={15} />
            </div>
          )}
        </div>

        <div className="pass-actions-footer">
          <button className="btn btn-light" onClick={onClose}>
            Close Pass
          </button>
        </div>
      </div>
    </div>
  );
}
