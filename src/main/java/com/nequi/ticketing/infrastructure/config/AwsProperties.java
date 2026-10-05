package com.nequi.ticketing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        String dynamodbEndpoint,
        String sqsEndpoint,
        String ordersQueueUrl
) {}
