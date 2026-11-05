package com.vetclinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AcceptanceFlowIntegrationTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	MockMvc mockMvc;
	@Autowired
	VeterinarianRepository vets;
	@Autowired
	ClinicServiceRepository services;

	private JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private MvcResult send(String path, String token, String content) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON);
		if (content != null) {
			req = req.content(content);
		}
		if (token != null) {
			req = req.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(req).andReturn();
	}

	private String login(String username, String password) throws Exception {
		MvcResult r = send("/api/auth/login", null,
				"{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return body(r).get("token").asString();
	}

	private String freeSlot(String token, long serviceId, long vetId) throws Exception {
		for (int d = 8; d < 22; d++) {
			String date = LocalDate.now().plusDays(d).toString();
			String res = mockMvc.perform(get("/api/availability").param("serviceId", String.valueOf(serviceId))
				.param("veterinarianId", String.valueOf(vetId)).param("date", date)
				.header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
			JsonNode slots = json.readTree(res);
			if (slots.size() > 0) {
				return slots.get(0).get("startAt").asString();
			}
		}
		throw new AssertionError("no free slot found");
	}

	@Test
	void fullLifecycleFromRegistrationToClosedPaidAppointment() throws Exception {
		assertThat(vets.findAll().size()).isGreaterThanOrEqualTo(2);
		Veterinarian ana = vets.findAll().stream().filter(v -> v.getName().contains("Ana")).findFirst().orElseThrow();
		long serviceId = services.findByCode(ServiceCode.GROOMING).orElseThrow().getId();

		String reception = login("reception", "reception123");
		String phone = "+1555" + (1000000 + Math.abs(UUID.randomUUID().hashCode()) % 8999999);
		MvcResult created = send("/api/owners", reception, "{\"name\":\"Acceptance Owner\",\"phone\":\"" + phone
				+ "\",\"password\":\"secret123\",\"pets\":[{\"name\":\"Rex\",\"species\":\"DOG\",\"breed\":\"BULLDOG\"}]}");
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		long petId = body(created).get("pets").get(0).get("id").asLong();

		String owner = login(phone, "secret123");
		String startAt = freeSlot(owner, serviceId, ana.getId());
		MvcResult reserved = send("/api/appointments", owner, "{\"petId\":" + petId + ",\"serviceId\":" + serviceId
				+ ",\"veterinarianId\":" + ana.getId() + ",\"startAt\":\"" + startAt + "\"}");
		assertThat(reserved.getResponse().getStatus()).isEqualTo(201);
		assertThat(body(reserved).get("status").asString()).isEqualTo("RESERVED");
		long id = body(reserved).get("id").asLong();

		MvcResult confirmed = send("/api/appointments/" + id + "/confirm", owner, null);
		assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(confirmed).get("status").asString()).isEqualTo("SCHEDULED");

		String vet = login("vet.ana", "vet123");
		assertThat(send("/api/appointments/" + id + "/start", vet, null).getResponse().getStatus()).isEqualTo(200);
		assertThat(send("/api/appointments/" + id + "/finish", vet, null).getResponse().getStatus()).isEqualTo(200);

		String total = body(mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + reception))
			.andReturn()).get("total").asString();
		String pay = "{\"amount\":\"" + total + "\"}";
		assertThat(send("/api/appointments/" + id + "/payments", reception, pay).getResponse().getStatus())
			.isEqualTo(201);

		MvcResult second = send("/api/appointments/" + id + "/payments", reception, pay);
		assertThat(second.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(second).get("code").asString()).isEqualTo("ALREADY_PAID");

		MvcResult closed = send("/api/appointments/" + id + "/close", reception, null);
		assertThat(closed.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(closed).get("status").asString()).isEqualTo("CLOSED");
		assertThat(body(closed).get("debt").asBoolean()).isFalse();
	}

	@Test
	void anotherOwnerCannotReadTheAppointment() throws Exception {
		Veterinarian ana = vets.findAll().stream().filter(v -> v.getName().contains("Ana")).findFirst().orElseThrow();
		long serviceId = services.findByCode(ServiceCode.GROOMING).orElseThrow().getId();
		String reception = login("reception", "reception123");
		String[] phones = new String[2];
		long petId = 0;
		for (int i = 0; i < 2; i++) {
			phones[i] = "+1556" + (1000000 + Math.abs(UUID.randomUUID().hashCode()) % 8999999);
			MvcResult c = send("/api/owners", reception, "{\"name\":\"Owner " + i + "\",\"phone\":\"" + phones[i]
					+ "\",\"password\":\"secret123\",\"pets\":[{\"name\":\"Tom\",\"species\":\"CAT\",\"breed\":\"SIAMESE\"}]}");
			assertThat(c.getResponse().getStatus()).isEqualTo(201);
			if (i == 0) {
				petId = body(c).get("pets").get(0).get("id").asLong();
			}
		}
		String first = login(phones[0], "secret123");
		String other = login(phones[1], "secret123");
		String startAt = freeSlot(first, serviceId, ana.getId());
		MvcResult reserved = send("/api/appointments", first, "{\"petId\":" + petId + ",\"serviceId\":" + serviceId
				+ ",\"veterinarianId\":" + ana.getId() + ",\"startAt\":\"" + startAt + "\"}");
		assertThat(reserved.getResponse().getStatus()).isEqualTo(201);
		long id = body(reserved).get("id").asLong();

		int status = mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + other))
			.andReturn().getResponse().getStatus();

		assertThat(status).isEqualTo(404);
	}
}
