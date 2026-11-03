import { getSession, clearSession } from "./auth.js";

// The backend address. For deployment, set VITE_API_BASE_URL in a ".env" file (see .env.example).
const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

// All calls to the Spring Boot backend go through this one function.
// "signal" is optional: pass an AbortController's signal to be able to cancel the request.
// A cancelled request throws an error whose name is "AbortError" (callers should ignore it).
export async function api(path, method = "GET", body = null, signal = undefined) {
  const headers = { "Content-Type": "application/json" };

  // If the user is logged in, send the token so the backend knows who we are
  const session = getSession();
  if (session) {
    headers.Authorization = "Bearer " + session.token;
  }

  let res;
  try {
    res = await fetch(BASE_URL + path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : null,
      signal,
    });
  } catch (e) {
    if (e.name === "AbortError") throw e;   // we cancelled it on purpose: not a "server down" problem
    throw new Error("Cannot reach the server. Is the Spring Boot backend running?");
  }

  // 401 on a normal page = the login expired. Log out and tell the app (App.jsx listens for this).
  if (res.status === 401 && !path.startsWith("/auth/")) {
    clearSession();
    window.dispatchEvent(new Event("session-expired"));
    throw new Error("Your session has expired. Please login again.");
  }

  // Read the reply safely: it may be empty, or not JSON (for example an HTML error page)
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (e) {
    throw new Error("Invalid response received from the server (" + res.status + ")");
  }

  if (!res.ok) {
    throw new Error((data && data.message) || "Something went wrong (" + res.status + ")");
  }
  return data;
}

// "COOKED_MEALS" -> "Cooked Meals"
export function prettify(text) {
  return text
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

// "2026-11-03T14:30:00" -> "3 Nov 2026, 2:30 pm"
export function formatDate(text) {
  if (!text) return "-";
  return new Date(text).toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

// 135 -> "2h 15m left"
export function timeLeft(minutes) {
  if (minutes <= 0) return "Expired";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h >= 48) return Math.floor(h / 24) + " days left";
  return (h > 0 ? h + "h " : "") + m + "m left";
}
