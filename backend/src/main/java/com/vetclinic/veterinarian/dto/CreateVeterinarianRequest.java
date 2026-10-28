package com.vetclinic.veterinarian.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateVeterinarianRequest(
        @NotBlank(message = "Enter the veterinarian's name.") String name,
        @NotBlank(message = "Enter the specialization.") String specialization,
        @NotNull(message = "Enter the cost per service.")
        @DecimalMin(value = "0.00", message = "The cost per service cannot be negative.") BigDecimal costPerService,
        @NotBlank(message = "Enter a username.") String username,
        @NotNull(message = "Enter a password.") @Size(min = 6, message = "The password must have at least 6 characters.") String password,
        @NotEmpty(message = "Add at least one schedule block.") @Valid List<ScheduleBlockDto> schedule) {
}
