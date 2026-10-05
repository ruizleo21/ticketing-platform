package com.nequi.ticketing.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

@Configuration
public class AwsClientConfig {

    @Bean
    DynamoDbAsyncClient dynamoDbAsyncClient(AwsProperties properties) {
        var builder = DynamoDbAsyncClient.builder()
                .region(Region.of(properties.region()));

        if (properties.dynamodbEndpoint() != null && !properties.dynamodbEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.dynamodbEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")));
        }

        return builder.build();
    }

    @Bean
    SqsAsyncClient sqsAsyncClient(AwsProperties properties) {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(properties.region()));

        if (properties.sqsEndpoint() != null && !properties.sqsEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.sqsEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")));
        }

        return builder.build();
    }
}
