package ma.rh.ai.hr_workflow.execution.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ExecutionProperties.class)
public class ExecutionConfig {

    public static final String EXECUTOR = "workflowExecutor";

    /**
     * Bounded, Spring-managed pool for running executions. On shutdown it waits for running
     * steps to finish; a full queue rejects new work, which the sweeper picks up later.
     */
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor workflowExecutor(ExecutionProperties properties) {
        ExecutionProperties.Worker worker = properties.worker();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(worker.corePoolSize());
        executor.setMaxPoolSize(worker.maxPoolSize());
        executor.setQueueCapacity(worker.queueCapacity());
        executor.setThreadNamePrefix("workflow-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(worker.shutdownTimeout().toMillis());
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        return executor;
    }
}
