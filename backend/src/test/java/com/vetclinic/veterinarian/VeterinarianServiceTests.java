package com.vetclinic.veterinarian;

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
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import com.vetclinic.veterinarian.dto.VeterinarianResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class VeterinarianServiceTests {

    @Mock VeterinarianRepository veterinarians;
    @Mock UserAccountRepository userAccounts;
    @Mock PasswordEncoder encoder;

    VeterinarianService service;

    @BeforeEach
    void setUp() {
        service = new VeterinarianService(veterinarians, new ScheduleValidator(),
                new AccountService(userAccounts, encoder));
    }

    private static ScheduleBlockDto block(int startHour, int endHour) {
        return new ScheduleBlockDto(DayOfWeek.MONDAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static CreateVeterinarianRequest request(String username, List<ScheduleBlockDto> schedule) {
        return new CreateVeterinarianRequest("Ana", "General medicine", new BigDecimal("20"), username,
                "secret1", schedule);
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void createsVeterinarianAndLinkedAccountWithHashedPassword() {
        when(veterinarians.save(any(Veterinarian.class))).thenAnswer(inv -> {
            Veterinarian v = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(v, "id", 7L);
            return v;
        });
        when(userAccounts.existsByUsername("vet.ana")).thenReturn(false);
        when(encoder.encode("secret1")).thenReturn("HASH");
        when(userAccounts.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        VeterinarianResponse response = service.create(request("vet.ana", List.of(block(9, 13), block(14, 18))));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.costPerService()).isEqualByComparingTo("20.00");
        assertThat(response.schedule()).hasSize(2);
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccounts).save(captor.capture());
        UserAccount account = captor.getValue();
        assertThat(account.getRole()).isEqualTo(Role.VETERINARIAN);
        assertThat(account.getVeterinarianId()).isEqualTo(7L);
        assertThat(account.getPasswordHash()).isEqualTo("HASH");
    }

    @Test
    void overlappingScheduleIsRejected() {
        assertCode(() -> service.create(request("vet.ana", List.of(block(9, 13), block(12, 18)))),
                "INVALID_SCHEDULE");
        verify(veterinarians, never()).save(any());
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(veterinarians.save(any(Veterinarian.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userAccounts.existsByUsername("vet.ana")).thenReturn(true);

        assertCode(() -> service.create(request("vet.ana", List.of(block(9, 13)))), "USERNAME_TAKEN");
        verify(userAccounts, never()).save(any());
    }

    @Test
    void unknownVeterinarianIsNotFound() {
        when(veterinarians.findById(99L)).thenReturn(Optional.empty());

        assertCode(() -> service.get(99L), "VETERINARIAN_NOT_FOUND");
    }
}
