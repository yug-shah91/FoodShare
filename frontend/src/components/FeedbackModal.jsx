import { useState } from "react";
import confetti from "canvas-confetti";
import { api } from "../api.js";

const QUICK_TAGS = [
  "🍲 Freshly Made",
  "😋 Delicious Meal",
  "📦 Clean Packaging",
  "⚡ Super Quick Handover",
  "❤️ Kind & Polite Donor",
  "🌱 High Quality",
];

export default function FeedbackModal({ donation, onClose, onSuccess }) {
  const [rating, setRating] = useState(5);
  const [feedback, setFeedback] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  function toggleTag(tag) {
    if (feedback.includes(tag)) {
      setFeedback(feedback.replace(tag, "").replace(/\s+,/g, "").trim());
    } else {
      setFeedback(feedback ? `${feedback}, ${tag}` : tag);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await api(`/donations/${donation.id}/feedback`, "POST", {
        rating,
        feedback: feedback.trim(),
      });

      confetti({
        particleCount: 80,
        spread: 60,
        origin: { y: 0.7 },
        colors: ["#fbbf24", "#34d399", "#60a5fa"],
      });

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(err.message);
    }
    setSubmitting(false);
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card feedback-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>⭐ Rate &amp; Thank Donor</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        <p className="muted">
          Your feedback helps build trust in the community and celebrates our generous food donors!
        </p>

        <form onSubmit={handleSubmit}>
          {/* Star Rating */}
          <div className="star-rating-selector">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                type="button"
                key={star}
                className={`star-btn ${star <= rating ? "active" : ""}`}
                onClick={() => setRating(star)}
                aria-label={`${star} star`}
              >
                ★
              </button>
            ))}
            <span className="rating-text-label">
              {rating === 5 ? "Outstanding! 🌟" : rating === 4 ? "Very Good! 👍" : rating === 3 ? "Average 👌" : "Needs Improvement"}
            </span>
          </div>

          {/* Quick Tags */}
          <div className="tag-chips-group">
            <label>Quick Gratitude Tags:</label>
            <div className="chips-row">
              {QUICK_TAGS.map((tag) => (
                <button
                  type="button"
                  key={tag}
                  className={`chip-btn ${feedback.includes(tag) ? "selected" : ""}`}
                  onClick={() => toggleTag(tag)}
                >
                  {tag}
                </button>
              ))}
            </div>
          </div>

          <label>Add a personal thank-you note (optional):</label>
          <textarea
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            maxLength={300}
            rows={3}
            placeholder="e.g. Thank you so much for the wonderful meal! It made my evening."
          />

          {error && <p className="error">{error}</p>}

          <div className="modal-actions">
            <button type="button" className="btn btn-light" onClick={onClose}>
              Skip
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Sending..." : "✓ Submit Feedback"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
