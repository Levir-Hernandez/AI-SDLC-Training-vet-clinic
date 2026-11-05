package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vetclinic.appointment.dto.CreateAppointmentRequest;
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
import com.vetclinic.owner.Species;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

@SpringBootTest
class BookingConcurrencyIntegrationTests {

	@Autowired
	BookingService booking;
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

	Owner owner;
	Pet pet;
	Veterinarian vet;
	ClinicService service;

	Set<Long> existingIds;

	@BeforeEach
	void setUp() {
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		owner = owners.save(new Owner("Race", "race-" + UUID.randomUUID()));
		pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
		vet = vets.save(new Veterinarian("Dr. Race", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Race grooming", 60, new BigDecimal("40"))));
	}

	@AfterEach
	void tearDown() {
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	@Test
	void twoSimultaneousBookingsOfTheSameSlotYieldExactlyOne() throws Exception {
		LocalDate wednesday = LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
		LocalDateTime start = wednesday.atTime(10, 0);
		CurrentUser reception = new CurrentUser(1L, Role.RECEPTIONIST, null, null);
		CreateAppointmentRequest req = new CreateAppointmentRequest(pet.getId(), service.getId(), vet.getId(), start);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Callable<String> task = () -> {
					go.await();
					try {
						booking.book(reception, req);
						return "OK";
					}
					catch (DomainException e) {
						return e.getCode();
					}
				};
				results.add(pool.submit(task));
			}
			go.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> f : results) {
				outcomes.add(f.get());
			}

			assertThat(outcomes).containsExactlyInAnyOrder("OK", "SLOT_TAKEN");
			assertThat(appointments.count()).isEqualTo(existingIds.size() + 1);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
