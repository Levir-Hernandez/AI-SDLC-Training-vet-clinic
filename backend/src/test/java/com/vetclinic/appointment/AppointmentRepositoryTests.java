package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

@DataJpaTest
class AppointmentRepositoryTests {

	private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);
	private static final LocalDateTime START = LocalDateTime.of(2030, 1, 2, 10, 0);
	private static final LocalDateTime END = START.plusHours(1);

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

	Pet pet;
	ClinicService service;
	Veterinarian vet;

	@BeforeEach
	void setUp() {
		Owner owner = owners.save(new Owner("Ann", "555-1"));
		pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
		service = services.save(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("30")));
		vet = vets.save(new Veterinarian("Dr. A", "General", new BigDecimal("10"),
				List.of(new ScheduleBlock(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
	}

	private Appointment save(AppointmentStatus status, LocalDateTime reservedUntil) {
		Appointment a = new Appointment(pet, service, vet, START, END, status, Actor.RECEPTIONIST,
				new BigDecimal("40"), NOW);
		a.setReservedUntil(reservedUntil);
		return appointments.saveAndFlush(a);
	}

	private boolean overlap(LocalDateTime s, LocalDateTime e, Long exclude) {
		return appointments.existsOverlap(vet.getId(), s, e, NOW, exclude);
	}

	@Test
	void blockingStatusesOverlap() {
		save(AppointmentStatus.SCHEDULED, null);
		assertThat(overlap(START.plusMinutes(30), END.plusMinutes(30), null)).isTrue();
	}

	@Test
	void inProgressOverlaps() {
		save(AppointmentStatus.IN_PROGRESS, null);
		assertThat(overlap(START, END, null)).isTrue();
	}

	@Test
	void liveReservedOverlaps() {
		save(AppointmentStatus.RESERVED, NOW.plusMinutes(5));
		assertThat(overlap(START, END, null)).isTrue();
	}

	@Test
	void adjacentDoesNotOverlap() {
		save(AppointmentStatus.SCHEDULED, null);
		assertThat(overlap(END, END.plusHours(1), null)).isFalse();
		assertThat(overlap(START.minusHours(1), START, null)).isFalse();
	}

	@Test
	void excludeIdIgnoresThatAppointment() {
		Appointment a = save(AppointmentStatus.SCHEDULED, null);
		assertThat(overlap(START, END, a.getId())).isFalse();
	}

	@Test
	void nonBlockingStatusesDoNotCount() {
		save(AppointmentStatus.RESERVED, NOW.minusMinutes(1));
		save(AppointmentStatus.CANCELLED, null);
		save(AppointmentStatus.RESCHEDULED, null);
		save(AppointmentStatus.FINISHED, null);
		assertThat(overlap(START, END, null)).isFalse();
	}

	@Test
	void findBlockingReturnsOnlyBlockingOfTheDay() {
		save(AppointmentStatus.SCHEDULED, null);
		save(AppointmentStatus.CANCELLED, null);
		List<Appointment> found = appointments.findBlocking(vet.getId(), START.toLocalDate().atStartOfDay(),
				START.toLocalDate().plusDays(1).atStartOfDay(), NOW);
		assertThat(found).hasSize(1);
	}

	@Test
	void duplicatePhoneIsRejected() {
		assertThatThrownBy(() -> owners.saveAndFlush(new Owner("Bob", "555-1")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicatePaymentPerAppointmentIsRejected() {
		Appointment a = save(AppointmentStatus.FINISHED, null);
		payments.saveAndFlush(new Payment(a, new BigDecimal("40"), NOW));
		assertThatThrownBy(() -> payments.saveAndFlush(new Payment(a, new BigDecimal("40"), NOW)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
