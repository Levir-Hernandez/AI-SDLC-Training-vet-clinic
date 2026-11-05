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
class PetHistoryTests {

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
	Pet petA;
	Pet petB;
	String tokenA;
	String tokenB;
	String tokenReception;
	String tokenVet;
	LocalDateTime day1;
	LocalDateTime day2;

	@BeforeEach
	void setUp() throws Exception {
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		vet = vets.save(new Veterinarian("Dr. History", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "History grooming", 60, new BigDecimal("40"))));
		ownerA = newOwner("A");
		ownerB = newOwner("B");
		petA = newPet("Hera", ownerA);
		petB = newPet("Hugo", ownerB);
		tokenA = ownerSignIn(ownerA);
		tokenB = ownerSignIn(ownerB);
		tokenReception = login("reception", "reception123");
		tokenVet = login("vet.ana", "vet123");
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
		Owner o = owners.save(new Owner("History" + name, "history-" + UUID.randomUUID()));
		createdOwners.add(o);
		return o;
	}

	private Pet newPet(String name, Owner o) {
		Pet p = pets.save(new Pet(name, Species.CAT, Breed.SIAMESE, o));
		createdPets.add(p);
		return p;
	}

	private String login(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asString();
	}

	private String ownerSignIn(Owner o) throws Exception {
		String username = "owner-" + UUID.randomUUID();
		createdAccounts.add(accounts.save(
				new UserAccount(username, encoder.encode("secret123"), Role.OWNER, "History", o.getId(), null)));
		return login(username, "secret123");
	}

	private long book(String token, Pet pet, LocalDateTime start) throws Exception {
		MvcResult r = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId() + ",\"veterinarianId\":"
					+ vet.getId() + ",\"startAt\":\"" + start + "\"}")
			.header("Authorization", "Bearer " + token)).andReturn();
		assertThat(r.getResponse().getStatus()).isEqualTo(201);
		return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
	}

	private MvcResult history(String token, Object petId) throws Exception {
		return mockMvc.perform(get("/api/pets/" + petId + "/history").header("Authorization", "Bearer " + token))
			.andReturn();
	}

	private List<Long> ids(MvcResult r) throws Exception {
		List<Long> ids = new ArrayList<>();
		for (JsonNode n : json.readTree(r.getResponse().getContentAsString()).get("content")) {
			ids.add(n.get("id").asLong());
		}
		return ids;
	}

	@Test
	void receptionistGetsAllItemsLatestFirstIncludingCancelled() throws Exception {
		long first = book(tokenA, petA, day1);
		long second = book(tokenA, petA, day2);
		long other = book(tokenB, petB, day1.plusHours(2));
		mockMvc.perform(post("/api/appointments/" + first + "/cancel").contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Pet is ill\"}").header("Authorization", "Bearer " + tokenA));

		MvcResult r = history(tokenReception, petA.getId());

		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		assertThat(ids(r)).containsExactly(second, first);
		assertThat(ids(r)).doesNotContain(other);
	}

	@Test
	void ownerGetsOwnPetHistory() throws Exception {
		long a = book(tokenA, petA, day1);

		MvcResult r = history(tokenA, petA.getId());

		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		assertThat(ids(r)).containsExactly(a);
	}

	@Test
	void ownerReadingAnotherOwnersPetHistoryGets404() throws Exception {
		book(tokenB, petB, day1);

		assertThat(history(tokenA, petB.getId()).getResponse().getStatus()).isEqualTo(404);
	}

	@Test
	void veterinarianIsForbidden() throws Exception {
		assertThat(history(tokenVet, petA.getId()).getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void unknownPetGets404() throws Exception {
		assertThat(history(tokenReception, 999999999L).getResponse().getStatus()).isEqualTo(404);
	}
}
