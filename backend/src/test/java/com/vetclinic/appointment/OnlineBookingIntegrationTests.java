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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** Owner reservation, confirmation and expiry over HTTP with a real owner sign-in and a movable clock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OnlineBookingIntegrationTests.ClockOverride.class)
class OnlineBookingIntegrationTests {

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(LocalDateTime.now().withSecond(0).withNano(0));
        }
    }

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
    @Autowired AppointmentActionService actions;
    @Autowired Clock clock;

    String token;
    Long serviceId;
    Long vetId;
    Long petId;
    String date;

    @BeforeEach
    void setUp() throws Exception {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        vetId = veterinarians.findAll().stream().filter(v -> v.getName().contains("Ana"))
                .map(Veterinarian::getId).findFirst().orElseGet(() -> veterinarians.findAll().get(0).getId());
        ownerService.create(new CreateOwnerRequest("Online Owner", "5550001", "secret123",
                List.of(new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG))));
        petId = pets.findAll().get(0).getId();
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"5550001\",\"password\":\"secret123\"}")).andReturn();
        token = mapper.readTree(login.getResponse().getContentAsString()).get("token").asString();
        date = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        accounts.findByUsername("5550001").ifPresent(accounts::delete);
    }

    private String body() {
        return "{\"petId\":" + petId + ",\"serviceId\":" + serviceId + ",\"veterinarianId\":" + vetId
                + ",\"startAt\":\"" + date + "T10:00:00\"}";
    }

    private int reserve() throws Exception {
        return mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body()))
                .andReturn().getResponse().getStatus();
    }

    private boolean slotListed() throws Exception {
        String json = mockMvc.perform(get("/api/availability").param("serviceId", String.valueOf(serviceId))
                        .param("veterinarianId", String.valueOf(vetId)).param("date", date)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.contains("\"startAt\":\"" + date + "T10:00:00\"");
    }

    private Long reserveAndGetId() throws Exception {
        String json = mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.bookedBy").value("OWNER"))
                .andExpect(jsonPath("$.reservedUntil").exists())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    @Test
    void ownerReservesThenConfirms() throws Exception {
        assertThat(slotListed()).isTrue();
        Long id = reserveAndGetId();
        assertThat(slotListed()).isFalse();

        mockMvc.perform(post("/api/appointments/" + id + "/confirm").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        assertThat(slotListed()).isFalse();

        mockMvc.perform(post("/api/appointments/" + id + "/confirm").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void unconfirmedReservationExpiresAndFreesTheSlot() throws Exception {
        Long id = reserveAndGetId();
        assertThat(reserve()).isEqualTo(409);

        ((MutableClock) clock).advance(Duration.ofMinutes(11));
        // Confirming a lapsed hold is rejected even before the sweeper runs.
        mockMvc.perform(post("/api/appointments/" + id + "/confirm").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));

        assertThat(actions.expireDue()).isEqualTo(1);
        Appointment expired = appointments.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(expired.getCancelReason()).isEqualTo("Reservation expired");
        assertThat(slotListed()).isTrue();
        mockMvc.perform(post("/api/appointments/" + id + "/confirm").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
