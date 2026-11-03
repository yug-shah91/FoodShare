import { useState, useEffect } from "react";
import { api, prettify } from "../api.js";
import LoadMore from "./LoadMore.jsx";
import usePagedList from "../usePagedList.js";

export default function AdminPage() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState("");
  // both tables are loaded one page at a time (with a "Load more" button), so nothing is cut off
  const users = usePagedList("/admin/users");
  const donations = usePagedList("/admin/donations");

  useEffect(() => {
    async function load() {
      try {
        setStats(await api("/admin/dashboard"));
      } catch (err) {
        setError(err.message);
      }
    }
    load();
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!stats) return <p className="muted">Loading...</p>;

  return (
    <div>
      <h2>Admin Dashboard</h2>

      <div className="stats">
        <div className="card stat"><h3>{stats.mealsSaved}</h3><p>Meals picked up</p></div>
        <div className="card stat"><h3>{stats.co2SavedKg} kg</h3><p>CO₂ saved (estimate)</p></div>
        <div className="card stat"><h3>{stats.totalListings}</h3><p>Total listings</p></div>
        <div className="card stat"><h3>{stats.activeListings}</h3><p>Active</p></div>
        <div className="card stat"><h3>{stats.claimedListings}</h3><p>Claimed</p></div>
        <div className="card stat"><h3>{stats.completedListings}</h3><p>Completed</p></div>
        <div className="card stat"><h3>{stats.expiredListings}</h3><p>Expired</p></div>
      </div>

      <h2>Users</h2>
      {users.error && <p className="error">{users.error}</p>}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Username</th><th>Name</th><th>Role</th><th>Activity</th></tr>
          </thead>
          <tbody>
            {users.items.map((u) => (
              <tr key={u.id}>
                <td>{u.username}</td>
                <td>{u.fullName}</td>
                <td>{u.role}</td>
                <td>{u.role === "ADMIN" ? "-" : u.activityCount + (u.role === "DONOR" ? " donations" : " pickups")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {users.loading && <p className="muted">Loading...</p>}
      <LoadMore hasMore={users.hasMore} loadingMore={users.loadingMore} onClick={users.loadMore} />

      <h2>Donations <span className="muted">(newest first)</span></h2>
      {donations.error && <p className="error">{donations.error}</p>}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Food</th><th>Category</th><th>Servings</th><th>Donor</th><th>Claimed by</th><th>Status</th></tr>
          </thead>
          <tbody>
            {donations.items.map((d) => (
              <tr key={d.id}>
                <td>{d.foodName}</td>
                <td>{prettify(d.category)}</td>
                <td>{d.quantity}</td>
                <td>{d.donorName}</td>
                <td>{d.claimedByName || "-"}</td>
                <td>{d.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {donations.loading && <p className="muted">Loading...</p>}
      <LoadMore hasMore={donations.hasMore} loadingMore={donations.loadingMore} onClick={donations.loadMore} />
    </div>
  );
}
