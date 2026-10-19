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
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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

/** Pet history over HTTP with real sign-ins: content, ordering and access scope. */
@SpringBootTest
@AutoConfigureMockMvc
class PetHistoryTests {

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

    String ownerToken;
    String receptionToken;
    String vetToken;
    Long serviceId;
    Long vetId;
    Long petA;
    Long petB;
    LocalDate monday;

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("token").asString();
    }

    @BeforeEach
    void setUp() throws Exception {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        vetId = veterinarians.findAll().get(0).getId();
        Long ownerA = ownerService.create(new CreateOwnerRequest("History A", "5552001", "secret123",
                List.of(new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG)))).id();
        Long ownerB = ownerService.create(new CreateOwnerRequest("History B", "5552002", "secret123",
                List.of(new CreatePetRequest("Bobby", Species.DOG, Breed.BULLDOG)))).id();
        petA = pets.findByOwnerIdOrderById(ownerA).get(0).getId();
        petB = pets.findByOwnerIdOrderById(ownerB).get(0).getId();
        ownerToken = login("5552001", "secret123");
        receptionToken = login("reception", "reception123");
        vetToken = login("vet.ana", "vet123");
        monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        accounts.findByUsername("5552001").ifPresent(accounts::delete);
        accounts.findByUsername("5552002").ifPresent(accounts::delete);
    }

    private Long reserve(String token, Long petId, LocalDate day, String time) throws Exception {
        String body = "{\"petId\":" + petId + ",\"serviceId\":" + serviceId + ",\"veterinarianId\":" + vetId
                + ",\"startAt\":\"" + day + "T" + time + "\"}";
        String json = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    private List<Long> historyIds(String token, Long petId) throws Exception {
        String json = mockMvc.perform(get("/api/pets/" + petId + "/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        mapper.readTree(json).get("content").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    @Test
    void receptionSeesAllOfAPetsAppointmentsLatestFirstIncludingCancelled() throws Exception {
        Long first = reserve(ownerToken, petA, monday, "10:00:00");
        Long second = reserve(ownerToken, petA, monday.plusDays(1), "10:00:00");
        mockMvc.perform(post("/api/appointments/" + first + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Changed plans\"}").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        assertThat(historyIds(receptionToken, petA)).containsExactly(second, first);
        mockMvc.perform(get("/api/pets/" + petA + "/history").header("Authorization", "Bearer " + receptionToken))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].upcoming").value(true));
    }

    @Test
    void ownerSeesOwnPetsHistory() throws Exception {
        Long id = reserve(ownerToken, petA, monday, "10:00:00");

        assertThat(historyIds(ownerToken, petA)).containsExactly(id);
    }

    @Test
    void ownerCannotSeeAnotherOwnersPetHistory() throws Exception {
        mockMvc.perform(get("/api/pets/" + petB + "/history").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPetIsNotFound() throws Exception {
        mockMvc.perform(get("/api/pets/999999/history").header("Authorization", "Bearer " + receptionToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void veterinarianIsForbidden() throws Exception {
        mockMvc.perform(get("/api/pets/" + petA + "/history").header("Authorization", "Bearer " + vetToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/pets/" + petA + "/history")).andExpect(status().isUnauthorized());
    }
}
