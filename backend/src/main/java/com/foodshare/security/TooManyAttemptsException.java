package com.foodshare.security;

// Thrown when a request is refused by a rate limiter (too many failed logins, too many registrations).
// Becomes HTTP 429 + Retry-After.
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        this(retryAfterSeconds, "Too many failed attempts.");
    }

    // "reason" is shown to the user, followed by "Please try again in ..."
    public TooManyAttemptsException(long retryAfterSeconds, String reason) {
        super(reason);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
