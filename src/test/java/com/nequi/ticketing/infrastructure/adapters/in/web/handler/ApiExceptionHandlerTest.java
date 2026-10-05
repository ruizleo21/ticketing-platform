package com.nequi.ticketing.infrastructure.adapters.in.web.handler;

import com.nequi.ticketing.domain.exception.TicketNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class ApiExceptionHandlerTest {

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    webTestClient = WebTestClient
        .bindToController(new ExceptionTestController())
        .controllerAdvice(new ApiExceptionHandler())
        .build();
  }

  @Test
  void shouldReturnConflictWhenDomainExceptionOccurs() {
    webTestClient.get()
        .uri("/test/domain-error")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.error").isEqualTo("Ticket is not available");
  }

  @Test
  void shouldReturnBadRequestWhenIllegalArgumentExceptionOccurs() {
    webTestClient.get()
        .uri("/test/argument-error")
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.error").isEqualTo("Invalid request argument");
  }

  @RestController
  static class ExceptionTestController {

    @GetMapping("/test/domain-error")
    Mono<Void> domainError() {
      return Mono.error(new TicketNotAvailableException("Ticket is not available"));
    }

    @GetMapping("/test/argument-error")
    Mono<Void> argumentError() {
      return Mono.error(new IllegalArgumentException("Invalid request argument"));
    }
  }
}