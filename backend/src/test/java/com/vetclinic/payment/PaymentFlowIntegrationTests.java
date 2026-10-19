package com.vetclinic.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.appointment.AppointmentRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Pay, close and debt flow over HTTP with a real receptionist sign-in. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIntegrationTests {

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

    String token;
    Long serviceId;
    Long vetId;
    Long pet1;
    Long pet2;
    String date;

    @BeforeEach
    void setUp() throws Exception {
        new DemoDataSeeder(accounts, accountService, services, veterinarianService).run();
        MvcResult login = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reception\",\"password\":\"reception123\"}")).andReturn();
        token = mapper.readTree(login.getResponse().getContentAsString()).get("token").asString();
        serviceId = services.findAll().stream().filter(s -> s.getName().equals("General consultation"))
                .map(ClinicService::getId).findFirst().orElseThrow();
        vetId = veterinarians.findAll().stream().filter(v -> v.getName().contains("Ana"))
                .map(Veterinarian::getId).findFirst().orElseGet(() -> veterinarians.findAll().get(0).getId());
        Owner owner = owners.save(new Owner("Pay Owner", "555-8888"));
        pet1 = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner)).getId();
        pet2 = pets.save(new Pet("Max", Species.DOG, Breed.BULLDOG, owner)).getId();
        date = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
    }

    @AfterEach
    void tearDown() {
        payments.deleteAll();
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
    }

    private ResultActions call(String path, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", "Bearer " + token);
        if (body != null) request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    /** Books a walk-in, starts and finishes it; returns its id and total. */
    private long finished(Long petId, String time) throws Exception {
        String res = call("/api/appointments", "{\"petId\":" + petId + ",\"serviceId\":" + serviceId
                + ",\"veterinarianId\":" + vetId + ",\"startAt\":\"" + date + "T" + time + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(res).get("id").asLong();
        call("/api/appointments/" + id + "/start", null).andExpect(status().isOk());
        call("/api/appointments/" + id + "/finish", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
        return id;
    }

    private String total(long id) throws Exception {
        String res = mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("total").asString();
    }

    @Test
    void payThenCloseIsNotADebt() throws Exception {
        long id = finished(pet1, "10:00:00");
        String total = total(id);

        call("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(id));
        mockMvc.perform(get("/api/appointments/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PAID"));

        call("/api/appointments/" + id + "/close", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.debt").value(false));
    }

    @Test
    void inexactAndRepeatedPaymentsAreRejected() throws Exception {
        long id = finished(pet1, "10:00:00");
        java.math.BigDecimal total = new java.math.BigDecimal(total(id));

        call("/api/appointments/" + id + "/payments",
                "{\"amount\":\"" + total.subtract(new java.math.BigDecimal("0.01")) + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        call("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total.add(new java.math.BigDecimal("0.01")) + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"));
        call("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total + "\"}")
                .andExpect(status().isCreated());
        call("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    @Test
    void closingUnpaidFinishedAppointmentCreatesADebtAndBlocksPayment() throws Exception {
        long id = finished(pet2, "11:00:00");

        call("/api/appointments/" + id + "/close", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.debt").value(true));

        String list = mockMvc.perform(get("/api/appointments?debt=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("\"id\":" + id);

        call("/api/appointments/" + id + "/payments", "{\"amount\":\"" + total(id) + "\"}")
                .andExpect(status().isConflict());
        call("/api/appointments/" + id + "/close", null).andExpect(status().isConflict());
    }

    @Test
    void paymentOnScheduledAppointmentIsInvalidTransition() throws Exception {
        String res = call("/api/appointments", "{\"petId\":" + pet1 + ",\"serviceId\":" + serviceId
                + ",\"veterinarianId\":" + vetId + ",\"startAt\":\"" + date + "T12:00:00\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(res).get("id").asLong();

        call("/api/appointments/" + id + "/payments", "{\"amount\":\"60.00\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }
}
