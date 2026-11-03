import { useState, useEffect } from "react";
import { getSession, saveSession, clearSession } from "./auth.js";
import Login from "./components/Login.jsx";
import Navbar from "./components/Navbar.jsx";
import DonorPage from "./components/DonorPage.jsx";
import RecipientPage from "./components/RecipientPage.jsx";
import AdminPage from "./components/AdminPage.jsx";

// Which page belongs to which role
const PAGES = {
  DONOR: DonorPage,
  RECIPIENT: RecipientPage,
  ADMIN: AdminPage,
};

export default function App() {
  const [user, setUser] = useState(getSession());   // null if not logged in or token expired
  const [notice, setNotice] = useState("");

  // api.js sends a "session-expired" event when the backend says our login is no longer valid
  useEffect(() => {
    function onExpired() {
      setUser(null);
      setNotice("Your session has expired. Please login again.");
    }
    window.addEventListener("session-expired", onExpired);
    return () => window.removeEventListener("session-expired", onExpired);
  }, []);

  function handleLogin(userData) {
    saveSession(userData);
    setNotice("");
    setUser(userData);
  }

  function handleLogout() {
    clearSession();
    setNotice("");
    setUser(null);
  }

  // Not logged in -> show the login page
  if (!user) {
    return <Login onLogin={handleLogin} notice={notice} />;
  }

  // Logged in -> show the page for this user's role
  const Page = PAGES[user.role];

  return (
    <div>
      <Navbar user={user} onLogout={handleLogout} />
      <main className="container">
        {Page ? <Page /> : <p className="error">Unknown role. Please logout and login again.</p>}
      </main>
    </div>
  );
}
