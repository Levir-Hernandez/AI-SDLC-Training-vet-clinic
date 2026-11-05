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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vetclinic.appointment.Fixtures.MutableClock;
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
class OnlineBookingIntegrationTests {

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(LocalDateTime.now(java.time.ZoneOffset.UTC));
		}
	}

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;
	@Autowired
	MutableClock clock;
	@Autowired
	AppointmentRepository appointments;
	@Autowired
	AppointmentActionService actions;
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

	Owner owner;
	Pet pet;
	Veterinarian vet;
	ClinicService service;
	UserAccount account;
	Set<Long> existingIds;
	String token;
	LocalDateTime start;

	@BeforeEach
	void setUp() throws Exception {
		clock.set(LocalDateTime.now(java.time.ZoneOffset.UTC));
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		String suffix = UUID.randomUUID().toString();
		owner = owners.save(new Owner("Online", "online-" + suffix));
		pet = pets.save(new Pet("Milo", Species.CAT, Breed.SIAMESE, owner));
		account = accounts.save(new UserAccount("owner-" + suffix, encoder.encode("secret123"), Role.OWNER, "Online",
				owner.getId(), null));
		vet = vets.save(new Veterinarian("Dr. Online", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Online grooming", 60, new BigDecimal("40"))));
		start = LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).atTime(10, 0);
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + account.getUsername() + "\",\"password\":\"secret123\"}"))
			.andReturn().getResponse().getContentAsString();
		token = json.readTree(body).get("token").asString();
	}

	@AfterEach
	void tearDown() {
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		accounts.delete(account);
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	private JsonNode reserve() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId() + ",\"veterinarianId\":"
					+ vet.getId() + ",\"startAt\":\"" + start + "\"}")
			.header("Authorization", "Bearer " + token)).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return json.readTree(result.getResponse().getContentAsString());
	}

	private MvcResult confirm(long id) throws Exception {
		return mockMvc.perform(post("/api/appointments/" + id + "/confirm").header("Authorization", "Bearer " + token))
			.andReturn();
	}

	private boolean slotListed() throws Exception {
		String res = mockMvc.perform(get("/api/availability").param("serviceId", service.getId().toString())
			.param("veterinarianId", vet.getId().toString()).param("date", start.toLocalDate().toString())
			.header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
		for (JsonNode s : json.readTree(res)) {
			if (s.get("startAt").asString().startsWith(start.toString())) {
				return true;
			}
		}
		return false;
	}

	@Test
	void ownerReservesThenConfirms() throws Exception {
		assertThat(slotListed()).isTrue();

		JsonNode reserved = reserve();

		assertThat(reserved.get("status").asString()).isEqualTo("RESERVED");
		assertThat(reserved.get("bookedBy").asString()).isEqualTo("OWNER");
		assertThat(reserved.get("reservedUntil").isNull()).isFalse();
		assertThat(slotListed()).isFalse();

		MvcResult confirmed = confirm(reserved.get("id").asLong());

		assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
		assertThat(json.readTree(confirmed.getResponse().getContentAsString()).get("status").asString())
			.isEqualTo("SCHEDULED");
	}

	@Test
	void expiredReservationIsCancelledFreesSlotAndCannotBeConfirmed() throws Exception {
		JsonNode reserved = reserve();
		long id = reserved.get("id").asLong();

		clock.set(clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime().plusMinutes(11));
		actions.expireDue();

		Appointment expired = appointments.findById(id).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(expired.getCancelReason()).isEqualTo("Reservation expired");
		assertThat(slotListed()).isTrue();

		MvcResult result = confirm(id);
		assertThat(result.getResponse().getStatus()).isEqualTo(409);
	}
}
