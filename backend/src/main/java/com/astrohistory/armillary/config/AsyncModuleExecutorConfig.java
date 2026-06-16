package com.astrohistory.armillary.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncModuleExecutorConfig {

    @Bean(name = "frictionExecutor")
    public Executor frictionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("friction-sim-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(runnable -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("[frictionExecutor] 异步任务异常", e);
            }
        });
        executor.initialize();
        log.info("[AsyncConfig] frictionExecutor初始化完成: 核心=2, 最大=4");
        return executor;
    }

    @Bean(name = "pointingExecutor")
    public Executor pointingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("pointing-analyze-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[AsyncConfig] pointingExecutor初始化完成: 核心=1, 最大=2");
        return executor;
    }

    @Bean(name = "alarmExecutor")
    public Executor alarmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("alarm-ws-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[AsyncConfig] alarmExecutor初始化完成: 核心=2, 最大=4");
        return executor;
    }
}
