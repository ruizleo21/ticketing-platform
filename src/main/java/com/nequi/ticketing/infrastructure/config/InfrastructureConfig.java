package com.nequi.ticketing.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class InfrastructureConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
