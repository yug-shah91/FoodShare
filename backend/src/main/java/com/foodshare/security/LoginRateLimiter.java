package com.foodshare.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory protection against password guessing.
 *
 * Two HARD limits can refuse a login (HTTP 429):
 *
 *   1. IP address             (threshold 20) - stops one machine from trying many usernames
 *   2. username + IP address  (threshold  5) - stops one machine from guessing ONE account.
 *                                              The IP is part of the key, so a stranger who types wrong passwords
 *                                              for "yash" only slows down THEMSELVES; the real Yash is not affected.
 *
 * One SOFT signal never refuses anybody:
 *
 *   3. username (any IP)      (threshold 30) - "this account is being attacked from many places".
 *                                              If it blocked logins, an attacker could lock the owner out by spreading
 *                                              failures over many IPs. So it only reports (see accountUnderAttack) and
 *                                              the caller logs / alerts. It is a good place to add a CAPTCHA or an
 *                                              e-mail warning to the owner later.
 *
 * After the threshold the wait doubles: 30s, 1m, 2m, 4m, 8m, then 15m (maximum).
 *
 * Old failures fade away with TIME (a leaky bucket): every "decay interval" removes one failure. This is what
 * keeps people on shared IPs (college, office, café) from being punished forever for old typos, and it cannot
 * be gamed, unlike "reset the counter after a successful login" (an attacker could log in to their own account
 * between guesses). So a successful login clears only the username+IP and the (soft) username counters,
 * and never the IP counter.
 *
 * Memory is bounded: every table holds at most MAX_ENTRIES keys. Entries are kept in order of their LAST failure,
 * so "oldest" means "least recently attacked".
 *   - HARD tables (IP, username+IP): when full, expired entries are removed first, then the oldest entry that is NOT
 *     currently blocking anybody. An active block is NEVER evicted: if the table is full of active blocks, a new key
 *     simply is not tracked (the IP limit still applies to whoever is flooding, so one IP cannot create more than a
 *     few keys before it is blocked itself).
 *   - SOFT table (username): it never blocks anybody, so none of its entries is protected. It never sets a block,
 *     and when it is full the least recently attacked entry is simply dropped. (It only produces a log signal, so
 *     losing an old entry costs nothing, while refusing to track a NEW attacked account would hide a real attack.)
 *
 * Limits of this design (fine for a small project, not for production at scale):
 *   - state is per server instance and is lost on restart
 *   - for several servers use a shared limiter (Redis + Bucket4j, an API gateway, a WAF, ...)
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ENTRIES = 10_000;
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;

    private final Clock clock;
    //                                           threshold, first wait, longest wait, one failure fades every
    private final Table ipTable      = new Table(20, 30 * SECOND, 15 * MINUTE, 1 * MINUTE, true);
    private final Table pairTable    = new Table(5,  30 * SECOND, 15 * MINUTE, 3 * MINUTE, true);
    private final Table accountTable = new Table(30, 0, 0,                     1 * MINUTE, false);  // soft: never blocks

    @Autowired
    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    // for tests
    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @return 0 if the login may proceed, otherwise how many seconds the caller must wait. */
    public long secondsUntilAllowed(String username, String ip) {
        long now = clock.millis();
        long wait = Math.max(ipTable.remainingMillis(ip, now),
                             pairTable.remainingMillis(username + "|" + ip, now));
        return wait <= 0 ? 0 : (wait + SECOND - 1) / SECOND;
    }

    public void recordFailure(String username, String ip) {
        long now = clock.millis();
        ipTable.fail(ip, now);
        pairTable.fail(username + "|" + ip, now);
        accountTable.fail(username, now);
    }

    /** Soft signal only: true if this account has had many recent failures (from any IPs). Never blocks a login. */
    public boolean accountUnderAttack(String username) {
        return accountTable.failures(username, clock.millis()) >= accountTable.threshold;
    }

    /**
     * Clears the username+IP counter and the soft username counter (the owner proved who they are).
     * The IP counter is NOT touched: it fades with time only (see the class comment).
     */
    public void recordSuccess(String username, String ip) {
        pairTable.clear(username + "|" + ip);
        accountTable.clear(username);
    }

    // ------------------------------------------------------------------------------------

    private static final class Entry {
        int fails;
        long blockedUntil;
        long lastFailure;
    }

    /** A bounded map of key -> failure counter. All methods are synchronized (tiny critical sections). */
    private static final class Table {
        final int threshold;
        private final long baseDelay;
        private final long maxDelay;
        private final long decayMillis;
        private final boolean blocking;   // false = soft signal: counts failures, never sets blockedUntil
        // insertion order = oldest first
        private final LinkedHashMap<String, Entry> map = new LinkedHashMap<>();
        private long lastPurge = 0;

        Table(int threshold, long baseDelay, long maxDelay, long decayMillis, boolean blocking) {
            this.threshold = threshold;
            this.baseDelay = baseDelay;
            this.maxDelay = maxDelay;
            this.decayMillis = decayMillis;
            this.blocking = blocking;
        }

        // failures still counted at time "now": one fades away every decayMillis
        private int currentFails(Entry e, long now) {
            long faded = Math.max(0, now - e.lastFailure) / decayMillis;
            return (int) Math.max(0, e.fails - faded);
        }

        private boolean isStale(Entry e, long now) {
            return e.blockedUntil <= now && currentFails(e, now) == 0;
        }

        synchronized long remainingMillis(String key, long now) {
            Entry e = map.get(key);
            return e == null ? 0 : Math.max(0, e.blockedUntil - now);
        }

        synchronized int failures(String key, long now) {
            Entry e = map.get(key);
            return e == null ? 0 : currentFails(e, now);
        }

        synchronized void fail(String key, long now) {
            Entry e = map.get(key);
            if (e != null && isStale(e, now)) {
                map.remove(key);
                e = null;
            }
            if (e == null) {
                if (!makeRoom(now)) return;      // full of active blocks: do not track this new key
                e = new Entry();
            } else {
                e.fails = currentFails(e, now);  // apply the fading before adding the new failure
                map.remove(key);                 // re-insert below: keeps the map ordered by LAST failure
            }
            e.fails++;
            e.lastFailure = now;
            map.put(key, e);
            if (blocking && e.fails >= threshold) {
                int exponent = Math.min(e.fails - threshold, 20);   // cap the shift: no overflow
                long delay = Math.min(maxDelay, baseDelay << exponent);
                e.blockedUntil = now + delay;
            }
        }

        synchronized void clear(String key) {
            map.remove(key);
        }

        // Called only when a NEW key must be added. Returns false if there is no room and nothing may be evicted.
        private boolean makeRoom(long now) {
            if (map.size() < MAX_ENTRIES) return true;

            // 1. drop everything that has expired (a full scan, so at most once a minute)
            if (now - lastPurge >= MINUTE) {
                lastPurge = now;
                for (Iterator<Entry> it = map.values().iterator(); it.hasNext(); ) {
                    if (isStale(it.next(), now)) it.remove();
                }
                if (map.size() < MAX_ENTRIES) return true;
            }

            // 2. drop the oldest entry that is not blocking anyone right now.
            //    (A soft table never blocks, so this is simply its oldest entry.)
            for (Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue().blockedUntil <= now) {
                    it.remove();
                    return true;
                }
            }
            // 3. everything is actively blocked: never evict an active block (can only happen in a hard table)
            return false;
        }
    }
}
