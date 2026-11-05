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
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccount;
import com.vetclinic.auth.UserAccountRepository;
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
class OwnerScopeIntegrationTests {

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
	@Autowired
	UserAccountRepository accounts;
	@Autowired
	PasswordEncoder encoder;

	Set<Long> existingIds;
	final List<UserAccount> createdAccounts = new ArrayList<>();
	final List<Pet> createdPets = new ArrayList<>();
	final List<Owner> createdOwners = new ArrayList<>();
	Veterinarian vet;
	ClinicService service;
	Owner ownerA;
	Owner ownerB;
	Pet petA1;
	Pet petA2;
	Pet petB;
	String tokenA;
	String tokenB;
	LocalDateTime day1;
	LocalDateTime day2;

	@BeforeEach
	void setUp() throws Exception {
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		vet = vets.save(new Veterinarian("Dr. Scope", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Scope grooming", 60, new BigDecimal("40"))));
		ownerA = newOwner("A");
		ownerB = newOwner("B");
		petA1 = newPet("Ace", ownerA);
		petA2 = newPet("Ava", ownerA);
		petB = newPet("Bo", ownerB);
		tokenA = signIn(ownerA);
		tokenB = signIn(ownerB);
		LocalDate friday = LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
		day1 = friday.atTime(10, 0);
		day2 = friday.plusWeeks(1).atTime(10, 0);
	}

	@AfterEach
	void tearDown() {
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		accounts.deleteAll(createdAccounts);
		pets.deleteAll(createdPets);
		owners.deleteAll(createdOwners);
		vets.delete(vet);
	}

	private Owner newOwner(String name) {
		Owner o = owners.save(new Owner("Scope" + name, "scope-" + UUID.randomUUID()));
		createdOwners.add(o);
		return o;
	}

	private Pet newPet(String name, Owner o) {
		Pet p = pets.save(new Pet(name, Species.CAT, Breed.SIAMESE, o));
		createdPets.add(p);
		return p;
	}

	private String signIn(Owner o) throws Exception {
		String username = "owner-" + UUID.randomUUID();
		createdAccounts.add(accounts.save(
				new UserAccount(username, encoder.encode("secret123"), Role.OWNER, "Scope", o.getId(), null)));
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
			.andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asString();
	}

	private long book(String token, Pet pet, LocalDateTime start) throws Exception {
		MvcResult r = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId() + ",\"veterinarianId\":"
					+ vet.getId() + ",\"startAt\":\"" + start + "\"}")
			.header("Authorization", "Bearer " + token)).andReturn();
		assertThat(r.getResponse().getStatus()).isEqualTo(201);
		return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void ownerListsOnlyOwnAppointmentsAcrossPetsAndDays() throws Exception {
		long a1 = book(tokenA, petA1, day1);
		long a2 = book(tokenA, petA2, day2);
		long b = book(tokenB, petB, day1.plusHours(2));

		String res = mockMvc.perform(get("/api/appointments").param("size", "100").header("Authorization", "Bearer " + tokenA))
			.andReturn().getResponse().getContentAsString();
		List<Long> ids = new ArrayList<>();
		for (JsonNode n : json.readTree(res).get("content")) {
			ids.add(n.get("id").asLong());
			assertThat(n.get("service").get("durationMinutes").asInt()).isPositive();
		}

		assertThat(ids).containsExactlyInAnyOrder(a1, a2);
		assertThat(ids).doesNotContain(b);
	}

	@Test
	void ownerCannotReadOrCancelAnotherOwnersAppointment() throws Exception {
		long b = book(tokenB, petB, day1);

		int read = mockMvc.perform(get("/api/appointments/" + b).header("Authorization", "Bearer " + tokenA))
			.andReturn().getResponse().getStatus();
		int cancel = mockMvc.perform(post("/api/appointments/" + b + "/cancel").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"nope\"}").header("Authorization", "Bearer " + tokenA))
			.andReturn().getResponse().getStatus();

		assertThat(read).isEqualTo(404);
		assertThat(cancel).isEqualTo(404);
	}

	@Test
	void ownerCancelsOwnAppointmentAndSecondCancelConflicts() throws Exception {
		long a = book(tokenA, petA1, day1);
		String body = "{\"reason\":\"Pet is ill\"}";

		MvcResult first = mockMvc.perform(post("/api/appointments/" + a + "/cancel")
			.contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + tokenA)).andReturn();
		MvcResult second = mockMvc.perform(post("/api/appointments/" + a + "/cancel")
			.contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + tokenA)).andReturn();

		assertThat(first.getResponse().getStatus()).isEqualTo(200);
		assertThat(json.readTree(first.getResponse().getContentAsString()).get("status").asString())
			.isEqualTo("CANCELLED");
		assertThat(second.getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void petsQueryForAnotherOwnerReturnsOnlyOwnPets() throws Exception {
		String res = mockMvc.perform(get("/api/pets").param("ownerId", ownerB.getId().toString())
			.header("Authorization", "Bearer " + tokenA)).andReturn().getResponse().getContentAsString();
		List<Long> ids = new ArrayList<>();
		for (JsonNode n : json.readTree(res).get("content")) {
			ids.add(n.get("id").asLong());
		}

		assertThat(ids).containsExactlyInAnyOrder(petA1.getId(), petA2.getId());
	}
}
