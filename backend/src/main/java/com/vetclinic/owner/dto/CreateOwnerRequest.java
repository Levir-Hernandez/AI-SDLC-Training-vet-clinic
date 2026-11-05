package com.vetclinic.owner.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOwnerRequest(@NotBlank String name,
		@NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "must be a valid phone number") String phone,
		@NotNull @Size(min = 6) String password, @Valid List<CreatePetRequest> pets) {
}
