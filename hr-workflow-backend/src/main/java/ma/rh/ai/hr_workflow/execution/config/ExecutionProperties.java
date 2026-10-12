package ma.rh.ai.hr_workflow.execution.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tuning for the workflow engine ({@code execution.*} in application.yml). */
@ConfigurationProperties(prefix = "execution")
public record ExecutionProperties(
        @DefaultValue Worker worker,
        @DefaultValue Retry retry,
        @DefaultValue Sweeper sweeper) {

    /** Thread pool that runs executions in the background. */
    public record Worker(
            @DefaultValue("4") int corePoolSize,
            @DefaultValue("8") int maxPoolSize,
            @DefaultValue("100") int queueCapacity,
            @DefaultValue("30s") Duration shutdownTimeout) {
    }

    /** Automatic retries of a step that failed with a transient error (timeout, 5xx, 429). */
    public record Retry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1s") Duration initialBackoff,
            @DefaultValue("2.0") double multiplier) {
    }

    /** Periodic pickup of executions that were never started or never resumed. */
    public record Sweeper(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30s") Duration interval,
            @DefaultValue("30s") Duration pendingGrace) {
    }
}
