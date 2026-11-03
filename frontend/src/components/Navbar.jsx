export default function Navbar({ user, onLogout }) {
  return (
    <header className="navbar">
      <div className="navbar-left">
        <span className="navbar-logo">🌱</span>
        <div>
          <h1 className="navbar-brand">FoodShare Connect</h1>
          <span className="navbar-tagline">Surplus Food Rescue &amp; Handover</span>
        </div>
      </div>

      <div className="navbar-right">
        <div className="navbar-user-chip">
          <div className="user-initial-bubble">{user.fullName.charAt(0)}</div>
          <div className="user-details-mini">
            <span className="user-name-label">{user.fullName}</span>
            <div className="user-badges-mini">
              <span className={`badge role role-${user.role.toLowerCase()}`}>{user.role}</span>
              {user.karmaPoints && (
                <span className="badge-karma-nav">💚 {user.karmaPoints} pts</span>
              )}
            </div>
          </div>
        </div>

        <button className="btn btn-logout" onClick={onLogout}>
          Sign Out
        </button>
      </div>
    </header>
  );
}
