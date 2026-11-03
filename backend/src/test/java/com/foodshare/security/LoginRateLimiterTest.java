package com.foodshare.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    // a clock we can move by hand
    static class TestClock extends Clock {
        long now = 1_000_000_000L;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
        void advanceSeconds(long s) { now += s * 1000; }
    }

    private TestClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        limiter = new LoginRateLimiter(clock);
    }

    private void fail(String user, String ip, int times) {
        for (int i = 0; i < times; i++) limiter.recordFailure(user, ip);
    }

    @Test
    void fifthFailureForSameUserAndIpStartsTheWait() {
        fail("victim", "1.1.1.1", 4);
        assertEquals(0, limiter.secondsUntilAllowed("victim", "1.1.1.1"));
        fail("victim", "1.1.1.1", 1);
        assertEquals(30, limiter.secondsUntilAllowed("victim", "1.1.1.1"));
    }

    @Test
    void strangerCannotLockTheOwnerOutFromAnotherIp() {
        fail("victim", "6.6.6.6", 10);
        assertTrue(limiter.secondsUntilAllowed("victim", "6.6.6.6") > 0);
        assertEquals(0, limiter.secondsUntilAllowed("victim", "2.2.2.2"));
    }

    @Test
    void failuresSpreadOverManyIpsNeverBlockTheAccount() {
        // 200 different IPs, 1 failure each: the account counter is far above its threshold of 30
        for (int i = 0; i < 200; i++) limiter.recordFailure("victim", "10.0." + (i / 250) + "." + i);
        assertTrue(limiter.accountUnderAttack("victim"));                       // the soft signal fires...
        assertEquals(0, limiter.secondsUntilAllowed("victim", "192.168.1.50")); // ...but the owner can still log in
    }

    @Test
    void waitDoublesAndIsCappedAtFifteenMinutes() {
        fail("u", "1.1.1.1", 5);
        assertEquals(30, limiter.secondsUntilAllowed("u", "1.1.1.1"));
        clock.advanceSeconds(31);
        fail("u", "1.1.1.1", 1);
        assertEquals(60, limiter.secondsUntilAllowed("u", "1.1.1.1"));
        fail("u", "1.1.1.1", 20);   // blocked callers are normally refused earlier; this just pushes the counter up
        assertEquals(900, limiter.secondsUntilAllowed("u", "1.1.1.1"));
    }

    @Test
    void oneIpTryingManyUsernamesGetsBlocked() {
        for (int i = 0; i < 20; i++) limiter.recordFailure("user" + i, "9.9.9.9");
        assertTrue(limiter.secondsUntilAllowed("brandnew", "9.9.9.9") > 0);
        assertEquals(0, limiter.secondsUntilAllowed("brandnew", "8.8.8.8"));
    }

    @Test
    void oldFailuresFadeWithTime() {
        fail("u", "1.1.1.1", 4);
        clock.advanceSeconds(60 * 60);                       // an hour later everything is forgotten
        fail("u", "1.1.1.1", 1);
        assertEquals(0, limiter.secondsUntilAllowed("u", "1.1.1.1"));   // counts as failure #1, not #5
    }

    @Test
    void successClearsPairAndAccountButNotTheIpCounter() {
        fail("alice", "7.7.7.7", 4);
        limiter.recordSuccess("alice", "7.7.7.7");
        fail("alice", "7.7.7.7", 4);
        assertEquals(0, limiter.secondsUntilAllowed("alice", "7.7.7.7"));   // pair counter restarted: 4 < 5

        // an attacker logging in to their own account must not wipe the IP counter
        for (int i = 0; i < 19; i++) {
            limiter.recordFailure("victim" + i, "5.5.5.5");
            limiter.recordSuccess("attackerAccount", "5.5.5.5");
        }
        limiter.recordFailure("victim99", "5.5.5.5");   // 20th failure from this IP
        assertTrue(limiter.secondsUntilAllowed("other", "5.5.5.5") > 0);
    }

    @Test
    void floodOfDistinctKeysCannotEvictAnActiveBlock() {
        fail("target", "6.6.6.6", 5);
        assertTrue(limiter.secondsUntilAllowed("target", "6.6.6.6") > 0);
        for (int i = 0; i < 50_000; i++) {
            limiter.recordFailure("n" + i, "10." + (i / 250) + "." + (i % 250));
        }
        assertTrue(limiter.secondsUntilAllowed("target", "6.6.6.6") > 0);
    }

    @Test
    void whenEveryKeyIsBlockedNothingIsEvictedAndNewKeysAreSimplyNotTracked() {
        // fill the pair table with blocked entries (5 failures each => blocked)
        for (int i = 0; i < LoginRateLimiter.MAX_ENTRIES; i++) fail("u" + i, "1.1.1." + i, 5);
        assertTrue(limiter.secondsUntilAllowed("u0", "1.1.1.0") > 0);

        fail("late", "2.2.2.2", 5);                                           // no room: not tracked
        assertEquals(0, limiter.secondsUntilAllowed("late", "2.2.2.2"));
        assertTrue(limiter.secondsUntilAllowed("u0", "1.1.1.0") > 0);         // the oldest active block is still there
        assertFalse(limiter.secondsUntilAllowed("u9999", "1.1.1.9999") == 0);
    }

    @Test
    void softAccountTableNeverBlocksSoItNeverFillsUpWithProtectedEntries() {
        // 10,000 different accounts, each attacked from 30 different IPs (= exactly the soft threshold)
        for (int a = 0; a < LoginRateLimiter.MAX_ENTRIES; a++) {
            for (int i = 0; i < 30; i++) limiter.recordFailure("acct" + a, "10." + a + "." + i);
        }
        assertTrue(limiter.accountUnderAttack("acct0"));
        assertTrue(limiter.accountUnderAttack("acct9999"));

        // the table is full, yet a NEW account that is attacked must still be recorded...
        for (int i = 0; i < 30; i++) limiter.recordFailure("newVictim", "99.99.99." + i);
        assertTrue(limiter.accountUnderAttack("newVictim"));
        // ...by dropping the least recently attacked account, not by refusing the new one
        assertFalse(limiter.accountUnderAttack("acct0"));
        assertTrue(limiter.accountUnderAttack("acct9999"));
    }

    @Test
    void aRecentlyAttackedAccountIsNotTheOneEvicted() {
        for (int a = 0; a < LoginRateLimiter.MAX_ENTRIES; a++) limiter.recordFailure("acct" + a, "1.1." + (a / 250) + "." + (a % 250));
        // acct0 is the OLDEST key, but it is attacked again now, so it becomes the newest
        for (int i = 0; i < 30; i++) limiter.recordFailure("acct0", "50.50.50." + i);
        limiter.recordFailure("somebodyNew", "60.60.60.60");   // forces one eviction
        assertTrue(limiter.accountUnderAttack("acct0"));
    }
}
