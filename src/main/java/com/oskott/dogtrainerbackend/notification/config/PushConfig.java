package com.oskott.dogtrainerbackend.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(ExpoPushProperties.class)
public class PushConfig {

    public static final String PUSH_EXECUTOR = "pushExecutor";

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /**
     * Dedicated, bounded executor so push delivery never competes with request threads. When the
     * queue is full the push is dropped (and logged) instead of blocking the caller - the
     * notification itself is already persisted and shows up in the in-app history.
     */
    @Bean(name = PUSH_EXECUTOR)
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Push delivery queue is full; dropping push delivery task"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}