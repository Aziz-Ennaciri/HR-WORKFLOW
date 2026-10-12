package ma.rh.ai.hr_workflow.user.service.Impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ma.rh.ai.hr_workflow.exceptions.TooManyRequestsException;

/**
 * Slows down password guessing: after {@code maxFailures} failed logins for an email within the
 * window, further attempts are refused until the window passes. In memory, so it is per backend
 * instance and resets on restart.
 */
@Component
public class LoginAttemptService {

    private record Failures(int count, Instant windowStart) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginAttemptService(@Value("${security.login.max-failures:5}") int maxFailures,
                               @Value("${security.login.lock-duration:PT15M}") Duration window,
                               Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** @throws TooManyRequestsException if the email is currently locked */
    public void checkAllowed(String email) {
        Failures current = failures.get(key(email));
        if (current != null && current.count() >= maxFailures) {
            Duration remaining = Duration.between(clock.instant(), current.windowStart().plus(window));
            if (!remaining.isNegative()) {
                throw new TooManyRequestsException(
                        "Too many failed login attempts. Try again in " + Math.max(1, remaining.toMinutes()) + " minute(s).");
            }
            failures.remove(key(email));
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        failures.merge(key(email), new Failures(1, now), (old, ignored) ->
                old.windowStart().plus(window).isBefore(now) ? new Failures(1, now) : new Failures(old.count() + 1, old.windowStart()));
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
