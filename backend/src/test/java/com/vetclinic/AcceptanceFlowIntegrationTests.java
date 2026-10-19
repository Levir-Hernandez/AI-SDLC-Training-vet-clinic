package com.vetclinic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.appointment.AppointmentRepository;
import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.seed.DemoDataSeeder;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end happy path across all three roles with real sign-ins on the seeded system. */
@SpringBootTest
@AutoConfigureMockMvc
class AcceptanceFlowIntegrationTests {

    private static final String OWNER_PHONE = "5559900";
    private static final String OWNER2_PHONE = "5559901";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper mapper;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired ClinicServiceRepository services;
    @Autowired VeterinarianService veterinarianService;
    @Autowired VeterinarianRepository veterinarians;
    @Autowired AppointmentRepository appointments;
    @Autowired PaymentRepository payments;
    @Autowired OwnerRepository owners;
    @Autowired PetRepository pets;

    String date;

    @BeforeEach
    void setUp() {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        date = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
    }

    @AfterEach
    void tearDown() {
        payments.deleteAll();
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        accounts.findByUsername(OWNER_PHONE).ifPresent(accounts::delete);
        accounts.findByUsername(OWNER2_PHONE).ifPresent(accounts::delete);
    }

    private String login(String username, String password) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asString();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request = request.header("Authorization", "Bearer " + token);
        if (body != null) request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private long registerOwner(String reception, String phone) throws Exception {
        return json(send(post("/api/owners"), reception, "{\"name\":\"Acceptance Owner\",\"phone\":\"" + phone
                + "\",\"password\":\"secret123\",\"pets\":[{\"name\":\"Rex\",\"species\":\"DOG\",\"breed\":\"BULLDOG\"}]}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long firstPetId(String ownerToken) throws Exception {
        return json(send(get("/api/pets"), ownerToken, null).andExpect(status().isOk()))
                .get("content").get(0).get("id").asLong();
    }

    @Test
    void fullFlowEndsClosedWithSinglePayment() throws Exception {
        String reception = login("reception", "reception123");
        String vet = login("vet.ana", "vet123");

        registerOwner(reception, OWNER_PHONE);
        String owner = login(OWNER_PHONE, "secret123");
        long petId = firstPetId(owner);

        long serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        long vetId = veterinarians.findAll().stream()
                .filter(v -> v.getName().contains("Ana")).findFirst().orElseThrow().getId();

        long id = json(send(post("/api/appointments"), owner, "{\"petId\":" + petId + ",\"serviceId\":" + serviceId
                + ",\"veterinarianId\":" + vetId + ",\"startAt\":\"" + date + "T10:00:00\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RESERVED"))).get("id").asLong();
        send(post("/api/appointments/" + id + "/confirm"), owner, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));

        send(post("/api/appointments/" + id + "/start"), vet, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        send(patch("/api/appointments/" + id + "/observations"), vet, "{\"observations\":\"All good\"}")
                .andExpect(status().isOk());
        send(post("/api/appointments/" + id + "/finish"), vet, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));

        String total = json(send(get("/api/appointments/" + id), reception, null).andExpect(status().isOk()))
                .get("total").asString();
        send(post("/api/appointments/" + id + "/payments"), reception, "{\"amount\":\"" + total + "\"}")
                .andExpect(status().isCreated());
        send(post("/api/appointments/" + id + "/close"), reception, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.debt").value(false));

        send(post("/api/appointments/" + id + "/payments"), reception, "{\"amount\":\"" + total + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void ownerCannotReadAnotherOwnersAppointment() throws Exception {
        String reception = login("reception", "reception123");
        registerOwner(reception, OWNER_PHONE);
        registerOwner(reception, OWNER2_PHONE);
        String owner = login(OWNER_PHONE, "secret123");
        String other = login(OWNER2_PHONE, "secret123");

        long serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        long vetId = veterinarians.findAll().get(0).getId();
        long id = json(send(post("/api/appointments"), owner, "{\"petId\":" + firstPetId(owner) + ",\"serviceId\":"
                + serviceId + ",\"veterinarianId\":" + vetId + ",\"startAt\":\"" + date + "T11:00:00\"}")
                .andExpect(status().isCreated())).get("id").asLong();

        send(get("/api/appointments/" + id), other, null).andExpect(status().isNotFound());
    }
}
