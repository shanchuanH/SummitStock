package com.example.portfolio.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
@EnableScheduling
class WorkerRuntimeConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WorkerRuntimeConfiguration.class);

    @Bean
    ApplicationRunner workerRuntime() {
        return args -> log.info("event=worker_runtime_ready runtimeMode=worker");
    }
}
