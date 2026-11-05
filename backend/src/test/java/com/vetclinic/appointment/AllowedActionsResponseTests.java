package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.support.TestJwt;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AllowedActionsResponseTests {

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

	Set<Long> existingIds;
	Veterinarian vet;
	Owner owner;
	Pet pet;
	ClinicService service;
	final Map<AppointmentStatus, Long> ids = new EnumMap<>(AppointmentStatus.class);

	@BeforeEach
	void setUp() {
		existingIds = appointments.findAll().stream().map(Appointment::getId).collect(Collectors.toSet());
		vet = vets.save(new Veterinarian("Dr. Actions", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Actions grooming", 60, new BigDecimal("40"))));
		owner = owners.save(new Owner("Actions", "actions-" + UUID.randomUUID()));
		pet = pets.save(new Pet("Act", Species.CAT, Breed.SIAMESE, owner));
		LocalDateTime start = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
		for (AppointmentStatus s : AppointmentStatus.values()) {
			Appointment a = appointments.save(new Appointment(pet, service, vet, start, start.plusMinutes(60), s,
					Actor.RECEPTIONIST, new BigDecimal("40"), LocalDateTime.now()));
			ids.put(s, a.getId());
		}
	}

	@AfterEach
	void tearDown() {
		appointments.deleteAll(appointments.findAll().stream().filter(a -> !existingIds.contains(a.getId())).toList());
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	private Set<String> actions(String token, AppointmentStatus status) throws Exception {
		String body = mockMvc.perform(get("/api/appointments/" + ids.get(status)).header("Authorization", "Bearer " + token))
			.andReturn().getResponse().getContentAsString();
		Set<String> result = new HashSet<>();
		for (JsonNode n : json.readTree(body).get("allowedActions")) {
			result.add(n.asString());
		}
		return result;
	}

	private void check(String token, Map<AppointmentStatus, Set<String>> expected) throws Exception {
		for (AppointmentStatus s : AppointmentStatus.values()) {
			assertThat(actions(token, s)).as(s.name()).isEqualTo(expected.getOrDefault(s, Set.of()));
		}
	}

	@Test
	void ownerSeesOnlyConfirmAndCancel() throws Exception {
		check(TestJwt.owner(owner.getId()), Map.of(
				AppointmentStatus.RESERVED, Set.of("CONFIRM", "CANCEL"),
				AppointmentStatus.SCHEDULED, Set.of("CANCEL")));
	}

	@Test
	void veterinarianSeesClinicalActions() throws Exception {
		check(TestJwt.veterinarian(vet.getId()), Map.of(
				AppointmentStatus.RESERVED, Set.of("EDIT_OBSERVATIONS"),
				AppointmentStatus.SCHEDULED,Set.of("START", "EDIT_OBSERVATIONS"),
				AppointmentStatus.IN_PROGRESS, Set.of("FINISH", "EDIT_OBSERVATIONS"),
				AppointmentStatus.FINISHED, Set.of("EDIT_OBSERVATIONS"),
				AppointmentStatus.PAID, Set.of("EDIT_OBSERVATIONS")));
	}

	@Test
	void receptionistSeesFullActionTable() throws Exception {
		check(TestJwt.receptionist(), Map.of(
				AppointmentStatus.RESERVED, Set.of("CONFIRM", "CANCEL", "EDIT_OBSERVATIONS"),
				AppointmentStatus.SCHEDULED, Set.of("START", "RESCHEDULE", "CANCEL", "EDIT_OBSERVATIONS"),
				AppointmentStatus.IN_PROGRESS, Set.of("FINISH", "RESCHEDULE", "CANCEL", "EDIT_OBSERVATIONS"),
				AppointmentStatus.FINISHED, Set.of("PAY", "CLOSE", "EDIT_OBSERVATIONS"),
				AppointmentStatus.PAID, Set.of("CLOSE", "EDIT_OBSERVATIONS")));
	}

	@Test
	void ownerCancelOnInProgressIsRejected() throws Exception {
		var res = mockMvc.perform(post("/api/appointments/" + ids.get(AppointmentStatus.IN_PROGRESS) + "/cancel")
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed mind\"}")
			.header("Authorization", "Bearer " + TestJwt.owner(owner.getId()))).andReturn().getResponse();

		assertThat(res.getStatus()).isEqualTo(403);
		assertThat(res.getContentAsString()).contains("ACTION_NOT_ALLOWED");
	}
}
