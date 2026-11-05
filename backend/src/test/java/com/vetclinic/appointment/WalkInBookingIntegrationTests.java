package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class WalkInBookingIntegrationTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;
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
	String token;
	LocalDateTime start;

	@BeforeEach
	void setUp() throws Exception {
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		owner = owners.save(new Owner("Walk", "walk-" + UUID.randomUUID()));
		pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
		vet = vets.save(new Veterinarian("Dr. Walk", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Walk grooming", 60, new BigDecimal("40"))));
		start = LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)).atTime(10, 0);
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"reception\",\"password\":\"reception123\"}"))
			.andReturn().getResponse().getContentAsString();
		token = json.readTree(body).get("token").asString();
	}

	@AfterEach
	void tearDown() {
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	private String body() {
		return "{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId() + ",\"veterinarianId\":" + vet.getId()
				+ ",\"startAt\":\"" + start + "\"}";
	}

	private MvcResult book() throws Exception {
		return mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body())
			.header("Authorization", "Bearer " + token)).andReturn();
	}

	private boolean slotListed() throws Exception {
		String res = mockMvc.perform(get("/api/availability").param("serviceId", service.getId().toString())
			.param("veterinarianId", vet.getId().toString()).param("date", start.toLocalDate().toString())
			.header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
		JsonNode slots = json.readTree(res);
		for (JsonNode s : slots) {
			if (s.get("startAt").asString().startsWith(start.toString())) {
				return true;
			}
		}
		return false;
	}

	@Test
	void walkInIsScheduledAndSlotDisappears() throws Exception {
		assertThat(slotListed()).isTrue();

		MvcResult result = book();

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		JsonNode created = json.readTree(result.getResponse().getContentAsString());
		assertThat(created.get("status").asString()).isEqualTo("SCHEDULED");
		assertThat(created.get("bookedBy").asString()).isEqualTo("RECEPTIONIST");
		assertThat(created.get("total").decimalValue()).isEqualByComparingTo(service.getBasePrice().add(new BigDecimal("20")));
		assertThat(slotListed()).isFalse();
	}

	@Test
	void concurrentPostsOfTheSameSlotYieldOneCreated() throws Exception {
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					go.await();
					return book().getResponse().getStatus();
				}));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> f : futures) {
				statuses.add(f.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
