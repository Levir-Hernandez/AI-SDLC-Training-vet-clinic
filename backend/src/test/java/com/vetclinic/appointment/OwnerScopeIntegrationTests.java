package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.OwnerService;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.seed.DemoDataSeeder;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Two owners with real sign-ins: each sees and acts only on their own appointments. */
@SpringBootTest
@AutoConfigureMockMvc
class OwnerScopeIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper mapper;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired ClinicServiceRepository services;
    @Autowired VeterinarianService veterinarianService;
    @Autowired VeterinarianRepository veterinarians;
    @Autowired AppointmentRepository appointments;
    @Autowired OwnerRepository owners;
    @Autowired OwnerService ownerService;
    @Autowired PetRepository pets;

    String tokenA;
    String tokenB;
    Long serviceId;
    Long vetId;
    Long ownerBId;
    List<Long> petsA;
    Long petB;
    LocalDate monday;

    private String login(String phone) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + phone + "\",\"password\":\"secret123\"}")).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("token").asString();
    }

    @BeforeEach
    void setUp() throws Exception {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        vetId = veterinarians.findAll().get(0).getId();
        Long ownerAId = ownerService.create(new CreateOwnerRequest("Owner A", "5551001", "secret123",
                List.of(new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG),
                        new CreatePetRequest("Tom", Species.CAT, Breed.SIAMESE)))).id();
        ownerBId = ownerService.create(new CreateOwnerRequest("Owner B", "5551002", "secret123",
                List.of(new CreatePetRequest("Bobby", Species.DOG, Breed.BULLDOG)))).id();
        petsA = pets.findByOwnerIdOrderById(ownerAId).stream().map(p -> p.getId()).toList();
        petB = pets.findByOwnerIdOrderById(ownerBId).get(0).getId();
        tokenA = login("5551001");
        tokenB = login("5551002");
        monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        accounts.findByUsername("5551001").ifPresent(accounts::delete);
        accounts.findByUsername("5551002").ifPresent(accounts::delete);
    }

    private Long reserve(String token, Long petId, LocalDate day, String time) throws Exception {
        String body = "{\"petId\":" + petId + ",\"serviceId\":" + serviceId + ",\"veterinarianId\":" + vetId
                + ",\"startAt\":\"" + day + "T" + time + "\"}";
        String json = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    @Test
    void ownerListsOnlyOwnAppointmentsAcrossPetsAndDays() throws Exception {
        Long a1 = reserve(tokenA, petsA.get(0), monday, "10:00:00");
        Long a2 = reserve(tokenA, petsA.get(1), monday.plusDays(1), "10:00:00");
        Long b1 = reserve(tokenB, petB, monday, "11:00:00");

        String json = mockMvc.perform(get("/api/appointments").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].service.durationMinutes").value(30))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Bobby");
        List<Long> ids = new java.util.ArrayList<>();
        mapper.readTree(json).get("content").forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).containsExactlyInAnyOrder(a1, a2).doesNotContain(b1);
    }

    @Test
    void ownerCannotReadOrCancelAnotherOwnersAppointment() throws Exception {
        Long b1 = reserve(tokenB, petB, monday, "11:00:00");

        mockMvc.perform(get("/api/appointments/" + b1).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/appointments/" + b1 + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"nope\"}").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        assertThat(appointments.findById(b1).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.RESERVED);
    }

    @Test
    void ownerCancelsOwnAppointmentOnlyWithReason() throws Exception {
        Long a1 = reserve(tokenA, petsA.get(0), monday, "10:00:00");

        mockMvc.perform(post("/api/appointments/" + a1 + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/appointments/" + a1 + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Pet is ill\"}").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/appointments/" + a1 + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void petListIgnoresForeignOwnerId() throws Exception {
        String json = mockMvc.perform(get("/api/pets").param("ownerId", String.valueOf(ownerBId))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Bobby");
    }
}
