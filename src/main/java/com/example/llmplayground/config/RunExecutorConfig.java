package com.example.llmplayground.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** One background thread: runs are queued and executed one after another. */
@Configuration
public class RunExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor runExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("run-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
