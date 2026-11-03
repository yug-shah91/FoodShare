import { useState } from "react";
import { api } from "../api.js";

export default function Login({ onLogin, notice }) {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState("DONOR");
  const [phone, setPhone] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [donorType, setDonorType] = useState("Restaurant / Cafe");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      let data;
      if (isRegister) {
        data = await api("/auth/register", "POST", {
          username,
          password,
          fullName,
          role,
          phone,
          organizationName: role === "DONOR" ? organizationName : null,
          donorType: role === "DONOR" ? donorType : null,
        });
      } else {
        data = await api("/auth/login", "POST", { username, password });
      }
      onLogin(data);
    } catch (err) {
      setError(err.message);
    }
    setLoading(false);
  }

  return (
    <div className="login-page">
      <form className="card login-card" onSubmit={handleSubmit}>
        <div className="login-brand-header">
          <span className="brand-logo-icon">🌱</span>
          <h1>FoodShare Connect</h1>
          <p className="muted">Live map food rescue · Verified OTP handovers · Zero waste</p>
        </div>

        <h2 className="login-title">{isRegister ? "Create Your Account" : "Welcome Back"}</h2>

        {notice && <p className="error text-center">{notice}</p>}

        {isRegister && (
          <>
            <label>Full Name</label>
            <input
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              maxLength={60}
              placeholder="e.g. Yash Veer Singh"
              required
            />

            <label>Phone Number (for pickup coordination)</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              maxLength={20}
              placeholder="e.g. +91 98765 43210"
              required
            />
          </>
        )}

        <label>Username</label>
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          maxLength={30}
          pattern={isRegister ? "[A-Za-z0-9_]{3,30}" : undefined}
          title="3 to 30 letters, numbers or underscore"
          placeholder="e.g. yash"
          required
        />

        <label>Password</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={isRegister ? 6 : undefined}
          maxLength={64}
          placeholder="••••••••"
          required
        />

        {isRegister && (
          <>
            <label>I Want To</label>
            <select value={role} onChange={(e) => setRole(e.target.value)}>
              <option value="DONOR">Donate Food (Restaurant, Bakery, Supermarket, Cook)</option>
              <option value="RECIPIENT">Claim &amp; Collect Food (Community Member, NGO)</option>
            </select>

            {role === "DONOR" && (
              <div className="donor-register-fields">
                <label>Donor Category</label>
                <select value={donorType} onChange={(e) => setDonorType(e.target.value)}>
                  <option value="Restaurant / Cafe">Restaurant / Cafe</option>
                  <option value="Bakery">Bakery &amp; Confectionery</option>
                  <option value="Supermarket / Grocery">Supermarket / Grocery</option>
                  <option value="Catering Service">Catering &amp; Events</option>
                  <option value="Community Kitchen">Community Kitchen / Langar</option>
                  <option value="Individual Donor">Individual / Home Cook</option>
                </select>

                <label>Organization / Business Name (optional)</label>
                <input
                  value={organizationName}
                  onChange={(e) => setOrganizationName(e.target.value)}
                  maxLength={100}
                  placeholder="e.g. Annapurna Kitchen &amp; Cafe"
                />
              </div>
            )}
          </>
        )}

        {error && <p className="error text-center">{error}</p>}

        <button className="btn btn-primary btn-submit-auth" type="submit" disabled={loading}>
          {loading ? "Authenticating..." : isRegister ? "Create Account & Get Started" : "Sign In to FoodShare"}
        </button>

        <button
          type="button"
          className="link-btn"
          onClick={() => {
            setIsRegister(!isRegister);
            setError("");
          }}
        >
          {isRegister ? "Already have an account? Sign In" : "New here? Create free account"}
        </button>
      </form>
    </div>
  );
}
