package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** {@code veterinarianId} is optional: when absent the original veterinarian is kept (A6). */
public record RescheduleRequest(Long veterinarianId, @NotNull LocalDateTime startAt) {
}
