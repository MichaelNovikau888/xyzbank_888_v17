package com.bank.authorization.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            Environment env,
            @Value("${spring.application.name}") String appName,
            @Value("${app.version:unknown}") String version
    ) {
        return registry -> registry.config()
                .commonTags(
                        "application", appName,
                        "version", version,
                        "environment", String.join(",", env.getActiveProfiles())
                );
    }
}