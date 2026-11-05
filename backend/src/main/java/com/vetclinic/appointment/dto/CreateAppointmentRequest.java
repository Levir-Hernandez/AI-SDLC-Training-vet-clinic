package com.vetclinic.appointment.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record CreateAppointmentRequest(@NotNull Long petId, @NotNull Long serviceId, @NotNull Long veterinarianId,
		@NotNull LocalDateTime startAt) {
}
