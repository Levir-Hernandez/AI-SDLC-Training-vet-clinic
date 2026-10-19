package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.seed.DemoDataSeeder;
import com.vetclinic.veterinarian.Veterinarian;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Reschedule over HTTP with a real receptionist sign-in. */
@SpringBootTest
@AutoConfigureMockMvc
class RescheduleIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper mapper;
    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired ClinicServiceRepository services;
    @Autowired VeterinarianService veterinarianService;
    @Autowired VeterinarianRepository veterinarians;
    @Autowired AppointmentRepository appointments;
    @Autowired OwnerRepository owners;
    @Autowired PetRepository pets;

    String token;
    Long serviceId;
    Long vetId;
    Long pet1;
    Long pet2;
    String date;

    @BeforeEach
    void setUp() throws Exception {
        // Seeding is disabled in tests; run it explicitly (idempotent).
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reception\",\"password\":\"reception123\"}")).andReturn();
        token = mapper.readTree(login.getResponse().getContentAsString()).get("token").asString();
        serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        vetId = veterinarians.findAll().stream().filter(v -> v.getName().contains("Ana"))
                .map(Veterinarian::getId).findFirst().orElseGet(() -> veterinarians.findAll().get(0).getId());
        Owner owner = owners.save(new Owner("Walk-in Owner", "555-7777"));
        pet1 = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner)).getId();
        pet2 = pets.save(new Pet("Max", Species.DOG, Breed.BULLDOG, owner)).getId();
        date = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
    }

    private String json(Long petId, String time) {
        return "{\"petId\":" + petId + ",\"serviceId\":" + serviceId + ",\"veterinarianId\":" + vetId
                + ",\"startAt\":\"" + date + "T" + time + "\"}";
    }

    private long book(Long petId, String time) throws Exception {
        String res = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(json(petId, time)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asLong();
    }

    private boolean slotListed(String time) throws Exception {
        String res = mockMvc.perform(get("/api/availability").param("serviceId", String.valueOf(serviceId))
                        .param("veterinarianId", String.valueOf(vetId)).param("date", date)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return res.contains("\"startAt\":\"" + date + "T" + time + "\"");
    }

    private org.springframework.test.web.servlet.ResultActions reschedule(long id, String time) throws Exception {
        return mockMvc.perform(post("/api/appointments/" + id + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token)
                .content("{\"veterinarianId\":" + vetId + ",\"startAt\":\"" + date + "T" + time + "\"}"));
    }

    @Test
    void rescheduleLinksNewAppointmentAndFreesOldSlot() throws Exception {
        long original = book(pet1, "10:00:00");
        assertThat(slotListed("10:00:00")).isFalse();

        String res = reschedule(original, "11:00:00")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.rescheduledFromId").value(original))
                .andReturn().getResponse().getContentAsString();
        long created = mapper.readTree(res).get("id").asLong();

        mockMvc.perform(get("/api/appointments/" + original).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("RESCHEDULED"))
                .andExpect(jsonPath("$.rescheduledToId").value(created));
        assertThat(slotListed("10:00:00")).isTrue();
        assertThat(slotListed("11:00:00")).isFalse();
    }

    @Test
    void actionsOnTheRescheduledOriginalAreRejected() throws Exception {
        long original = book(pet1, "10:00:00");
        reschedule(original, "11:00:00").andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments/" + original + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void rescheduleIntoATakenSlotLeavesTheOriginalScheduled() throws Exception {
        long original = book(pet1, "10:00:00");
        book(pet2, "11:00:00");

        reschedule(original, "11:00:00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));

        mockMvc.perform(get("/api/appointments/" + original).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.rescheduledToId").doesNotExist());
        assertThat(appointments.count()).isEqualTo(2);
    }
}
