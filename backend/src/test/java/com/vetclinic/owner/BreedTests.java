package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.vetclinic.shared.error.BadRequestException;

class BreedTests {

	@ParameterizedTest
	@EnumSource(Breed.class)
	void everyBreedBelongsToItsSpecies(Breed breed) {
		assertThat(breed.belongsTo(breed.species())).isTrue();
		Species other = breed.species() == Species.DOG ? Species.CAT : Species.DOG;
		assertThat(breed.belongsTo(other)).isFalse();
	}

	@Test
	void labradorDogIsCreated() {
		Pet pet = new Pet("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER, new Owner("Ann", "555"));
		assertThat(pet.getBreed()).isEqualTo(Breed.LABRADOR_RETRIEVER);
	}

	@Test
	void dogWithPersianIsRejected() {
		Owner owner = new Owner("Ann", "555");
		assertThatThrownBy(() -> new Pet("Tom", Species.DOG, Breed.PERSIAN, owner))
			.isInstanceOfSatisfying(BadRequestException.class,
					e -> assertThat(e.getCode()).isEqualTo("BREED_SPECIES_MISMATCH"));
	}
}
