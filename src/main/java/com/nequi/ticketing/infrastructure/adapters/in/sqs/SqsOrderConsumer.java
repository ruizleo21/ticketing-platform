package com.nequi.ticketing.infrastructure.adapters.in.sqs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nequi.ticketing.application.port.in.ProcessOrderUseCase;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.infrastructure.config.AwsProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class SqsOrderConsumer {

  private final SqsAsyncClient client;
  private final AwsProperties properties;
  private final ObjectMapper objectMapper;
  private final ProcessOrderUseCase processOrderUseCase;

  public SqsOrderConsumer(SqsAsyncClient client, AwsProperties properties,
      ObjectMapper objectMapper, ProcessOrderUseCase processOrderUseCase) {
    this.client = client;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.processOrderUseCase = processOrderUseCase;
  }

  @Scheduled(fixedDelayString = "${ticketing.sqs.poll-ms:1000}")
  public void poll() {

    receive().flatMap(this::processMessage).onErrorContinue((error, value) -> {
      // Message remains in SQS until
      // visibility timeout expires.
    }).subscribe();
  }

  private Flux<Message> receive() {

    return Mono.fromFuture(client.receiveMessage(
            ReceiveMessageRequest.builder().queueUrl(properties.ordersQueueUrl())
                .maxNumberOfMessages(10).waitTimeSeconds(10).visibilityTimeout(30).build()))
        .flatMapMany(response -> Flux.fromIterable(response.messages()));
  }

  private Mono<Void> processMessage(Message message) {

    return Mono.fromCallable(() -> objectMapper.readValue(message.body(), Order.class))
        .flatMap(processOrderUseCase::execute).flatMap(order -> delete(message));
  }

  private Mono<Void> delete(Message message) {

    return Mono.fromFuture(client.deleteMessage(
        DeleteMessageRequest.builder().queueUrl(properties.ordersQueueUrl())
            .receiptHandle(message.receiptHandle()).build())).then();
  }
}