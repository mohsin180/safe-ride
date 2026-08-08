package com.saferide.monolith.user.security;

import com.saferide.monolith.user.exceptions.TooManyAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-key attempt budget for the unauthenticated auth endpoints.
 *
 * <p>Login previously ran BCrypt on every request with no counter, so an
 * address could be brute-forced as fast as the CPU allowed; the mail-sending
 * endpoints could likewise be looped to flood an inbox (and burn the app's
 * SMTP quota). Keyed by email so one attacker can't lock out unrelated users
 * by hammering a shared IP.
 *
 * <p>In-memory on purpose: this is one process, and a limiter that forgets
 * everything on restart is still worth far more than none. It would need to
 * move to shared storage before running more than one instance.
 */
@Component
public class AttemptLimiter {

    /** Attempts allowed inside {@link #window} before the key is refused. */
    private final int maxAttempts;
    private final Duration window;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AttemptLimiter(
            @Value("${auth.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${auth.rate-limit.window-minutes:15}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * Counts one attempt against {@code key}, refusing once the budget for the
     * current window is spent.
     *
     * @param action verb used in the error the caller sees ("sign in")
     * @throws TooManyAttemptsException when the budget is exhausted
     */
    public void check(String action, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalised = key.trim().toLowerCase();
        Instant now = Instant.now();

        Window w = windows.compute(normalised, (k, existing) ->
                (existing == null || existing.startedBefore(now.minus(window)))
                        ? new Window(now)
                        : existing);

        if (w.count.incrementAndGet() > maxAttempts) {
            throw new TooManyAttemptsException(
                    "Too many attempts to " + action + ". Try again in "
                            + window.toMinutes() + " minutes.");
        }
        // Opportunistic cleanup so an unbounded key space (arbitrary emails)
        // can't grow the map forever.
        if (windows.size() > 10_000) {
            windows.values().removeIf(entry -> entry.startedBefore(now.minus(window)));
        }
    }

    /** Clears the budget after a genuine success, so real users aren't punished. */
    public void reset(String key) {
        if (key != null && !key.isBlank()) {
            windows.remove(key.trim().toLowerCase());
        }
    }

    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private boolean startedBefore(Instant cutoff) {
            return startedAt.isBefore(cutoff);
        }
    }
}
