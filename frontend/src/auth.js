// Everything about the saved login lives here, so every file reads it the same safe way.

const KEY = "user";
const ROLES = ["DONOR", "RECIPIENT", "ADMIN"];

export function saveSession(user) {
  localStorage.setItem(KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(KEY);
}

// NOTE: this only reads the expiry time to keep the screen tidy. It does NOT check the token's signature.
// It is a convenience, NOT security: the backend verifies every token on every request.
//
// A JWT token has 3 parts separated by dots. The middle part contains the expiry time ("exp").
function tokenIsExpired(token) {
  try {
    const middle = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(middle));
    return payload.exp * 1000 < Date.now();
  } catch (e) {
    return true;   // a token we cannot read is treated as invalid
  }
}

// Returns the logged-in user, or null if nobody is logged in
// (also null if the saved data is broken, has an unknown role, or the token expired).
export function getSession() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const user = JSON.parse(raw);
    if (!user || typeof user.token !== "string" || !ROLES.includes(user.role) || tokenIsExpired(user.token)) {
      clearSession();
      return null;
    }
    return user;
  } catch (e) {
    clearSession();
    return null;
  }
}
