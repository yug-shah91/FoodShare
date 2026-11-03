import { useState, useEffect, useRef, useCallback } from "react";
import { api } from "./api.js";

// A small helper ("hook") for any list that the backend sends one page at a time.
//   const { items, hasMore, loading, loadingMore, error, reload, loadMore } = usePagedList("/donations/mine");
//
// - items:     everything loaded so far
// - reload():  start again from the first page (use it after the user changes something)
// - loadMore(): load the next page and add it to the end
//
// Stale requests: reload() and loadMore() can overlap (e.g. the user clicks "Load more", then claims a
// donation, which calls reload()). Without care, the OLD "load more" answer could arrive after the
// refreshed list and be appended to it. To prevent that:
//   - every reload() starts a new "generation" and cancels (AbortController) everything still running
//   - an answer is used only if its generation is still the current one
//   - leaving the page (unmount) or changing `path` also cancels everything
const PAGE_SIZE = 20;

export default function usePagedList(path) {
  const [items, setItems] = useState([]);
  const [hasMore, setHasMore] = useState(false);    // true if there may be another page
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");

  // Refs hold values that must be read "right now" inside async code (state could be out of date there)
  const generation = useRef(0);          // bumped by every reload / unmount
  const controller = useRef(null);       // cancels the requests of the current generation
  const lastPage = useRef(0);            // last page we loaded
  const hasMoreRef = useRef(false);
  const loadingMoreRef = useRef(false);
  const loadingRef = useRef(true);       // true while page 0 is being (re)loaded

  const reload = useCallback(async () => {
    controller.current?.abort();         // cancel the previous reload / loadMore, if still running
    const ctrl = new AbortController();
    controller.current = ctrl;
    const gen = ++generation.current;
    const isCurrent = () => gen === generation.current;

    // Reset EVERYTHING that loadMore() reads, right now, before waiting for the answer.
    // Otherwise a "Load more" click during the reload would still see the old page number / hasMore
    // and ask for page N+1 of a list that is being replaced.
    lastPage.current = 0;
    hasMoreRef.current = false;
    setHasMore(false);
    loadingMoreRef.current = false;
    setLoadingMore(false);
    loadingRef.current = true;
    setLoading(true);

    try {
      // the backend answers { items, page, size, totalElements, totalPages, hasMore }
      const res = await api(path + "?page=0&size=" + PAGE_SIZE, "GET", null, ctrl.signal);
      if (!isCurrent()) return;          // a newer reload started while we waited: drop this answer
      setItems(res.items);
      lastPage.current = 0;
      hasMoreRef.current = res.hasMore;
      setHasMore(res.hasMore);
      setError("");
    } catch (err) {
      if (err.name === "AbortError" || !isCurrent()) return;
      setError(err.message);
    }
    if (isCurrent()) {
      loadingRef.current = false;
      setLoading(false);
    }
  }, [path]);

  const loadMore = useCallback(async () => {
    // ignore the click if: page 0 is still (re)loading, a "load more" is already running, or nothing is left
    if (loadingRef.current || loadingMoreRef.current || !hasMoreRef.current) return;
    loadingMoreRef.current = true;
    setLoadingMore(true);
    setError("");                         // a new try starts: a previous failed "Load more" message is stale now

    const gen = generation.current;      // remember which list we are adding to
    const ctrl = controller.current;
    const isCurrent = () => gen === generation.current;
    const nextPage = lastPage.current + 1;

    try {
      const res = await api(path + "?page=" + nextPage + "&size=" + PAGE_SIZE, "GET", null, ctrl?.signal);
      if (!isCurrent()) return;          // the list was reloaded meanwhile: this page belongs to the old list
      // add the new ones at the end (skip any we already have)
      setItems((old) => [...old, ...res.items.filter((n) => !old.some((o) => o.id === n.id))]);
      lastPage.current = nextPage;
      hasMoreRef.current = res.hasMore;
      setHasMore(res.hasMore);
    } catch (err) {
      if (err.name === "AbortError" || !isCurrent()) return;
      setError(err.message);
    }
    if (isCurrent()) {
      loadingMoreRef.current = false;
      setLoadingMore(false);
    }
  }, [path]);

  useEffect(() => {
    reload();
    return () => {
      generation.current++;              // anything still running is now "old"
      controller.current?.abort();
    };
  }, [reload]);

  return { items, hasMore, loading, loadingMore, error, reload, loadMore };
}
