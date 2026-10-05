package com.nequi.ticketing.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(

    @NotBlank
    String eventId,

    @NotBlank
    String userId,

    @NotEmpty
    List<@NotBlank String> ticketIds
) {
}