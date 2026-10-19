package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.OwnerService;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.owner.dto.CreateOwnerRequest;
import com.vetclinic.owner.dto.CreatePetRequest;
import com.vetclinic.seed.DemoDataSeeder;
import com.vetclinic.support.TestJwt;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** The allowedActions the API reports per role and status match the lifecycle table (no mocks). */
@SpringBootTest
@AutoConfigureMockMvc
class AllowedActionsResponseTests {

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

    final Map<AppointmentStatus, Long> ids = new EnumMap<>(AppointmentStatus.class);
    String owner;
    String vet;
    String reception;

    @BeforeEach
    void setUp() {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        ClinicService service = services.findAll().get(0);
        Veterinarian veterinarian = veterinarians.findAll().get(0);
        Long ownerId = ownerService.create(new CreateOwnerRequest("Owner A", "5559001", "secret123",
                List.of(new CreatePetRequest("Rex", Species.DOG, Breed.BULLDOG)))).id();
        Pet pet = pets.findByOwnerIdOrderById(ownerId).get(0);
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        int i = 0;
        for (AppointmentStatus s : AppointmentStatus.values()) {
            LocalDateTime reservedUntil = s == AppointmentStatus.RESERVED ? LocalDateTime.now().plusMinutes(10) : null;
            Appointment a = appointments.save(new Appointment(pet, service, veterinarian, start.plusHours(i++), s,
                    Actor.RECEPTIONIST, reservedUntil, null, LocalDateTime.now()));
            ids.put(s, a.getId());
        }
        Instant exp = Instant.now().plusSeconds(3600);
        owner = TestJwt.token(TestJwt.TEST_SECRET, 1L, Role.OWNER, ownerId, null, exp);
        vet = TestJwt.token(TestJwt.TEST_SECRET, 2L, Role.VETERINARIAN, null, veterinarian.getId(), exp);
        reception = TestJwt.receptionist();
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        accounts.findByUsername("5559001").ifPresent(accounts::delete);
    }

    private List<String> actions(String token, AppointmentStatus s) throws Exception {
        String json = mockMvc.perform(get("/api/appointments/" + ids.get(s)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> result = new ArrayList<>();
        mapper.readTree(json).get("allowedActions").forEach(n -> result.add(n.asString()));
        return result;
    }

    @Test
    void ownerActionsPerStatus() throws Exception {
        assertThat(actions(owner, AppointmentStatus.RESERVED)).containsExactlyInAnyOrder("CONFIRM", "CANCEL");
        assertThat(actions(owner, AppointmentStatus.SCHEDULED)).containsExactly("CANCEL");
        assertThat(actions(owner, AppointmentStatus.IN_PROGRESS)).isEmpty();
        assertThat(actions(owner, AppointmentStatus.FINISHED)).isEmpty();
        assertThat(actions(owner, AppointmentStatus.PAID)).isEmpty();
    }

    @Test
    void veterinarianActionsPerStatus() throws Exception {
        assertThat(actions(vet, AppointmentStatus.RESERVED)).containsExactly("EDIT_OBSERVATIONS");
        assertThat(actions(vet, AppointmentStatus.SCHEDULED)).containsExactlyInAnyOrder("START", "EDIT_OBSERVATIONS");
        assertThat(actions(vet, AppointmentStatus.IN_PROGRESS)).containsExactlyInAnyOrder("FINISH", "EDIT_OBSERVATIONS");
        assertThat(actions(vet, AppointmentStatus.FINISHED)).containsExactly("EDIT_OBSERVATIONS");
        assertThat(actions(vet, AppointmentStatus.PAID)).containsExactly("EDIT_OBSERVATIONS");
    }

    @Test
    void receptionistActionsPerStatus() throws Exception {
        assertThat(actions(reception, AppointmentStatus.RESERVED))
                .containsExactlyInAnyOrder("CONFIRM", "CANCEL", "EDIT_OBSERVATIONS");
        assertThat(actions(reception, AppointmentStatus.SCHEDULED))
                .containsExactlyInAnyOrder("START", "RESCHEDULE", "CANCEL", "EDIT_OBSERVATIONS");
        assertThat(actions(reception, AppointmentStatus.IN_PROGRESS))
                .containsExactlyInAnyOrder("FINISH", "RESCHEDULE", "CANCEL", "EDIT_OBSERVATIONS");
        assertThat(actions(reception, AppointmentStatus.FINISHED))
                .containsExactlyInAnyOrder("PAY", "CLOSE", "EDIT_OBSERVATIONS");
        assertThat(actions(reception, AppointmentStatus.PAID)).containsExactlyInAnyOrder("CLOSE", "EDIT_OBSERVATIONS");
    }

    @Test
    void finalStatusesOfferNoActionsToAnyRole() throws Exception {
        for (AppointmentStatus s : List.of(AppointmentStatus.CLOSED, AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED)) {
            for (String token : List.of(owner, vet, reception)) {
                assertThat(actions(token, s)).as(s.name()).isEmpty();
            }
        }
    }

    @Test
    void ownerNeverGetsStaffActions() throws Exception {
        for (AppointmentStatus s : AppointmentStatus.values()) {
            assertThat(actions(owner, s)).as(s.name())
                    .doesNotContain("START", "FINISH", "PAY", "CLOSE", "RESCHEDULE", "EDIT_OBSERVATIONS");
        }
    }
}
