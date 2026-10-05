package com.nequi.ticketing.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String name,
        @Future Instant date,
        @NotBlank String venue,
        @Positive int totalCapacity
) {}
