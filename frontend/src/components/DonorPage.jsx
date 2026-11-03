import { useState, useEffect } from "react";
import { api } from "../api.js";
import { getSession } from "../auth.js";
import DonationCard from "./DonationCard.jsx";
import LoadMore from "./LoadMore.jsx";
import InteractiveMap from "./InteractiveMap.jsx";
import OtpModal from "./OtpModal.jsx";
import ImpactBanner from "./ImpactBanner.jsx";
import usePagedList from "../usePagedList.js";

function toInputValue(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

function makeEmptyForm() {
  const now = new Date();
  return {
    foodName: "",
    category: "COOKED_MEALS",
    dietaryType: "VEG",
    quantity: 10,
    storage: "ROOM_TEMPERATURE",
    area: "",
    address: "",
    latitude: 22.7533,
    longitude: 75.8937,
    allergens: "",
    notes: "",
    preparedAt: toInputValue(now),
    expiresAt: toInputValue(new Date(now.getTime() + 5 * 60 * 60 * 1000)), // 5 hours later
  };
}

export default function DonorPage() {
  const [form, setForm] = useState(makeEmptyForm());
  const mine = usePagedList("/donations/mine");
  const [saving, setSaving] = useState(false);
  const [cancellingId, setCancellingId] = useState(null);
  const [verifyingDonation, setVerifyingDonation] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [gpsLoading, setGpsLoading] = useState(false);
  const session = getSession();

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  function handleLocationSelect({ lat, lng }) {
    setForm((prev) => ({
      ...prev,
      latitude: lat,
      longitude: lng,
      area: prev.area || "Indore Area",
    }));
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      alert("Geolocation is not supported by your browser.");
      return;
    }
    setGpsLoading(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = Number(pos.coords.latitude.toFixed(5));
        const lng = Number(pos.coords.longitude.toFixed(5));
        setForm((prev) => ({
          ...prev,
          latitude: lat,
          longitude: lng,
        }));
        setGpsLoading(false);
      },
      () => {
        alert("Could not fetch your GPS location. Please drop a pin on the map instead.");
        setGpsLoading(false);
      },
      { timeout: 8000 }
    );
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (saving) return;
    setError("");
    setMessage("");
    setSaving(true);
    try {
      await api("/donations", "POST", {
        ...form,
        quantity: Number(form.quantity),
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        preparedAt: new Date(form.preparedAt).toISOString(),
        expiresAt: new Date(form.expiresAt).toISOString(),
      });
      setForm(makeEmptyForm());
      setMessage("🎉 Food donation listed successfully with map location!");
      await mine.reload();
    } catch (err) {
      setError(err.message);
    }
    setSaving(false);
  }

  async function handleCancel(id) {
    if (cancellingId) return;
    if (!window.confirm("Cancel this food listing?")) return;
    setError("");
    setCancellingId(id);
    try {
      await api("/donations/" + id + "/cancel", "POST");
      await mine.reload();
    } catch (err) {
      setError(err.message);
    }
    setCancellingId(null);
  }

  return (
    <div className="page-wrapper">
      <ImpactBanner />

      {/* Donor Karma Profile Header */}
      <div className="donor-dashboard-header card">
        <div className="donor-profile-summary">
          <div className="donor-avatar-large">
            {session ? session.fullName.charAt(0) : "D"}
          </div>
          <div>
            <h3>{session ? session.fullName : "Donor"}</h3>
            <p className="muted">
              {session?.organizationName ? `🏢 ${session.organizationName} · ` : ""}
              <span>{session?.donorType || "Community Donor"}</span>
            </p>
            <div className="donor-badges-row">
              <span className="badge role">{session?.badge || "Food Rescue Hero"}</span>
              <span className="badge-flair">⭐ {session?.rating || 4.9} Rating</span>
              <span className="badge-karma">💚 {session?.karmaPoints || 250} Karma Points</span>
            </div>
          </div>
        </div>
      </div>

      <div className="donor-layout-grid">
        {/* Form Column */}
        <section className="donor-form-section">
          <h2>🥘 List Food Donation</h2>
          <form className="card form form-enhanced" onSubmit={handleSubmit}>
            <label>Food Item Name</label>
            <input
              name="foodName"
              value={form.foodName}
              onChange={handleChange}
              maxLength={100}
              placeholder="e.g. Vegetable Biryani & Raita (20 sets)"
              required
            />

            <div className="row">
              <div>
                <label>Category</label>
                <select name="category" value={form.category} onChange={handleChange}>
                  <option value="COOKED_MEALS">Cooked Meals</option>
                  <option value="BAKERY_ITEMS">Bakery Items</option>
                  <option value="VEGETABLES_FRUITS">Vegetables &amp; Fruits</option>
                  <option value="DAIRY_PRODUCTS">Dairy Products</option>
                  <option value="PACKAGED_FOOD">Packaged Food</option>
                </select>
              </div>

              <div>
                <label>Dietary Type</label>
                <select name="dietaryType" value={form.dietaryType} onChange={handleChange}>
                  <option value="VEG">🌱 Pure Vegetarian</option>
                  <option value="NON_VEG">🥩 Non-Vegetarian</option>
                  <option value="VEGAN">🥑 Vegan</option>
                  <option value="JAIN">✨ Jain</option>
                </select>
              </div>
            </div>

            <div className="row">
              <div>
                <label>Quantity (Servings)</label>
                <input
                  type="number"
                  name="quantity"
                  value={form.quantity}
                  onChange={handleChange}
                  min={1}
                  max={1000}
                  required
                />
              </div>

              <div>
                <label>Storage Requirement</label>
                <select name="storage" value={form.storage} onChange={handleChange}>
                  <option value="ROOM_TEMPERATURE">Room Temperature</option>
                  <option value="REFRIGERATED">Refrigerated (Chilled)</option>
                  <option value="FROZEN">Frozen</option>
                </select>
              </div>
            </div>

            {/* Interactive Map Location Pinning */}
            <div className="map-picker-block">
              <div className="map-picker-header">
                <label>📍 Pin Pickup Location on Map</label>
                <button
                  type="button"
                  className="btn btn-gps"
                  onClick={useCurrentLocation}
                  disabled={gpsLoading}
                >
                  {gpsLoading ? "Detecting..." : "🧭 Use Current GPS"}
                </button>
              </div>
              <p className="muted small">
                Click anywhere on the map or drag the pin to set the exact pickup location.
              </p>
              <InteractiveMap
                mode="picker"
                center={[form.latitude, form.longitude]}
                onLocationSelect={handleLocationSelect}
              />
              <div className="coords-display">
                <span>Selected Coordinates:</span>
                <code>
                  {form.latitude}, {form.longitude}
                </code>
              </div>
            </div>

            <div className="row">
              <div>
                <label>Public Area / Neighborhood</label>
                <input
                  name="area"
                  value={form.area}
                  onChange={handleChange}
                  maxLength={100}
                  placeholder="e.g. Vijay Nagar, Indore"
                  required
                />
              </div>

              <div>
                <label>Exact Pickup Address (Private until claimed)</label>
                <input
                  name="address"
                  value={form.address}
                  onChange={handleChange}
                  maxLength={200}
                  placeholder="e.g. 14 Scheme 54, Behind C21 Mall"
                  required
                />
              </div>
            </div>

            <div className="row">
              <div>
                <label>Preparation Time</label>
                <input
                  type="datetime-local"
                  name="preparedAt"
                  value={form.preparedAt}
                  onChange={handleChange}
                  required
                />
              </div>

              <div>
                <label>Consume-Before Expiry Time</label>
                <input
                  type="datetime-local"
                  name="expiresAt"
                  value={form.expiresAt}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <label>Allergens (optional)</label>
            <input
              name="allergens"
              value={form.allergens}
              onChange={handleChange}
              maxLength={200}
              placeholder="e.g. Contains dairy, peanuts, gluten"
            />

            <label>Pickup Instructions / Notes (optional)</label>
            <input
              name="notes"
              value={form.notes}
              onChange={handleChange}
              maxLength={300}
              placeholder="e.g. Call bell at flat 302, please bring your own containers"
            />

            {error && <p className="error">{error}</p>}
            {message && <p className="success">{message}</p>}

            <button className="btn btn-submit-donation" type="submit" disabled={saving}>
              {saving ? "Publishing Listing..." : "✨ List Food for Donation"}
            </button>
          </form>
        </section>

        {/* My Donations Column */}
        <section className="donor-listings-section">
          <h2>📦 My Food Listings ({mine.items.length}{mine.hasMore ? "+" : ""})</h2>

          {mine.loading && <p className="muted">Loading your listings...</p>}
          {!mine.loading && mine.items.length === 0 && (
            <div className="card empty-state">
              <span className="empty-icon">🍲</span>
              <p>You have not created any food listings yet.</p>
              <p className="muted small">Fill out the form to share surplus food!</p>
            </div>
          )}

          <div className="grid">
            {mine.items.map((d) => (
              <DonationCard
                key={d.id}
                donation={d}
                userRole="DONOR"
                onReload={mine.reload}
              >
                {d.status === "ACTIVE" && (
                  <button
                    className="btn btn-danger"
                    disabled={cancellingId === d.id}
                    onClick={() => handleCancel(d.id)}
                  >
                    {cancellingId === d.id ? "Cancelling..." : "Cancel Listing"}
                  </button>
                )}

                {/* Handover OTP Verification Button for Donor */}
                {d.status === "CLAIMED" && (
                  <button
                    className="btn btn-verify-otp"
                    onClick={() => setVerifyingDonation(d)}
                  >
                    🔑 Verify Handover OTP
                  </button>
                )}
              </DonationCard>
            ))}
          </div>

          <LoadMore hasMore={mine.hasMore} loadingMore={mine.loadingMore} onClick={mine.loadMore} />
        </section>
      </div>

      {/* OTP Verification Modal */}
      {verifyingDonation && (
        <OtpModal
          donation={verifyingDonation}
          onClose={() => setVerifyingDonation(null)}
          onSuccess={() => mine.reload()}
        />
      )}
    </div>
  );
}
