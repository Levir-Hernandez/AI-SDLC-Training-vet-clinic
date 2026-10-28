package com.vetclinic.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOwnerRequest(
        @NotBlank(message = "Enter the owner's name.") String name,
        @NotBlank(message = "Enter a phone number.")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number (7 to 20 digits).") String phone,
        @NotNull(message = "Enter a password.") @Size(min = 6, message = "The password must have at least 6 characters.") String password,
        @Valid List<CreatePetRequest> pets) {
}
