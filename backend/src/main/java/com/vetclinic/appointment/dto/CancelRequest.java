package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HTTP blank reason is a 400 validation error; the lifecycle REASON_REQUIRED stays the service-level guard. */
public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
}
