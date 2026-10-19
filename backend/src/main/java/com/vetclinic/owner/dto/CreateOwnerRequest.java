package com.vetclinic.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOwnerRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @NotNull @Size(min = 6) String password,
        @Valid List<CreatePetRequest> pets) {
}
