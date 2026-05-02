package com.ibm.codeanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures a dedicated thread pool for async analysis tasks so large repo
 * scans don't block the HTTP thread pool.
 */
@Configuration
public class AsyncConfig {

    @Value("${analyzer.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${analyzer.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${analyzer.async.queue-capacity:25}")
    private int queueCapacity;

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("analyzer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
