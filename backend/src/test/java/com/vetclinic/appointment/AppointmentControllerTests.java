package com.vetclinic.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.config.ClockConfig;
import com.vetclinic.config.SecurityConfig;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.ConflictException;
import com.vetclinic.shared.error.GlobalExceptionHandler;
import com.vetclinic.auth.Role;
import com.vetclinic.support.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.vetclinic.appointment.dto.AppointmentFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@Import({SecurityConfig.class, ClockConfig.class, GlobalExceptionHandler.class})
class AppointmentControllerTests {

    static final String BODY =
            "{\"petId\":1,\"serviceId\":1,\"veterinarianId\":1,\"startAt\":\"2030-01-07T10:00:00\"}";

    @Autowired MockMvc mockMvc;
    @MockitoBean BookingService booking;
    @MockitoBean AppointmentQueryService queries;
    @MockitoBean AppointmentActionService actions;

    private static AppointmentResponse response() {
        return new AppointmentResponse(5L,
                new AppointmentResponse.PetSummary(1L, "Rex", Species.DOG, Breed.BULLDOG), "Maria",
                new AppointmentResponse.ServiceSummary(1L, "General consultation", 30),
                new AppointmentResponse.VeterinarianSummary(1L, "Ana"),
                LocalDateTime.of(2030, 1, 7, 10, 0), LocalDateTime.of(2030, 1, 7, 10, 30),
                AppointmentStatus.SCHEDULED, Actor.RECEPTIONIST, new BigDecimal("60.00"), null, null, null, null,
                null, false, true, List.of(AppointmentAction.START, AppointmentAction.CANCEL));
    }

    @Test
    void receptionistBooksWalkIn() throws Exception {
        when(booking.book(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/appointments/5"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.bookedBy").value("RECEPTIONIST"));
    }

    @Test
    void listIsPaginated() throws Exception {
        when(queries.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1));

        mockMvc.perform(get("/api/appointments").header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailIncludesAllowedActions() throws Exception {
        when(queries.get(any(), any())).thenReturn(response());

        mockMvc.perform(get("/api/appointments/5").header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions[0]").value("START"));
    }

    @Test
    void missingPetIdReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":1,\"veterinarianId\":1,\"startAt\":\"2030-01-07T10:00:00\"}")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void slotTakenReturns409() throws Exception {
        when(booking.book(any(), any())).thenThrow(new ConflictException("SLOT_TAKEN", "taken"));

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    @Test
    void ownerConfirmsReservation() throws Exception {
        when(actions.confirm(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/appointments/5/confirm")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void veterinarianCannotConfirm() throws Exception {
        mockMvc.perform(post("/api/appointments/5/confirm")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredReservationReturns409() throws Exception {
        when(actions.confirm(any(), any())).thenThrow(new ConflictException("RESERVATION_EXPIRED", "expired"));

        mockMvc.perform(post("/api/appointments/5/confirm")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void ownerCancelsWithReason() throws Exception {
        when(actions.cancel(any(), any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/appointments/5/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Pet is ill\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isOk());
    }

    @Test
    void cancelWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments/5/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void veterinarianCannotCancel() throws Exception {
        mockMvc.perform(post("/api/appointments/5/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarianStartsAndFinishes() throws Exception {
        when(actions.start(any(), any())).thenReturn(response());
        when(actions.finish(any(), any())).thenReturn(response());
        String auth = "Bearer " + TestJwt.forRole(Role.VETERINARIAN);

        mockMvc.perform(post("/api/appointments/5/start").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/5/finish").header("Authorization", auth))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotStart() throws Exception {
        mockMvc.perform(post("/api/appointments/5/start")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarianUpdatesObservations() throws Exception {
        when(actions.updateObservations(any(), any(), any())).thenReturn(response());

        mockMvc.perform(patch("/api/appointments/5/observations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observations\":\"Healthy\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isOk());
    }

    @Test
    void tooLongObservationsReturn400() throws Exception {
        mockMvc.perform(patch("/api/appointments/5/observations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observations\":\"" + "a".repeat(2001) + "\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lockedObservationsReturn409() throws Exception {
        when(actions.updateObservations(any(), any(), any()))
                .thenThrow(new ConflictException("OBSERVATIONS_LOCKED", "locked"));

        mockMvc.perform(patch("/api/appointments/5/observations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observations\":\"x\"}")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OBSERVATIONS_LOCKED"));
    }

    @Test
    void statusFilterReachesService() throws Exception {
        when(queries.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/appointments?status=SCHEDULED,IN_PROGRESS")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.VETERINARIAN)))
                .andExpect(status().isOk());

        ArgumentCaptor<AppointmentFilter> captor = ArgumentCaptor.forClass(AppointmentFilter.class);
        verify(queries).list(any(), captor.capture(), any());
        assertEquals(List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.IN_PROGRESS),
                captor.getValue().statuses());
    }

    @Test
    void ongoingBoardParamsReachService() throws Exception {
        when(queries.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/appointments?ongoing=true&date=2026-03-03&veterinarianId=7")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isOk());

        ArgumentCaptor<AppointmentFilter> captor = ArgumentCaptor.forClass(AppointmentFilter.class);
        verify(queries).list(any(), captor.capture(), any());
        assertEquals(Boolean.TRUE, captor.getValue().ongoing());
        assertEquals(java.time.LocalDate.of(2026, 3, 3), captor.getValue().date());
        assertEquals(7L, captor.getValue().veterinarianId());
    }

    @Test
    void receptionistCancelWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments/5/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/appointments?status=FOO")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receptionistReschedules() throws Exception {
        when(actions.reschedule(any(), any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/appointments/4/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"veterinarianId\":2,\"startAt\":\"2030-01-08T10:00:00\"}")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/appointments/5"));
    }

    @Test
    void rescheduleWithoutStartAtReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments/4/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"veterinarianId\":2}")
                        .header("Authorization", "Bearer " + TestJwt.receptionist()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerCannotReschedule() throws Exception {
        mockMvc.perform(post("/api/appointments/4/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"2030-01-08T10:00:00\"}")
                        .header("Authorization", "Bearer " + TestJwt.forRole(Role.OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }
}
