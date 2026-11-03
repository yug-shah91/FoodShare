import { useState, useEffect } from "react";
import { api } from "../api.js";

export default function ImpactBanner() {
  const [stats, setStats] = useState(null);

  useEffect(() => {
    let ignore = false;
    api("/donations/impact")
      .then((data) => {
        if (!ignore && data) setStats(data);
      })
      .catch(() => {});
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="impact-banner">
      <div className="impact-item">
        <span className="impact-icon">🍲</span>
        <div>
          <div className="impact-number">{stats ? stats.totalMealsRescued : "--"}</div>
          <div className="impact-label">Meals Rescued</div>
        </div>
      </div>

      <div className="impact-divider"></div>

      <div className="impact-item">
        <span className="impact-icon">🌍</span>
        <div>
          <div className="impact-number">{stats ? `${stats.co2SavedKg} kg` : "--"}</div>
          <div className="impact-label">CO₂ Prevented</div>
        </div>
      </div>

      <div className="impact-divider"></div>

      <div className="impact-item">
        <span className="impact-icon">⚡</span>
        <div>
          <div className="impact-number">{stats ? stats.activeListings : "--"}</div>
          <div className="impact-label">Available Now</div>
        </div>
      </div>

      <div className="impact-divider"></div>

      <div className="impact-item">
        <span className="impact-icon">💚</span>
        <div>
          <div className="impact-number">{stats ? stats.totalDonorsCount : "--"}</div>
          <div className="impact-label">Community Donors</div>
        </div>
      </div>
    </div>
  );
}
