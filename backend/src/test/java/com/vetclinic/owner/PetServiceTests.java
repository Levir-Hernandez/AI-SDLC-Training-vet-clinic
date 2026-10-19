package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.PetResponse;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.ForbiddenException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PetServiceTests {

    @Mock PetRepository pets;
    @Mock OwnerRepository owners;

    PetService service;
    Owner ownerA;
    Owner ownerB;

    final CurrentUser asOwnerA = new CurrentUser(1L, Role.OWNER, 10L, null);
    final CurrentUser asReceptionist = new CurrentUser(2L, Role.RECEPTIONIST, null, null);
    final CurrentUser asVet = new CurrentUser(3L, Role.VETERINARIAN, null, 5L);

    @BeforeEach
    void setUp() {
        service = new PetService(pets, owners);
        ownerA = owner(10L, "A", "111-1111");
        ownerB = owner(20L, "B", "222-2222");
    }

    private static Owner owner(Long id, String name, String phone) {
        Owner o = new Owner(name, phone);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void addsDogWithLabradorRetriever() {
        when(owners.findById(10L)).thenReturn(Optional.of(ownerA));
        when(pets.save(any(Pet.class))).thenAnswer(inv -> inv.getArgument(0));

        PetResponse response = service.add(10L, new CreatePetRequest("Rex", Species.DOG, Breed.LABRADOR_RETRIEVER));

        assertThat(response.breed()).isEqualTo(Breed.LABRADOR_RETRIEVER);
        assertThat(response.ownerId()).isEqualTo(10L);
    }

    @Test
    void dogWithPersianBreedIsRejected() {
        when(owners.findById(10L)).thenReturn(Optional.of(ownerA));

        assertCode(() -> service.add(10L, new CreatePetRequest("Rex", Species.DOG, Breed.PERSIAN)),
                "BREED_SPECIES_MISMATCH");
        verify(pets, never()).save(any());
    }

    @Test
    void ownerListsOnlyOwnPetsIgnoringRequestedOwnerId() {
        var pageable = PageRequest.of(0, 10);
        Pet pet = new Pet("Rex", Species.DOG, Breed.BULLDOG, ownerA);
        when(pets.findByOwnerId(10L, pageable)).thenReturn(new PageImpl<>(List.of(pet), pageable, 1));

        PageResponse<PetResponse> result = service.listFor(asOwnerA, 20L, pageable);

        assertThat(result.content()).extracting(PetResponse::ownerId).containsExactly(10L);
        verify(pets, never()).findByOwnerId(20L, pageable);
    }

    @Test
    void receptionistListsPetsOfRequestedOwner() {
        var pageable = PageRequest.of(0, 10);
        Pet pet = new Pet("Tom", Species.CAT, Breed.SIAMESE, ownerB);
        when(pets.findByOwnerId(20L, pageable)).thenReturn(new PageImpl<>(List.of(pet), pageable, 1));

        assertThat(service.listFor(asReceptionist, 20L, pageable).content()).hasSize(1);
    }

    @Test
    void receptionistWithoutOwnerIdGetsBadRequest() {
        assertCode(() -> service.listFor(asReceptionist, null, PageRequest.of(0, 10)), "OWNER_ID_REQUIRED");
    }

    @Test
    void veterinarianListingIsForbidden() {
        assertThatThrownBy(() -> service.listFor(asVet, null, PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownerReadingAnotherOwnersPetGetsNotFound() {
        Pet other = new Pet("Tom", Species.CAT, Breed.SIAMESE, ownerB);
        when(pets.findById(99L)).thenReturn(Optional.of(other));

        assertCode(() -> service.requireAccessible(asOwnerA, 99L), "PET_NOT_FOUND");
    }

    @Test
    void ownerReadsOwnPet() {
        Pet own = new Pet("Rex", Species.DOG, Breed.BULLDOG, ownerA);
        when(pets.findById(1L)).thenReturn(Optional.of(own));

        assertThat(service.requireAccessible(asOwnerA, 1L)).isSameAs(own);
    }

    @Test
    void veterinarianAccessingPetIsForbidden() {
        assertThatThrownBy(() -> service.requireAccessible(asVet, 1L)).isInstanceOf(ForbiddenException.class);
    }
}
