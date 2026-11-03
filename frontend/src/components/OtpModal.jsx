import { useState, useRef, useEffect } from "react";
import confetti from "canvas-confetti";
import { api } from "../api.js";

export default function OtpModal({ donation, onClose, onSuccess }) {
  const [digits, setDigits] = useState(["", "", "", "", "", ""]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const inputRefs = useRef([]);

  useEffect(() => {
    // Focus first input automatically
    if (inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, []);

  function handleDigitChange(index, value) {
    if (!/^[0-9]?$/.test(value)) return;

    const newDigits = [...digits];
    newDigits[index] = value;
    setDigits(newDigits);
    setError("");

    // Auto-advance to next input
    if (value && index < 5 && inputRefs.current[index + 1]) {
      inputRefs.current[index + 1].focus();
    }
  }

  function handleKeyDown(index, e) {
    if (e.key === "Backspace" && !digits[index] && index > 0 && inputRefs.current[index - 1]) {
      inputRefs.current[index - 1].focus();
    }
  }

  function handlePaste(e) {
    e.preventDefault();
    const paste = e.clipboardData.getData("text").trim();
    if (/^[0-9]{6}$/.test(paste)) {
      setDigits(paste.split(""));
      if (inputRefs.current[5]) inputRefs.current[5].focus();
    }
  }

  async function handleVerify(e) {
    e.preventDefault();
    const otp = digits.join("");
    if (otp.length !== 6) {
      setError("Please enter all 6 digits of the OTP.");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      await api(`/donations/${donation.id}/verify-pickup-otp`, "POST", { otp });

      // Celebratory Confetti!
      confetti({
        particleCount: 120,
        spread: 70,
        origin: { y: 0.6 },
        colors: ["#1a7f3c", "#34d399", "#fbbf24", "#60a5fa"],
      });

      if (onSuccess) {
        onSuccess();
      }
      onClose();
    } catch (err) {
      setError(err.message);
    }
    setSubmitting(false);
  }

  const d = donation;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card otp-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>🔑 Verify Recipient Handover</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <p className="muted">
          Ask the recipient for their 6-digit Pickup OTP code before handing over the food.
        </p>

        <div className="otp-claimant-info">
          <div>
            <strong>Recipient:</strong> {d.claimedByName || "Reserved Claim"}
          </div>
          {d.claimedByPhone && (
            <div>
              <strong>Phone:</strong> {d.claimedByPhone}
            </div>
          )}
          <div>
            <strong>Food:</strong> {d.foodName} ({d.quantity} servings)
          </div>
        </div>

        <form onSubmit={handleVerify}>
          <div className="otp-inputs-row" onPaste={handlePaste}>
            {digits.map((digit, idx) => (
              <input
                key={idx}
                ref={(el) => (inputRefs.current[idx] = el)}
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                onChange={(e) => handleDigitChange(idx, e.target.value)}
                onKeyDown={(e) => handleKeyDown(idx, e)}
                className={`otp-single-box ${digit ? "filled" : ""}`}
                autoComplete="off"
              />
            ))}
          </div>

          {error && <p className="error text-center">{error}</p>}

          <div className="modal-actions">
            <button type="button" className="btn btn-light" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting || digits.join("").length !== 6}>
              {submitting ? "Verifying..." : "✓ Confirm Handover"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
