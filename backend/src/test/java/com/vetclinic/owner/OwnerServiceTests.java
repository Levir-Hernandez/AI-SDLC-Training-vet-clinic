package com.vetclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccount;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.owner.dto.OwnerResponse;
import com.vetclinic.shared.error.DomainException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTests {

    @Mock OwnerRepository owners;
    @Mock PetRepository pets;
    @Mock UserAccountRepository userAccounts;
    @Mock PasswordEncoder encoder;

    OwnerService service;

    @BeforeEach
    void setUp() {
        service = new OwnerService(owners, pets, new PetService(pets, owners),
                new AccountService(userAccounts, encoder));
    }

    private static CreateOwnerRequest request(List<CreatePetRequest> petRequests) {
        return new CreateOwnerRequest("Maria Lopez", "555-123-4567", "secret1", petRequests);
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void createsOwnerAccountAndInitialPets() {
        when(owners.existsByPhone("555-123-4567")).thenReturn(false);
        when(owners.save(any(Owner.class))).thenAnswer(inv -> {
            Owner o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 3L);
            return o;
        });
        when(userAccounts.existsByUsername("555-123-4567")).thenReturn(false);
        when(encoder.encode("secret1")).thenReturn("HASH");
        when(userAccounts.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pets.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        OwnerResponse response = service.create(request(List.of(
                new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG))));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.pets()).hasSize(1);
        assertThat(response.pets().get(0).breed()).isEqualTo(Breed.BULLDOG);
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccounts).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(captor.getValue().getUsername()).isEqualTo("555-123-4567");
        assertThat(captor.getValue().getOwnerId()).isEqualTo(3L);
    }

    @Test
    void duplicatePhoneIsRejected() {
        when(owners.existsByPhone("555-123-4567")).thenReturn(true);

        assertCode(() -> service.create(request(List.of())), "PHONE_TAKEN");
        verify(owners, never()).save(any());
    }

    @Test
    void initialPetWithMismatchedBreedIsRejectedAndNothingSaved() {
        when(owners.existsByPhone("555-123-4567")).thenReturn(false);

        assertCode(() -> service.create(request(List.of(
                new CreatePetRequest("Tom", Species.DOG, Breed.PERSIAN)))), "BREED_SPECIES_MISMATCH");
        verify(owners, never()).save(any());
        verify(userAccounts, never()).save(any());
        verify(pets, never()).saveAll(any());
    }
}
