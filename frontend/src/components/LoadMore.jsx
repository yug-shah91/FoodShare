// The "Load more" button shown under a list that has more pages
export default function LoadMore({ hasMore, loadingMore, onClick }) {
  if (!hasMore) return null;
  return (
    <p style={{ textAlign: "center", marginTop: 16 }}>
      <button className="btn btn-light" disabled={loadingMore} onClick={onClick}>
        {loadingMore ? "Loading..." : "Load more"}
      </button>
    </p>
  );
}
