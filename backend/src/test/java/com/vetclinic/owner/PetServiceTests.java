package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.shared.error.DomainException;

class PetServiceTests {

	PetRepository pets = Mockito.mock(PetRepository.class);
	OwnerRepository owners = Mockito.mock(OwnerRepository.class);
	PetService service;
	Owner ownerA = owner(1L);
	Owner ownerB = owner(2L);

	static Owner owner(Long id) {
		Owner o = new Owner("O" + id, "555" + id);
		ReflectionTestUtils.setField(o, "id", id);
		return o;
	}

	CurrentUser ownerUser(Long ownerId) {
		return new CurrentUser(10L, Role.OWNER, ownerId, null);
	}

	@BeforeEach
	void setUp() {
		service = new PetService(pets, owners);
		when(owners.findById(1L)).thenReturn(Optional.of(ownerA));
		when(pets.save(any(Pet.class))).thenAnswer(i -> i.getArgument(0));
	}

	private void code(Runnable r, String code) {
		assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo(code);
	}

	@Test
	void addsDogWithLabrador() {
		var res = service.add(1L, new CreatePetRequest("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER));
		assertThat(res.breed()).isEqualTo(Breed.LABRADOR_RETRIEVER);
		assertThat(res.ownerId()).isEqualTo(1L);
	}

	@Test
	void dogWithPersianMismatch() {
		code(() -> service.add(1L, new CreatePetRequest("Rex", Species.DOG, Breed.PERSIAN)),
				"BREED_SPECIES_MISMATCH");
	}

	@Test
	void ownerListsOnlyOwnPetsIgnoringOwnerId() {
		var pageable = PageRequest.of(0, 10);
		Pet p = new Pet("Rex", Species.DOG, Breed.BULLDOG, ownerA);
		when(pets.findByOwnerId(eq(1L), any(org.springframework.data.domain.Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(p)));

		var res = service.listFor(ownerUser(1L), 2L, pageable);

		assertThat(res.content()).hasSize(1);
		verify(pets).findByOwnerId(1L, pageable);
	}

	@Test
	void ownerCannotReadOtherOwnersPet() {
		Pet p = new Pet("Tom", Species.CAT, Breed.PERSIAN, ownerB);
		when(pets.findById(5L)).thenReturn(Optional.of(p));
		code(() -> service.requireAccessible(ownerUser(1L), 5L), "PET_NOT_FOUND");
	}

	@Test
	void vetForbidden() {
		var vet = new CurrentUser(11L, Role.VETERINARIAN, null, 3L);
		assertThatThrownBy(() -> service.listFor(vet, null, PageRequest.of(0, 10)))
			.isInstanceOf(com.vetclinic.shared.error.ForbiddenException.class);
	}

	@Test
	void receptionistWithoutOwnerIdBadRequest() {
		var rec = new CurrentUser(12L, Role.RECEPTIONIST, null, null);
		code(() -> service.listFor(rec, null, PageRequest.of(0, 10)), "OWNER_ID_REQUIRED");
	}
}
