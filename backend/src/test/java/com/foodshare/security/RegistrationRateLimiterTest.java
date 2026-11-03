package com.foodshare.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationRateLimiterTest {

    static class TestClock extends Clock {
        long now = 1_000_000_000L;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
        void advanceSeconds(long s) { now += s * 1000; }
    }

    private TestClock clock;
    private RegistrationRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        limiter = new RegistrationRateLimiter(3, 5, clock);   // 3 attempts per IP per hour, alert at 5 accounts/hour
    }

    @Test
    void fourthAttemptFromTheSameIpInAnHourIsRefused() {
        for (int i = 0; i < 3; i++) assertEquals(0, limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1") > 0);
    }

    @Test
    void otherIpsAreNotAffected() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("6.6.6.6");
        assertTrue(limiter.tryAcquire("6.6.6.6") > 0);
        assertEquals(0, limiter.tryAcquire("2.2.2.2"));
    }

    @Test
    void waitIsTheTimeUntilTheOldestAttemptLeavesTheWindow() {
        limiter.tryAcquire("1.1.1.1");                 // t = 0
        clock.advanceSeconds(600);
        limiter.tryAcquire("1.1.1.1");                 // t = 10 min
        limiter.tryAcquire("1.1.1.1");                 // t = 10 min
        clock.advanceSeconds(600);                     // t = 20 min
        assertEquals(40 * 60, limiter.tryAcquire("1.1.1.1"));   // the first attempt expires at t = 60 min
    }

    @Test
    void windowSlidesSoOldAttemptsStopCounting() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("1.1.1.1");
        assertTrue(limiter.tryAcquire("1.1.1.1") > 0);
        clock.advanceSeconds(3601);
        assertEquals(0, limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void refusedAttemptsDoNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("1.1.1.1");
        clock.advanceSeconds(3000);
        for (int i = 0; i < 50; i++) assertTrue(limiter.tryAcquire("1.1.1.1") > 0);   // hammering while blocked
        clock.advanceSeconds(601);                                                     // 3601 s after the first three
        assertEquals(0, limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void floodOfDifferentIpsStaysWithinTheMemoryBound() {
        for (int i = 0; i < 50_000; i++) limiter.tryAcquire("10." + (i / 65536) + "." + ((i / 256) % 256) + "." + (i % 256));
        // still works, and the newest IP is tracked correctly
        String ip = "200.200.200.200";
        for (int i = 0; i < 3; i++) assertEquals(0, limiter.tryAcquire(ip));
        assertTrue(limiter.tryAcquire(ip) > 0);
    }

    @Test
    void monitoringCountsAccountsCreatedByAllIpsAndNeverBlocks() {
        for (int i = 0; i < 8; i++) {
            assertEquals(0, limiter.tryAcquire("3.3." + i + ".1"));   // 8 different IPs: none is blocked
            limiter.recordCreated();
        }
        assertEquals(8, limiter.createdInLastHour());
        clock.advanceSeconds(3601);
        assertEquals(0, limiter.createdInLastHour());                 // old accounts leave the window
    }
}
