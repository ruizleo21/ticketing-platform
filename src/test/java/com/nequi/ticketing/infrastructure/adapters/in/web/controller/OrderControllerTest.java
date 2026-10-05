package com.nequi.ticketing.infrastructure.adapters.in.web.controller;

import com.nequi.ticketing.application.port.in.ConfirmOrderUseCase;
import com.nequi.ticketing.application.port.in.CreateOrderUseCase;
import com.nequi.ticketing.application.port.in.GetOrderStatusUseCase;
import com.nequi.ticketing.domain.model.EventId;
import com.nequi.ticketing.domain.model.Order;
import com.nequi.ticketing.domain.model.OrderId;
import com.nequi.ticketing.domain.model.OrderStatus;
import com.nequi.ticketing.domain.model.TicketId;
import com.nequi.ticketing.infrastructure.adapters.in.web.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  private static final String ORDER_ID_VALUE = "order-1";
  private static final String EVENT_ID_VALUE = "event-1";
  private static final String USER_ID = "user-1";
  private static final String IDEMPOTENCY_KEY = "idem-1";
  private static final Instant NOW = Instant.parse("2030-06-15T10:00:00Z");
  private static final List<String> REQUEST_TICKET_IDS = List.of("ticket-1", "ticket-2");
  private static final List<TicketId> DOMAIN_TICKET_IDS = List.of(
      new TicketId("ticket-1"), new TicketId("ticket-2"));

  @Mock
  private CreateOrderUseCase createOrderUseCase;

  @Mock
  private GetOrderStatusUseCase getOrderStatusUseCase;

  @Mock
  private ConfirmOrderUseCase confirmOrderUseCase;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    OrderController controller = new OrderController(
        createOrderUseCase,
        getOrderStatusUseCase,
        confirmOrderUseCase
    );
    webTestClient = WebTestClient.bindToController(controller).build();
  }

  @Test
  void shouldCreateOrderThroughHttpAndReturnAccepted() {
    Order order = order();
    when(createOrderUseCase.execute(
        EVENT_ID_VALUE, USER_ID, DOMAIN_TICKET_IDS, IDEMPOTENCY_KEY))
        .thenReturn(Mono.just(order));

    webTestClient.post()
        .uri("/api/v1/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", IDEMPOTENCY_KEY)
        .bodyValue(createOrderJson())
        .exchange()
        .expectStatus().isAccepted()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody(OrderResponse.class)
        .isEqualTo(OrderResponse.from(order));

    verify(createOrderUseCase).execute(
        EVENT_ID_VALUE, USER_ID, DOMAIN_TICKET_IDS, IDEMPOTENCY_KEY);
    verifyNoInteractions(getOrderStatusUseCase, confirmOrderUseCase);
  }

  @Test
  void shouldReturnBadRequestWhenIdempotencyHeaderIsMissing() {
    webTestClient.post()
        .uri("/api/v1/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(createOrderJson())
        .exchange()
        .expectStatus().isBadRequest();

    verifyNoInteractions(createOrderUseCase, getOrderStatusUseCase, confirmOrderUseCase);
  }

  @Test
  void shouldGetOrderThroughHttpAndReturnOk() {
    OrderId orderId = new OrderId(ORDER_ID_VALUE);
    Order order = order();
    when(getOrderStatusUseCase.execute(orderId)).thenReturn(Mono.just(order));

    webTestClient.get()
        .uri("/api/v1/orders/{orderId}", ORDER_ID_VALUE)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody(OrderResponse.class)
        .isEqualTo(OrderResponse.from(order));

    verify(getOrderStatusUseCase).execute(orderId);
    verifyNoInteractions(createOrderUseCase, confirmOrderUseCase);
  }

  @Test
  void shouldConfirmOrderThroughHttpAndReturnOk() {
    OrderId orderId = new OrderId(ORDER_ID_VALUE);
    Order order = order();
    when(confirmOrderUseCase.confirm(orderId)).thenReturn(Mono.just(order));

    webTestClient.post()
        .uri("/api/v1/orders/{orderId}/confirm", ORDER_ID_VALUE)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody(OrderResponse.class)
        .isEqualTo(OrderResponse.from(order));

    verify(confirmOrderUseCase).confirm(orderId);
    verifyNoInteractions(createOrderUseCase, getOrderStatusUseCase);
  }

  private static Map<String, Object> createOrderJson() {
    return Map.of(
        "eventId", EVENT_ID_VALUE,
        "userId", USER_ID,
        "ticketIds", REQUEST_TICKET_IDS
    );
  }

  private static Order order() {
    return new Order(
        new OrderId(ORDER_ID_VALUE),
        new EventId(EVENT_ID_VALUE),
        USER_ID,
        DOMAIN_TICKET_IDS,
        OrderStatus.RESERVED,
        NOW,
        NOW,
        NOW.plusSeconds(600),
        IDEMPOTENCY_KEY
    );
  }
}
