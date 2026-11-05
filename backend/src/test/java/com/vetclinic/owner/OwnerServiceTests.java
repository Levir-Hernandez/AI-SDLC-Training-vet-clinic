package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccount;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.shared.error.DomainException;

class OwnerServiceTests {

	private OwnerRepository owners;

	private PetRepository pets;

	private UserAccountRepository accountRepo;

	private OwnerService service;

	@BeforeEach
	void setUp() {
		owners = Mockito.mock(OwnerRepository.class);
		pets = Mockito.mock(PetRepository.class);
		accountRepo = Mockito.mock(UserAccountRepository.class);
		PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
		when(encoder.encode("secret1")).thenReturn("HASHED");
		when(owners.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));
		when(pets.save(any(Pet.class))).thenAnswer(i -> i.getArgument(0));
		when(accountRepo.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
		service = new OwnerService(owners, pets, new PetService(pets, owners),
				new AccountService(accountRepo, encoder));
	}

	@Test
	void createsOwnerAccountAndInitialPets() {
		CreateOwnerRequest req = new CreateOwnerRequest("Ann", "555-1234", "secret1",
				List.of(new CreatePetRequest("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER)));

		OwnerResponse res = service.create(req);

		assertThat(res.phone()).isEqualTo("555-1234");
		assertThat(res.pets()).hasSize(1);
		ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
		verify(accountRepo).save(captor.capture());
		assertThat(captor.getValue().getUsername()).isEqualTo("555-1234");
		assertThat(captor.getValue().getRole()).isEqualTo(Role.OWNER);
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
	}

	@Test
	void duplicatePhoneIsRejected() {
		when(owners.existsByPhone("555-1234")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateOwnerRequest("Ann", "555-1234", "secret1", null)))
			.isInstanceOf(DomainException.class)
			.extracting("code")
			.isEqualTo("PHONE_TAKEN");
		verify(owners, never()).save(any());
	}

	@Test
	void mismatchedInitialPetFailsAndSavesNoPet() {
		CreateOwnerRequest req = new CreateOwnerRequest("Ann", "555-1234", "secret1",
				List.of(new CreatePetRequest("Tom", Species.DOG, Breed.PERSIAN)));

		assertThatThrownBy(() -> service.create(req)).isInstanceOf(DomainException.class)
			.extracting("code")
			.isEqualTo("BREED_SPECIES_MISMATCH");
		verify(pets, never()).save(any());
	}
}
