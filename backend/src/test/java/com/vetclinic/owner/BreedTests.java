package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.shared.error.DomainException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BreedTests {

    private final Owner owner = new Owner("Ann", "555-0001");

    @Test
    void fiveBreedsPerSpecies() {
        for (Species s : Species.values()) {
            assertThat(Arrays.stream(Breed.values()).filter(b -> b.belongsTo(s))).hasSize(5);
        }
    }

    @Test
    void everyBreedBelongsToItsOwnSpeciesOnly() {
        for (Breed b : Breed.values()) {
            assertThat(b.belongsTo(b.species())).isTrue();
            for (Species other : Species.values()) {
                if (other != b.species()) {
                    assertThat(b.belongsTo(other)).isFalse();
                }
            }
        }
    }

    @Test
    void dogWithLabradorIsCreated() {
        Pet pet = new Pet("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER, owner);
        assertThat(pet.getBreed()).isEqualTo(Breed.LABRADOR_RETRIEVER);
    }

    @Test
    void dogWithPersianIsRejected() {
        assertThatThrownBy(() -> new Pet("Rex", Species.DOG, Breed.PERSIAN, owner))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BREED_SPECIES_MISMATCH"));
    }

    @Test
    void mismatchMessageNamesBreedAndSpecies() {
        assertThatThrownBy(() -> new Pet("Tom", Species.DOG, Breed.SIAMESE, owner))
                .hasMessage("Breed SIAMESE is not a DOG breed");
    }
}
