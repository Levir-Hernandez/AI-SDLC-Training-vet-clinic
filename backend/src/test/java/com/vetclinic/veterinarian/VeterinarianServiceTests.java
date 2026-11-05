package com.vetclinic.veterinarian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccount;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;

class VeterinarianServiceTests {

	VeterinarianRepository vets = Mockito.mock(VeterinarianRepository.class);
	UserAccountRepository accountRepo = Mockito.mock(UserAccountRepository.class);
	PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
	VeterinarianService service;

	@BeforeEach
	void setUp() {
		service = new VeterinarianService(vets, new ScheduleValidator(), new AccountService(accountRepo, encoder));
		when(vets.save(any(Veterinarian.class))).thenAnswer(i -> i.getArgument(0));
		when(accountRepo.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
		when(encoder.encode("secret1")).thenReturn("HASHED");
	}

	private CreateVeterinarianRequest req(List<ScheduleBlockDto> blocks) {
		return new CreateVeterinarianRequest("Dr. Who", "Surgery", new BigDecimal("40"), "drwho", "secret1", blocks);
	}

	private ScheduleBlockDto block(int from, int to) {
		return new ScheduleBlockDto(DayOfWeek.MONDAY, LocalTime.of(from, 0), LocalTime.of(to, 0));
	}

	@Test
	void createsVetAndHashedAccount() {
		var res = service.create(req(List.of(block(9, 12), block(13, 17))));

		assertThat(res.name()).isEqualTo("Dr. Who");
		assertThat(res.schedule()).hasSize(2);
		ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
		verify(accountRepo).save(cap.capture());
		assertThat(cap.getValue().getRole()).isEqualTo(Role.VETERINARIAN);
		assertThat(cap.getValue().getPasswordHash()).isEqualTo("HASHED");
		assertThat(cap.getValue().getUsername()).isEqualTo("drwho");
	}

	@Test
	void overlappingScheduleRejected() {
		assertThatThrownBy(() -> service.create(req(List.of(block(9, 12), block(11, 14)))))
			.isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo("INVALID_SCHEDULE");
		verify(vets, never()).save(any());
	}

	@Test
	void duplicateUsernameRejected() {
		when(accountRepo.existsByUsername("drwho")).thenReturn(true);
		assertThatThrownBy(() -> service.create(req(List.of(block(9, 12)))))
			.isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo("USERNAME_TAKEN");
	}

	@Test
	void unknownIdNotFound() {
		when(vets.findById(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo("VETERINARIAN_NOT_FOUND");
	}
}
