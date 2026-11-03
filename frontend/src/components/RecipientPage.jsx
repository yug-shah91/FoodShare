import { useState, useEffect, useMemo } from "react";
import confetti from "canvas-confetti";
import { api } from "../api.js";
import DonationCard from "./DonationCard.jsx";
import ClaimHistoryCard from "./ClaimHistoryCard.jsx";
import InteractiveMap from "./InteractiveMap.jsx";
import ImpactBanner from "./ImpactBanner.jsx";
import LoadMore from "./LoadMore.jsx";
import usePagedList from "../usePagedList.js";

// Haversine formula to calculate km distance between two GPS coordinates
function calcDistanceKm(lat1, lon1, lat2, lon2) {
  if (!lat1 || !lon1 || !lat2 || !lon2) return null;
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Number((R * c).toFixed(1));
}

const CATEGORIES = [
  { id: "ALL", label: "All Categories", emoji: "🍽️" },
  { id: "COOKED_MEALS", label: "Cooked Meals", emoji: "🍲" },
  { id: "BAKERY_ITEMS", label: "Bakery", emoji: "🥖" },
  { id: "VEGETABLES_FRUITS", label: "Produce", emoji: "🥦" },
  { id: "DAIRY_PRODUCTS", label: "Dairy", emoji: "🥛" },
  { id: "PACKAGED_FOOD", label: "Packaged", emoji: "📦" },
];

const DIETARY_FILTERS = [
  { id: "ALL", label: "All Diets" },
  { id: "VEG", label: "🌱 Pure Veg" },
  { id: "NON_VEG", label: "🥩 Non-Veg" },
  { id: "VEGAN", label: "🥑 Vegan" },
  { id: "JAIN", label: "✨ Jain" },
];

export default function RecipientPage() {
  const [tab, setTab] = useState("available"); // "available", "claimed", "missed"
  const [displayMode, setDisplayMode] = useState("cards"); // "cards" | "map"
  const [selectedCategory, setSelectedCategory] = useState("ALL");
  const [selectedDiet, setSelectedDiet] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [userLocation, setUserLocation] = useState(null);
  const [locating, setLocating] = useState(false);

  const available = usePagedList("/donations");
  const claimed = usePagedList("/donations/claimed");
  const missed = usePagedList("/donations/claim-history");
  const [allMapItems, setAllMapItems] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const [actionError, setActionError] = useState("");

  // Load all active items for the full Map View
  useEffect(() => {
    if (tab === "available" && displayMode === "map") {
      api("/donations/map")
        .then((items) => {
          if (Array.isArray(items)) setAllMapItems(items);
        })
        .catch(() => {});
    }
  }, [tab, displayMode]);

  function locateUser() {
    if (!navigator.geolocation) {
      alert("Geolocation is not supported by your browser.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setUserLocation({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        });
        setLocating(false);
      },
      () => {
        alert("Could not detect your location. Default city center will be used.");
        setLocating(false);
      },
      { timeout: 8000 }
    );
  }

  async function handleClaim(id) {
    if (busyId) return;
    setActionError("");
    setBusyId(id);
    try {
      await api("/donations/" + id + "/claim", "POST");

      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 },
        colors: ["#1a7f3c", "#34d399", "#fbbf24"],
      });

      // Switch to claimed tab to reveal Pickup Pass & OTP immediately
      await Promise.all([available.reload(), claimed.reload()]);
      setTab("claimed");
    } catch (err) {
      setActionError(err.message);
    }
    setBusyId(null);
  }

  async function handleCompleteDirect(id) {
    if (busyId) return;
    setActionError("");
    setBusyId(id);
    try {
      await api("/donations/" + id + "/complete", "POST");
      await Promise.all([available.reload(), claimed.reload()]);
    } catch (err) {
      setActionError(err.message);
    }
    setBusyId(null);
  }

  // Filtered available items
  const filteredAvailable = useMemo(() => {
    return available.items.filter((item) => {
      if (selectedCategory !== "ALL" && item.category !== selectedCategory) return false;
      if (selectedDiet !== "ALL" && item.dietaryType !== selectedDiet) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesName = item.foodName && item.foodName.toLowerCase().includes(q);
        const matchesArea = item.area && item.area.toLowerCase().includes(q);
        const matchesDonor = item.donorName && item.donorName.toLowerCase().includes(q);
        if (!matchesName && !matchesArea && !matchesDonor) return false;
      }
      return true;
    });
  }, [available.items, selectedCategory, selectedDiet, searchQuery]);

  function openTab(name) {
    setTab(name);
    if (name === "missed") missed.reload();
    if (name === "claimed") claimed.reload();
  }

  const busy = busyId !== null;
  const error = actionError || available.error || claimed.error || missed.error;

  return (
    <div className="page-wrapper">
      <ImpactBanner />

      {/* Main Recipient Navigation Tabs */}
      <div className="tabs tabs-recipient">
        <button
          className={tab === "available" ? "tab active" : "tab"}
          onClick={() => openTab("available")}
        >
          🍲 Available Food ({available.items.length}
          {available.hasMore ? "+" : ""})
        </button>
        <button
          className={tab === "claimed" ? "tab active" : "tab"}
          onClick={() => openTab("claimed")}
        >
          🎫 My Pickups &amp; Passes ({claimed.items.length}
          {claimed.hasMore ? "+" : ""})
        </button>
        <button
          className={tab === "missed" ? "tab active" : "tab"}
          onClick={() => openTab("missed")}
        >
          Missed Pickups ({missed.items.length}
          {missed.hasMore ? "+" : ""})
        </button>
      </div>

      {error && <p className="error error-banner">{error}</p>}

      {/* Tab: Available Food */}
      {tab === "available" && (
        <div className="available-section">
          {/* Controls Bar: Filters & Map Toggle */}
          <div className="discovery-controls card">
            <div className="discovery-row-top">
              {/* Search Bar */}
              <div className="search-bar-wrap">
                <span className="search-icon">🔍</span>
                <input
                  type="text"
                  placeholder="Search dishes, areas (e.g. Vijay Nagar), or donors..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="search-input"
                />
                {searchQuery && (
                  <button className="search-clear-btn" onClick={() => setSearchQuery("")}>
                    ✕
                  </button>
                )}
              </div>

              {/* View Switcher & GPS Button */}
              <div className="view-switch-group">
                <button
                  type="button"
                  className={`btn-view-toggle ${displayMode === "cards" ? "active" : ""}`}
                  onClick={() => setDisplayMode("cards")}
                >
                  📋 Cards
                </button>
                <button
                  type="button"
                  className={`btn-view-toggle ${displayMode === "map" ? "active" : ""}`}
                  onClick={() => setDisplayMode("map")}
                >
                  🗺️ Live Map
                </button>
                <button
                  type="button"
                  className="btn btn-locate-me"
                  onClick={locateUser}
                  disabled={locating}
                >
                  {locating ? "Locating..." : userLocation ? "📍 Located" : "📍 Near Me"}
                </button>
              </div>
            </div>

            {/* Category Pills */}
            <div className="filter-chips-cluster">
              <div className="filter-row categories-row">
                {CATEGORIES.map((cat) => (
                  <button
                    key={cat.id}
                    className={`filter-chip ${selectedCategory === cat.id ? "active" : ""}`}
                    onClick={() => setSelectedCategory(cat.id)}
                  >
                    <span>{cat.emoji}</span> {cat.label}
                  </button>
                ))}
              </div>

              {/* Dietary Pills */}
              <div className="filter-row diet-row">
                {DIETARY_FILTERS.map((diet) => (
                  <button
                    key={diet.id}
                    className={`diet-filter-pill ${selectedDiet === diet.id ? "active" : ""}`}
                    onClick={() => setSelectedDiet(diet.id)}
                  >
                    {diet.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Interactive Map View */}
          {displayMode === "map" && (
            <div className="map-view-container card">
              <div className="map-view-header">
                <h3>🗺️ Interactive Food Rescue Map</h3>
                <span className="muted small">
                  Showing available surplus food pins. Click any pin to inspect &amp; claim!
                </span>
              </div>
              <InteractiveMap
                mode="browse"
                items={allMapItems.length > 0 ? allMapItems : available.items}
                userLocation={userLocation}
                onClaim={handleClaim}
              />
            </div>
          )}

          {/* Cards Grid View */}
          {displayMode === "cards" && (
            <div>
              {available.loading && <p className="muted">Loading available food donations...</p>}
              {!available.loading && filteredAvailable.length === 0 && (
                <div className="card empty-state">
                  <span className="empty-icon">🥦</span>
                  <p>No food donations match your current filters right now.</p>
                  <p className="muted small">Try selecting different categories or check back soon!</p>
                </div>
              )}

              <div className="grid">
                {filteredAvailable.map((d) => {
                  const dist = userLocation
                    ? calcDistanceKm(userLocation.lat, userLocation.lng, d.latitude, d.longitude)
                    : null;
                  return (
                    <DonationCard
                      key={d.id}
                      donation={d}
                      userRole="RECIPIENT"
                      distanceKm={dist}
                      onReload={available.reload}
                    >
                      <button
                        className="btn btn-claim-action"
                        disabled={busy}
                        onClick={() => handleClaim(d.id)}
                      >
                        {busyId === d.id ? "Reserving..." : "⚡ Claim Food"}
                      </button>
                    </DonationCard>
                  );
                })}
              </div>

              <LoadMore
                hasMore={available.hasMore}
                loadingMore={available.loadingMore}
                onClick={available.loadMore}
              />
            </div>
          )}
        </div>
      )}

      {/* Tab: My Pickups & Passes */}
      {tab === "claimed" && (
        <div>
          {claimed.loading && <p className="muted">Loading your claimed pickups...</p>}
          {!claimed.loading && claimed.items.length === 0 && (
            <div className="card empty-state">
              <span className="empty-icon">🎫</span>
              <p>You have not claimed any food donations yet.</p>
              <p className="muted small">Browse the available food listings to claim your first meal!</p>
            </div>
          )}

          <div className="grid">
            {claimed.items.map((d) => {
              const dist = userLocation
                ? calcDistanceKm(userLocation.lat, userLocation.lng, d.latitude, d.longitude)
                : null;
              return (
                <DonationCard
                  key={d.id}
                  donation={d}
                  userRole="RECIPIENT"
                  distanceKm={dist}
                  onReload={claimed.reload}
                >
                  {d.status === "CLAIMED" && (
                    <button
                      className="btn btn-secondary-complete"
                      disabled={busy}
                      onClick={() => handleCompleteDirect(d.id)}
                    >
                      {busyId === d.id ? "Saving..." : "✓ Mark Picked Up"}
                    </button>
                  )}
                </DonationCard>
              );
            })}
          </div>

          <LoadMore
            hasMore={claimed.hasMore}
            loadingMore={claimed.loadingMore}
            onClick={claimed.loadMore}
          />
        </div>
      )}

      {/* Tab: Missed Pickups History */}
      {tab === "missed" && (
        <div>
          <div className="history-info-banner card">
            <span className="info-icon">ℹ️</span>
            <div>
              <strong>Missed Pickup Records</strong>
              <p className="muted small">
                Food claims are held for 2 hours. If not collected before the deadline, they are
                automatically released so other community members can rescue the food.
              </p>
            </div>
          </div>

          {missed.loading && <p className="muted">Loading missed claims history...</p>}
          {!missed.loading && missed.items.length === 0 && (
            <div className="card empty-state">
              <span className="empty-icon">🌟</span>
              <p>Great job! You have never missed any food pickups.</p>
            </div>
          )}

          <div className="grid">
            {missed.items.map((h) => (
              <ClaimHistoryCard key={h.id} history={h} />
            ))}
          </div>

          <LoadMore
            hasMore={missed.hasMore}
            loadingMore={missed.loadingMore}
            onClick={missed.loadMore}
          />
        </div>
      )}
    </div>
  );
}
