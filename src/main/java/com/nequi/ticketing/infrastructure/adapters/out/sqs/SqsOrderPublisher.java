package com.nequi.ticketing.infrastructure.adapters.out.sqs;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.nequi.ticketing.application.port.out.OrderPublisher;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.infrastructure.config.AwsProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class SqsOrderPublisher implements OrderPublisher {

    private final SqsAsyncClient client;
    private final AwsProperties properties;
    private final ObjectMapper objectMapper;

    public SqsOrderPublisher(SqsAsyncClient client, AwsProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Order order) {
        try {
            String body = objectMapper.writeValueAsString(order);
            return Mono.fromFuture(client.sendMessage(SendMessageRequest.builder()
                            .queueUrl(properties.ordersQueueUrl())
                            .messageBody(body)
                            .build()))
                    .then();
        } catch (JacksonException e) {
            return Mono.error(e);
        }
    }
}
