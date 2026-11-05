package com.vetclinic.appointment;

import static com.vetclinic.appointment.Fixtures.NOW;
import static com.vetclinic.appointment.Fixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.vetclinic.appointment.Fixtures.MutableClock;
import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.PetService;
import com.vetclinic.owner.Species;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

@DataJpaTest
@Import({ AppointmentQueryService.class, AppointmentLifecycle.class, PetService.class,
		AppointmentQueryServiceTests.ClockConfig.class })
class AppointmentQueryServiceTests {

	@TestConfiguration
	static class ClockConfig {

		@Bean
		Clock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired
	AppointmentQueryService queries;
	@Autowired
	AppointmentRepository appointments;
	@Autowired
	OwnerRepository owners;
	@Autowired
	PetRepository pets;
	@Autowired
	VeterinarianRepository vets;
	@Autowired
	ClinicServiceRepository services;
	@Autowired
	PaymentRepository payments;

	Owner ann;
	Owner bob;
	Pet annPet;
	Pet bobPet;
	Veterinarian vetA;
	Veterinarian vetB;
	ClinicService service;
	CurrentUser reception = Fixtures.receptionist();

	@BeforeEach
	void setUp() {
		ann = owners.save(new Owner("Ann", "555-1"));
		bob = owners.save(new Owner("Bob", "555-2"));
		annPet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, ann));
		bobPet = pets.save(new Pet("Tom", Species.CAT, Breed.PERSIAN, bob));
		List<ScheduleBlock> blocks = List.of(new ScheduleBlock(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));
		vetA = vets.save(new Veterinarian("Dr. A", "General", new BigDecimal("20"), blocks));
		vetB = vets.save(new Veterinarian("Dr. B", "General", new BigDecimal("20"), blocks));
		service = services.save(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("40")));
	}

	private Appointment save(Pet pet, Veterinarian vet, LocalDateTime start, AppointmentStatus status) {
		return appointments.saveAndFlush(new Appointment(pet, service, vet, start, start.plusMinutes(60), status,
				Actor.RECEPTIONIST, new BigDecimal("60"), NOW));
	}

	private CurrentUser owner(Owner o) {
		return new CurrentUser(1L, Role.OWNER, o.getId(), null);
	}

	private CurrentUser vet(Veterinarian v) {
		return new CurrentUser(2L, Role.VETERINARIAN, null, v.getId());
	}

	private void code(Runnable r, String code) {
		assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
			.extracting(e -> ((DomainException) e).getCode())
			.isEqualTo(code);
	}

	@Test
	void ownerListsOnlyOwnAppointments() {
		save(annPet, vetA, START, AppointmentStatus.SCHEDULED);
		save(bobPet, vetA, START.plusHours(2), AppointmentStatus.SCHEDULED);

		var page = queries.list(owner(ann), AppointmentFilter.none(), PageRequest.of(0, 10));

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).pet().name()).isEqualTo("Rex");
		assertThat(page.content().get(0).ownerName()).isEqualTo("Ann");
	}

	@Test
	void veterinarianListsOnlyOwnAppointments() {
		save(annPet, vetA, START, AppointmentStatus.SCHEDULED);
		save(bobPet, vetB, START, AppointmentStatus.SCHEDULED);

		var page = queries.list(vet(vetB), AppointmentFilter.none(), PageRequest.of(0, 10));

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).veterinarian().id()).isEqualTo(vetB.getId());
	}

	@Test
	void receptionistSeesAllSortedByStartAndFiltersByStatusAndDate() {
		save(annPet, vetA, START.plusHours(2), AppointmentStatus.SCHEDULED);
		save(bobPet, vetB, START, AppointmentStatus.SCHEDULED);
		save(bobPet, vetB, START.plusDays(7), AppointmentStatus.CANCELLED);

		var all = queries.list(reception, AppointmentFilter.none(), PageRequest.of(0, 10));
		var day = queries.list(reception,
				new AppointmentFilter(List.of(AppointmentStatus.SCHEDULED), START.toLocalDate(), null, null),
				PageRequest.of(0, 10));
		var byVet = queries.list(reception, new AppointmentFilter(null, null, vetA.getId(), null), PageRequest.of(0, 10));

		assertThat(all.content()).hasSize(3);
		assertThat(all.content().get(0).startAt()).isEqualTo(START);
		assertThat(day.content()).hasSize(2);
		assertThat(byVet.content()).hasSize(1);
	}

	@Test
	void debtFilterKeepsOnlyClosedAppointmentsWithoutPayment() {
		Appointment unpaid = save(annPet, vetA, START, AppointmentStatus.CLOSED);
		Appointment paid = save(annPet, vetA, START.plusHours(2), AppointmentStatus.CLOSED);
		payments.saveAndFlush(new Payment(paid, new BigDecimal("60"), NOW));
		save(annPet, vetA, START.plusHours(4), AppointmentStatus.FINISHED);

		var debts = queries.list(reception, new AppointmentFilter(null, null, null, true), PageRequest.of(0, 10));

		assertThat(debts.content()).extracting(AppointmentResponse::id).containsExactly(unpaid.getId());
		assertThat(debts.content().get(0).debt()).isTrue();
		assertThat(queries.get(reception, paid.getId()).debt()).isFalse();
	}

	@Test
	void historyIsNewestFirstWithUpcomingFlags() {
		save(annPet, vetA, NOW.minusDays(2), AppointmentStatus.CLOSED);
		save(annPet, vetA, START, AppointmentStatus.SCHEDULED);
		save(annPet, vetA, NOW.minusDays(1), AppointmentStatus.CANCELLED);

		var history = queries.historyForPet(owner(ann), annPet.getId(), PageRequest.of(0, 10));

		assertThat(history.content()).extracting(AppointmentResponse::startAt)
			.containsExactly(START, NOW.minusDays(1), NOW.minusDays(2));
		assertThat(history.content()).extracting(AppointmentResponse::upcoming).containsExactly(true, false, false);
	}

	@Test
	void allowedActionsComeFromTheLifecycle() {
		Appointment a = save(annPet, vetA, START, AppointmentStatus.SCHEDULED);

		assertThat(queries.get(reception, a.getId()).allowedActions()).contains(AppointmentAction.START,
				AppointmentAction.RESCHEDULE, AppointmentAction.CANCEL);
		assertThat(queries.get(owner(ann), a.getId()).allowedActions()).containsExactly(AppointmentAction.CANCEL);
	}

	@Test
	void otherOwnersAppointmentIsNotFound() {
		Appointment a = save(bobPet, vetA, START, AppointmentStatus.SCHEDULED);

		code(() -> queries.get(owner(ann), a.getId()), "APPOINTMENT_NOT_FOUND");
	}

	@Test
	void otherVetsAppointmentIsNotFound() {
		Appointment a = save(bobPet, vetA, START, AppointmentStatus.SCHEDULED);

		code(() -> queries.get(vet(vetB), a.getId()), "APPOINTMENT_NOT_FOUND");
	}

	@Test
	void otherOwnersPetHistoryIsNotFound() {
		code(() -> queries.historyForPet(owner(ann), bobPet.getId(), PageRequest.of(0, 10)), "PET_NOT_FOUND");
	}

	@Test
	void veterinarianCannotReadPetHistory() {
		assertThatThrownBy(() -> queries.historyForPet(vet(vetA), annPet.getId(), PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class);
	}
}
