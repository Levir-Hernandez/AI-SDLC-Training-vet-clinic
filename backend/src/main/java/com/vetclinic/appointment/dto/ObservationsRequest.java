package com.vetclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ObservationsRequest(@NotNull @Size(max = 2000) String observations) {
}
