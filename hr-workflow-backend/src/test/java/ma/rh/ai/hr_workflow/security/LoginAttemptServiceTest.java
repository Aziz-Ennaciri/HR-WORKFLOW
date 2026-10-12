package ma.rh.ai.hr_workflow.security;

import ma.rh.ai.hr_workflow.exceptions.TooManyRequestsException;
import ma.rh.ai.hr_workflow.user.service.Impl.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LoginAttemptService")
class LoginAttemptServiceTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-12T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

    @Test
    @DisplayName("locks after the max failures, case-insensitively, and unlocks when the window passes")
    void locksAndUnlocks() {
        service.recordFailure("Bob@Test.com");
        service.recordFailure("bob@test.com");
        assertThatCode(() -> service.checkAllowed("bob@test.com")).doesNotThrowAnyException();

        service.recordFailure("bob@test.com ");
        assertThrows(TooManyRequestsException.class, () -> service.checkAllowed("BOB@test.com"));

        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThatCode(() -> service.checkAllowed("bob@test.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a successful login clears the failures")
    void successResets() {
        service.recordFailure("amy@test.com");
        service.recordFailure("amy@test.com");
        service.recordSuccess("amy@test.com");
        service.recordFailure("amy@test.com");

        assertThatCode(() -> service.checkAllowed("amy@test.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("failures older than the window start a new count")
    void oldFailuresExpire() {
        service.recordFailure("eve@test.com");
        service.recordFailure("eve@test.com");
        now.set(now.get().plus(Duration.ofMinutes(20)));
        service.recordFailure("eve@test.com");

        assertThatCode(() -> service.checkAllowed("eve@test.com")).doesNotThrowAnyException();
    }
}
