package com.vetclinic.owner.dto;

import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePetRequest(
        @NotBlank(message = "Enter the pet's name.") String name,
        @NotNull(message = "Select a species.") Species species,
        @NotNull(message = "Select a breed.") Breed breed) {
}
