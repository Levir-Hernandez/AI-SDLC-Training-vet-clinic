package com.vetclinic.payment;

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

import com.vetclinic.appointment.Appointment;
import com.vetclinic.appointment.AppointmentRepository;
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
class PaymentFlowIntegrationTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;
	@Autowired
	AppointmentRepository appointments;
	@Autowired
	PaymentRepository payments;
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
		owner = owners.save(new Owner("Payer", "payer-" + UUID.randomUUID()));
		pet = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
		vet = vets.save(new Veterinarian("Dr. Payer", "General", new BigDecimal("20"),
				List.of(new ScheduleBlock(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
		service = services.findByCode(ServiceCode.GROOMING).orElseGet(
				() -> services.save(new ClinicService(ServiceCode.GROOMING, "Pay grooming", 60, new BigDecimal("40"))));
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
		payments.deleteAll(payments.findAll().stream()
			.filter(p -> created.stream().anyMatch(a -> a.getId().equals(p.getAppointment().getId()))).toList());
		appointments.deleteAll(created);
		pets.delete(pet);
		owners.delete(owner);
		vets.delete(vet);
	}

	private JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private MvcResult act(String path, String content) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content)
			.header("Authorization", "Bearer " + token)).andReturn();
	}

	private long finished(LocalDateTime at) throws Exception {
		MvcResult booked = act("/api/appointments", "{\"petId\":" + pet.getId() + ",\"serviceId\":" + service.getId()
				+ ",\"veterinarianId\":" + vet.getId() + ",\"startAt\":\"" + at + "\"}");
		long id = body(booked).get("id").asLong();
		assertThat(act("/api/appointments/" + id + "/start", "").getResponse().getStatus()).isEqualTo(200);
		assertThat(act("/api/appointments/" + id + "/finish", "").getResponse().getStatus()).isEqualTo(200);
		return id;
	}

	private String total(long id) throws Exception {
		return body(mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + token))
			.andReturn()).get("total").asString();
	}

	@Test
	void payThenCloseIsNotDebt() throws Exception {
		long id = finished(start);
		String total = total(id);

		assertThat(act("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total + "\"}").getResponse()
			.getStatus()).isEqualTo(201);
		MvcResult closed = act("/api/appointments/" + id + "/close", "");

		assertThat(closed.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(closed).get("status").asString()).isEqualTo("CLOSED");
		assertThat(body(closed).get("debt").asBoolean()).isFalse();
	}

	@Test
	void closeUnpaidIsListedAsDebtAndCannotBePaid() throws Exception {
		long id = finished(start.plusHours(3));
		MvcResult closed = act("/api/appointments/" + id + "/close", "");
		assertThat(closed.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(closed).get("debt").asBoolean()).isTrue();

		String list = mockMvc.perform(get("/api/appointments?debt=true&size=200")
			.header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
		boolean listed = false;
		for (JsonNode n : json.readTree(list).get("content")) {
			listed |= n.get("id").asLong() == id;
		}
		assertThat(listed).isTrue();

		MvcResult late = act("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total(id) + "\"}");
		assertThat(late.getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void wrongAmountIsMismatch() throws Exception {
		long id = finished(start.plusHours(5));

		MvcResult result = act("/api/appointments/" + id + "/payments", "{\"amount\":\"0.01\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(result).get("code").asString()).isEqualTo("AMOUNT_MISMATCH");
	}
}
