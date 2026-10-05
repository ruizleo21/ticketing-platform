package com.nequi.ticketing.infrastructure.adapters.out.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.infrastructure.config.AwsProperties;

import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SqsOrderPublisherTest {

  private static final String QUEUE_URL = "https://sqs.example.test/orders";
  private static final String SERIALIZED_ORDER = "{\"id\":\"order-1\"}";
  private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");

  @Mock
  private SqsAsyncClient client;

  @Mock
  private AwsProperties properties;

  @Mock
  private ObjectMapper objectMapper;

  private SqsOrderPublisher publisher;

  private static Order order() {
    return new Order(new OrderId("order-1"), new EventId("event-1"), "user-1",
        List.of(new TicketId("ticket-1")), OrderStatus.RESERVED, NOW, NOW, NOW.plusSeconds(600),
        "idem-1");
  }

  @BeforeEach
  void setUp() {
    publisher = new SqsOrderPublisher(client, properties, objectMapper);
  }

  @Test
  void shouldSerializeOrderAndSendMessageToConfiguredQueue() throws Exception {

    Order order = order();

    when(properties.ordersQueueUrl()).thenReturn(QUEUE_URL);

    when(objectMapper.writeValueAsString(order)).thenReturn(SERIALIZED_ORDER);

    when(client.sendMessage(any(SendMessageRequest.class))).thenReturn(
        CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

    StepVerifier.create(publisher.publish(order)).verifyComplete();

    verify(objectMapper).writeValueAsString(order);

    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);

    verify(client).sendMessage(captor.capture());

    assertEquals(QUEUE_URL, captor.getValue().queueUrl());

    assertEquals(SERIALIZED_ORDER, captor.getValue().messageBody());
  }

  @Test
  void shouldPropagateSqsSendFailure() throws Exception {

    Order order = order();

    RuntimeException sendFailure = new RuntimeException("SQS unavailable");

    when(properties.ordersQueueUrl()).thenReturn(QUEUE_URL);

    when(objectMapper.writeValueAsString(order)).thenReturn(SERIALIZED_ORDER);

    when(client.sendMessage(any(SendMessageRequest.class))).thenReturn(
        CompletableFuture.failedFuture(sendFailure));

    StepVerifier.create(publisher.publish(order))
        .expectErrorSatisfies(actual -> assertSame(sendFailure, actual)).verify();

    verify(objectMapper).writeValueAsString(order);

    verify(client).sendMessage(any(SendMessageRequest.class));
  }

  @Test
  void shouldReturnErrorAndNotSendMessageWhenSerializationFails() throws Exception {

    Order order = order();

    JacksonException serializationFailure = org.mockito.Mockito.mock(JacksonException.class);

    when(objectMapper.writeValueAsString(order)).thenThrow(serializationFailure);

    StepVerifier.create(publisher.publish(order))
        .expectErrorSatisfies(actual -> assertSame(serializationFailure, actual)).verify();

    verify(objectMapper).writeValueAsString(order);

    verify(client, never()).sendMessage(any(SendMessageRequest.class));
  }
}
