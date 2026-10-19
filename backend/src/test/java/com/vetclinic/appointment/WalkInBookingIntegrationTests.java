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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** Walk-in booking over HTTP with a real sign-in and the seeded veterinarians. */
@SpringBootTest
@AutoConfigureMockMvc
class WalkInBookingIntegrationTests {

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

    private String body(Long petId) {
        return "{\"petId\":" + petId + ",\"serviceId\":" + serviceId + ",\"veterinarianId\":" + vetId
                + ",\"startAt\":\"" + date + "T10:00:00\"}";
    }

    private int book(Long petId) throws Exception {
        return mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body(petId)))
                .andReturn().getResponse().getStatus();
    }

    private boolean slotListed() throws Exception {
        String json = mockMvc.perform(get("/api/availability").param("serviceId", String.valueOf(serviceId))
                        .param("veterinarianId", String.valueOf(vetId)).param("date", date)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.contains("\"startAt\":\"" + date + "T10:00:00\"");
    }

    @Test
    void walkInIsScheduledAndTheSlotDisappears() throws Exception {
        assertThat(slotListed()).isTrue();

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body(pet1)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.bookedBy").value("RECEPTIONIST"));

        assertThat(slotListed()).isFalse();
        assertThat(book(pet2)).isEqualTo(409);
    }

    @Test
    void twoConcurrentRequestsForTheSameSlotYieldOneCreated() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Long petId : List.of(pet1, pet2)) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return book(petId);
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(appointments.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
