package com.nequi.ticketing.infrastructure.adapters.in.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.nequi.ticketing.application.port.in.ProcessOrderUseCase;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.infrastructure.config.AwsProperties;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SqsOrderConsumerTest {

  private static final String QUEUE_URL = "https://sqs.example.test/orders";
  private static final String BODY = "{\"order\":\"payload\"}";
  private static final String RECEIPT_HANDLE = "receipt-123";

  @Mock
  private SqsAsyncClient client;

  @Mock
  private AwsProperties properties;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private ProcessOrderUseCase processOrderUseCase;

  private SqsOrderConsumer consumer;

  @BeforeEach
  void setUp() {
    when(properties.ordersQueueUrl()).thenReturn(QUEUE_URL);
    consumer = new SqsOrderConsumer(client, properties, objectMapper, processOrderUseCase);
  }

  @Test
  void shouldProcessMessageAndDeleteItAfterSuccessfulOrderProcessing() throws Exception {
    Order order = sampleOrder();
    Message message = message();
    when(client.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ReceiveMessageResponse.builder().messages(message).build()));
    when(objectMapper.readValue(BODY, Order.class)).thenReturn(order);
    when(processOrderUseCase.execute(order)).thenReturn(Mono.just(order));
    when(client.deleteMessage(any(DeleteMessageRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

    consumer.poll();

    verify(objectMapper).readValue(BODY, Order.class);
    verify(processOrderUseCase).execute(order);
    ArgumentCaptor<DeleteMessageRequest> deleteCaptor =
        ArgumentCaptor.forClass(DeleteMessageRequest.class);
    verify(client).deleteMessage(deleteCaptor.capture());
    assertEquals(QUEUE_URL, deleteCaptor.getValue().queueUrl());
    assertEquals(RECEIPT_HANDLE, deleteCaptor.getValue().receiptHandle());
  }

  @Test
  void shouldLeaveMessageInQueueWhenOrderProcessingFails() throws Exception {
    Order order = sampleOrder();
    Message message = message();
    when(client.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ReceiveMessageResponse.builder().messages(message).build()));
    when(objectMapper.readValue(BODY, Order.class)).thenReturn(order);
    when(processOrderUseCase.execute(order))
        .thenReturn(Mono.error(new IllegalStateException("processing failed")));

    consumer.poll();

    verify(processOrderUseCase).execute(order);
    verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void shouldNotDeleteMessageWhenDeserializationFails() throws Exception {
    Message message = message();
    when(client.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ReceiveMessageResponse.builder().messages(message).build()));
    when(objectMapper.readValue(BODY, Order.class))
        .thenThrow(new IllegalArgumentException("invalid order payload"));

    consumer.poll();

    verify(objectMapper).readValue(BODY, Order.class);
    verifyNoInteractions(processOrderUseCase);
    verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void shouldDoNothingWhenQueueHasNoMessages() {
    when(client.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(
            ReceiveMessageResponse.builder().messages(List.of()).build()));

    consumer.poll();

    verify(client).receiveMessage(any(ReceiveMessageRequest.class));
    verifyNoInteractions(objectMapper, processOrderUseCase);
    verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  private static Message message() {
    return Message.builder()
        .body(BODY)
        .receiptHandle(RECEIPT_HANDLE)
        .build();
  }

  private static Order sampleOrder() {
    Instant now = Instant.parse("2030-06-15T10:00:00Z");
    return new Order(
        new OrderId("order-1"),
        new EventId("event-1"),
        "user-1",
        List.of(new TicketId("ticket-1")),
        OrderStatus.RESERVED,
        now,
        now,
        now.plusSeconds(600),
        "idem-1"
    );
  }
}
