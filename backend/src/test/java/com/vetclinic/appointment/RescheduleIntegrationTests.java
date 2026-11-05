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
class RescheduleIntegrationTests {

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
		owner = owners.save(new Owner("Resched", "resched-" + UUID.randomUUID()));
		pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
		vet = vets.save(new Veterinarian("Dr. Resched", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Resched grooming", 60, new BigDecimal("40"))));
		start = LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)).atTime(10, 0);
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"reception\",\"password\":\"reception123\"}"))
			.andReturn().getResponse().getContentAsString();
		token = json.readTree(body).get("token").asString();
	}

	@AfterEach
	void tearDown() {
		List<Appointment> created = appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId()))
			.toList();
		created.forEach(a -> {
			a.setRescheduledFrom(null);
			appointments.save(a);
		});
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	private MvcResult book(LocalDateTime at) throws Exception {
		return mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId() + ",\"veterinarianId\":"
					+ vet.getId() + ",\"startAt\":\"" + at + "\"}")
			.header("Authorization", "Bearer " + token)).andReturn();
	}

	private MvcResult reschedule(long id, LocalDateTime at) throws Exception {
		return mockMvc.perform(post("/api/appointments/" + id + "/reschedule").contentType(MediaType.APPLICATION_JSON)
			.content("{\"veterinarianId\":" + vet.getId() + ",\"startAt\":\"" + at + "\"}")
			.header("Authorization", "Bearer " + token)).andReturn();
	}

	private JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode detail(long id) throws Exception {
		return body(mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + token))
			.andReturn());
	}

	private boolean slotListed(LocalDateTime at) throws Exception {
		String res = mockMvc.perform(get("/api/availability").param("serviceId", service.getId().toString())
			.param("veterinarianId", vet.getId().toString()).param("date", at.toLocalDate().toString())
			.header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
		for (JsonNode s : json.readTree(res)) {
			if (s.get("startAt").asString().startsWith(at.toString())) {
				return true;
			}
		}
		return false;
	}

	@Test
	void rescheduleLinksAppointmentsAndFreesOldSlot() throws Exception {
		long id = body(book(start)).get("id").asLong();
		LocalDateTime newStart = start.plusHours(3);

		MvcResult result = reschedule(id, newStart);

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		JsonNode created = body(result);
		assertThat(created.get("status").asString()).isEqualTo("SCHEDULED");
		assertThat(created.get("rescheduledFromId").asLong()).isEqualTo(id);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/appointments/" + created.get("id").asLong());
		JsonNode original = detail(id);
		assertThat(original.get("status").asString()).isEqualTo("RESCHEDULED");
		assertThat(original.get("rescheduledToId").asLong()).isEqualTo(created.get("id").asLong());
		assertThat(slotListed(start)).isTrue();
		assertThat(slotListed(newStart)).isFalse();
	}

	@Test
	void startOnRescheduledOriginalIsConflict() throws Exception {
		long id = body(book(start)).get("id").asLong();
		reschedule(id, start.plusHours(3));

		MvcResult result = mockMvc.perform(post("/api/appointments/" + id + "/start")
			.header("Authorization", "Bearer " + token)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void rescheduleIntoTakenSlotIsConflictAndOriginalStaysScheduled() throws Exception {
		long id = body(book(start)).get("id").asLong();
		LocalDateTime other = start.plusHours(3);
		assertThat(book(other).getResponse().getStatus()).isEqualTo(201);

		MvcResult result = reschedule(id, other);

		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(result).get("code").asString()).isEqualTo("SLOT_TAKEN");
		assertThat(detail(id).get("status").asString()).isEqualTo("SCHEDULED");
	}
}
