package com.foodshare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory throttle for POST /api/auth/register, so one script cannot create thousands of accounts.
 *
 * HARD limit (HTTP 429): per IP address, at most {@code maxPerIp} registration ATTEMPTS in any rolling hour.
 * Attempts are counted, not only successes, because every attempt costs a password hash (BCrypt is deliberately
 * slow) and a "username already taken" answer lets a script test which usernames exist.
 * The limit is configurable (app.rate-limit.register-per-ip-per-hour) because people on a shared IP
 * (college, office, one mobile-network address) share it.
 *
 * MONITORING (never blocks anybody): the number of accounts created by ALL IPs together during the last hour.
 * A botnet using thousands of IPs stays under every per-IP limit, but the total gives it away. When it reaches
 * app.rate-limit.register-alert-per-hour, a WARN line is logged (at most one per 10 minutes). It deliberately
 * does not refuse anyone: a global hard limit would let an attacker stop real people from signing up.
 * Add a CAPTCHA or e-mail verification when this alert fires. Blocks of a single IP are logged once per block.
 *
 * Memory is bounded: at most MAX_ENTRIES IPs are tracked and each holds at most {@code maxPerIp} timestamps.
 * When the table is full, IPs with no attempt in the last hour go first, then the least recently active IP.
 * (Evicting an active IP only lets that IP start a fresh window, which is far better than refusing real
 * visitors or growing without limit.)
 *
 * Limits of this design: state is per server instance and is lost on restart. For several servers use a shared
 * limiter (Redis + Bucket4j, an API gateway, a WAF, ...).
 */
@Component
public class RegistrationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegistrationRateLimiter.class);

    static final int MAX_ENTRIES = 10_000;
    static final long WINDOW = 60L * 60 * 1000;           // one hour
    private static final long MINUTE = 60_000L;
    private static final long ALERT_EVERY = 10 * MINUTE;

    private final Clock clock;
    private final int maxPerIp;
    private final int alertPerHour;

    // ip -> timestamps of the attempts inside the window (oldest first). Map order = least recently active first.
    private final LinkedHashMap<String, ArrayDeque<Long>> attempts = new LinkedHashMap<>();
    private long lastPurge = 0;

    // accounts created per minute, for the last 60 minutes (ring buffer)
    private final long[] createdPerMinute = new long[60];
    private final long[] createdMinuteId = new long[60];
    private long lastAlert = 0;

    @Autowired
    public RegistrationRateLimiter(@Value("${app.rate-limit.register-per-ip-per-hour:10}") int maxPerIp,
                                   @Value("${app.rate-limit.register-alert-per-hour:100}") int alertPerHour) {
        this(maxPerIp, alertPerHour, Clock.systemUTC());
    }

    // for tests
    RegistrationRateLimiter(int maxPerIp, int alertPerHour, Clock clock) {
        this.maxPerIp = Math.max(1, maxPerIp);
        this.alertPerHour = alertPerHour;
        this.clock = clock;
    }

    /**
     * Call once at the start of every registration. Counts the attempt unless it is refused.
     * @return 0 if the registration may proceed, otherwise how many seconds the caller must wait.
     */
    public synchronized long tryAcquire(String ip) {
        long now = clock.millis();
        ArrayDeque<Long> q = attempts.remove(ip);   // removed and re-inserted below: keeps "least recently active first"

        if (q != null) {
            while (!q.isEmpty() && q.peekFirst() <= now - WINDOW) q.pollFirst();
        } else {
            makeRoom(now);
            q = new ArrayDeque<>();
        }

        if (q.size() >= maxPerIp) {
            attempts.put(ip, q);                    // refused attempts are not recorded: memory stays bounded
            long wait = (q.peekFirst() + WINDOW - now + 999) / 1000;
            logBlockOnce(ip, now);
            return Math.max(1, wait);
        }
        q.addLast(now);
        attempts.put(ip, q);
        return 0;
    }

    /** Call after an account was really created. Only feeds the monitoring; it never blocks anybody. */
    public synchronized void recordCreated() {
        long now = clock.millis();
        long minute = now / MINUTE;
        int slot = (int) (minute % 60);
        if (createdMinuteId[slot] != minute) {
            createdMinuteId[slot] = minute;
            createdPerMinute[slot] = 0;
        }
        createdPerMinute[slot]++;

        long total = createdInLastHour(minute);
        if (alertPerHour > 0 && total >= alertPerHour && now - lastAlert >= ALERT_EVERY) {
            lastAlert = now;
            log.warn("Registration spike: {} accounts were created in the last hour (alert threshold {}). "
                    + "Possible automated sign-ups from many IPs; consider CAPTCHA or e-mail verification.",
                    total, alertPerHour);
        }
    }

    /** Accounts created (by anybody) during the last hour. */
    public synchronized long createdInLastHour() {
        return createdInLastHour(clock.millis() / MINUTE);
    }

    private long createdInLastHour(long minuteNow) {
        long sum = 0;
        for (int i = 0; i < 60; i++) {
            if (minuteNow - createdMinuteId[i] < 60) sum += createdPerMinute[i];
        }
        return sum;
    }

    // ---- block logging: one line per IP per window, not one per refused request ----
    private final Map<String, Long> blockLogged = new LinkedHashMap<>();

    private void logBlockOnce(String ip, long now) {
        Long last = blockLogged.get(ip);
        if (last != null && now - last < WINDOW) return;
        if (blockLogged.size() >= MAX_ENTRIES) {
            for (Iterator<Long> it = blockLogged.values().iterator(); it.hasNext(); ) {
                if (now - it.next() >= WINDOW) it.remove();
            }
            if (blockLogged.size() >= MAX_ENTRIES) return;   // flood: stay silent rather than grow
        }
        blockLogged.put(ip, now);
        log.warn("Registration blocked for IP {}: more than {} attempts in one hour", ip, maxPerIp);
    }

    // Called only when a NEW ip must be added. Always makes room (see the class comment).
    private void makeRoom(long now) {
        if (attempts.size() < MAX_ENTRIES) return;

        if (now - lastPurge >= MINUTE) {          // a full scan, so at most once a minute
            lastPurge = now;
            for (Iterator<ArrayDeque<Long>> it = attempts.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Long> d = it.next();
                if (d.isEmpty() || d.peekLast() <= now - WINDOW) it.remove();
            }
            if (attempts.size() < MAX_ENTRIES) return;
        }
        Iterator<String> oldest = attempts.keySet().iterator();   // least recently active IP
        oldest.next();
        oldest.remove();
    }
}
