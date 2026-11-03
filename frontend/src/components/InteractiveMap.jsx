import { useEffect, useRef } from "react";
import L from "leaflet";
import { prettify, timeLeft } from "../api.js";

// Emoji pins by category
const CATEGORY_EMOJI = {
  COOKED_MEALS: "🍲",
  BAKERY_ITEMS: "🥖",
  VEGETABLES_FRUITS: "🥦",
  DAIRY_PRODUCTS: "🥛",
  PACKAGED_FOOD: "📦",
};

export default function InteractiveMap({
  mode = "browse", // "browse" | "picker" | "viewSingle"
  items = [],
  singleItem = null,
  center = [22.7196, 75.8577], // Default Indore center
  zoom = 13,
  userLocation = null,
  onLocationSelect = null,
  onClaim = null,
}) {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersLayerRef = useRef(null);
  const pickerMarkerRef = useRef(null);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (!mapInstanceRef.current) {
      const map = L.map(mapContainerRef.current, {
        center: center,
        zoom: zoom,
        zoomControl: true,
      });

      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19,
      }).addTo(map);

      markersLayerRef.current = L.layerGroup().addTo(map);
      mapInstanceRef.current = map;

      // Handle map clicks in picker mode
      if (mode === "picker") {
        map.on("click", (e) => {
          const { lat, lng } = e.latlng;
          updatePickerMarker(lat, lng);
          if (onLocationSelect) {
            onLocationSelect({ lat: Number(lat.toFixed(5)), lng: Number(lng.toFixed(5)) });
          }
        });
      }
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  function updatePickerMarker(lat, lng) {
    if (!mapInstanceRef.current) return;

    const icon = L.divIcon({
      className: "custom-map-marker-pin",
      html: `<div class="map-pin donor-pin">📍<span class="pin-pulse"></span></div>`,
      iconSize: [36, 36],
      iconAnchor: [18, 36],
    });

    if (pickerMarkerRef.current) {
      pickerMarkerRef.current.setLatLng([lat, lng]);
    } else {
      pickerMarkerRef.current = L.marker([lat, lng], { icon, draggable: true })
        .addTo(mapInstanceRef.current)
        .bindPopup("<b>Pickup Location</b><br/>Drag to fine-tune exact spot.")
        .openPopup();

      pickerMarkerRef.current.on("dragend", (e) => {
        const pos = e.target.getLatLng();
        if (onLocationSelect) {
          onLocationSelect({ lat: Number(pos.lat.toFixed(5)), lng: Number(pos.lng.toFixed(5)) });
        }
      });
    }
  }

  // Update center or mode when props change
  useEffect(() => {
    if (!mapInstanceRef.current) return;

    if (mode === "picker" && center && center[0] && center[1]) {
      updatePickerMarker(center[0], center[1]);
      mapInstanceRef.current.setView(center, 14);
    }
  }, [mode, center]);

  // Update markers for Browse Mode or ViewSingle Mode
  useEffect(() => {
    if (!mapInstanceRef.current || !markersLayerRef.current) return;

    markersLayerRef.current.clearLayers();

    // Show user location marker if available
    if (userLocation && userLocation.lat && userLocation.lng) {
      const userIcon = L.divIcon({
        className: "user-location-pin",
        html: `<div class="user-pulse-dot" title="You are here"><div class="dot-core"></div><div class="dot-ring"></div></div>`,
        iconSize: [24, 24],
        iconAnchor: [12, 12],
      });

      L.marker([userLocation.lat, userLocation.lng], { icon: userIcon })
        .addTo(markersLayerRef.current)
        .bindPopup("<b>📍 You are here</b>");
    }

    // View Single Item Mode (e.g. In Claimed Pickup Pass)
    if (mode === "viewSingle" && singleItem && singleItem.latitude && singleItem.longitude) {
      const lat = singleItem.latitude;
      const lng = singleItem.longitude;
      const emoji = CATEGORY_EMOJI[singleItem.category] || "🍲";

      const pinIcon = L.divIcon({
        className: "custom-map-marker-pin",
        html: `<div class="map-pin food-pin">${emoji}</div>`,
        iconSize: [38, 38],
        iconAnchor: [19, 38],
        popupAnchor: [0, -36],
      });

      const popupContent = `
        <div class="map-popup-card">
          <h4>${singleItem.foodName}</h4>
          <p><b>Address:</b> ${singleItem.address || singleItem.area}</p>
          <p><b>Donor:</b> ${singleItem.donorName} (${singleItem.donorOrg || singleItem.donorType || "Donor"})</p>
          ${singleItem.donorPhone ? `<p>📞 <a href="tel:${singleItem.donorPhone}">${singleItem.donorPhone}</a></p>` : ""}
          <a class="map-dir-btn" target="_blank" rel="noopener noreferrer" href="https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}">
            🧭 Open in Google Maps
          </a>
        </div>
      `;

      L.marker([lat, lng], { icon: pinIcon })
        .addTo(markersLayerRef.current)
        .bindPopup(popupContent)
        .openPopup();

      mapInstanceRef.current.setView([lat, lng], 15);
      return;
    }

    // Browse Mode: Plot all available items
    if (mode === "browse" && items.length > 0) {
      const bounds = [];

      items.forEach((item) => {
        if (!item.latitude || !item.longitude) return;

        const lat = item.latitude;
        const lng = item.longitude;
        bounds.push([lat, lng]);

        const emoji = CATEGORY_EMOJI[item.category] || "🍲";
        const urgent = item.minutesLeft < 120;

        const pinIcon = L.divIcon({
          className: "custom-map-marker-pin",
          html: `<div class="map-pin food-pin ${urgent ? "pin-urgent" : ""}">${emoji}</div>`,
          iconSize: [38, 38],
          iconAnchor: [19, 38],
          popupAnchor: [0, -36],
        });

        const popupDiv = document.createElement("div");
        popupDiv.className = "map-popup-card";
        popupDiv.innerHTML = `
          <div class="popup-header">
            <h4>${item.foodName}</h4>
            <span class="badge ${urgent ? "time-urgent" : "time-ok"}">${timeLeft(item.minutesLeft)}</span>
          </div>
          <p class="popup-meta">
            ${item.dietaryType ? `<span class="diet-tag ${item.dietaryType.toLowerCase()}">${item.dietaryType}</span>` : ""}
            <span>${item.quantity} servings</span> · <span>${prettify(item.category)}</span>
          </p>
          <p class="popup-area">📍 ${item.area}</p>
          <div class="popup-donor">
            <strong>👤 ${item.donorName}</strong>
            ${item.donorOrg ? `<span class="donor-org">${item.donorOrg}</span>` : ""}
            <span class="donor-rating">⭐ ${item.donorRating || 4.9}</span>
          </div>
          ${item.allergens ? `<p class="popup-allergens">⚠️ ${item.allergens}</p>` : ""}
        `;

        if (onClaim) {
          const claimBtn = document.createElement("button");
          claimBtn.className = "btn btn-popup-claim";
          claimBtn.textContent = "⚡ Claim This Food";
          claimBtn.onclick = () => onClaim(item.id);
          popupDiv.appendChild(claimBtn);
        }

        L.marker([lat, lng], { icon: pinIcon })
          .addTo(markersLayerRef.current)
          .bindPopup(popupDiv);
      });

      if (bounds.length > 0 && mapInstanceRef.current) {
        if (bounds.length === 1) {
          mapInstanceRef.current.setView(bounds[0], 14);
        } else {
          mapInstanceRef.current.fitBounds(bounds, { padding: [40, 40] });
        }
      }
    }
  }, [items, userLocation, mode, singleItem]);

  return (
    <div className="map-wrapper">
      <div ref={mapContainerRef} className="interactive-leaflet-map" />
    </div>
  );
}
