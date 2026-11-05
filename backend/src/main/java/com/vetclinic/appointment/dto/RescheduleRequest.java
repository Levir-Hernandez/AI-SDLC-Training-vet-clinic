package com.vetclinic.appointment.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record RescheduleRequest(@NotNull Long veterinarianId, @NotNull LocalDateTime startAt) {
}
