package com.vetclinic.appointment;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Shared builders for appointment-related unit tests. 2026-03-02 is a Monday. */
final class Fixtures {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 8, 0);
    static final LocalDateTime T10 = LocalDateTime.of(2026, 3, 2, 10, 0);

    static final CurrentUser OWNER_A = new CurrentUser(1L, Role.OWNER, 10L, null);
    static final CurrentUser OWNER_B = new CurrentUser(2L, Role.OWNER, 20L, null);
    static final CurrentUser RECEPTIONIST = new CurrentUser(3L, Role.RECEPTIONIST, null, null);
    static final CurrentUser VET_ANA = new CurrentUser(4L, Role.VETERINARIAN, null, 100L);
    static final CurrentUser VET_LUIS = new CurrentUser(5L, Role.VETERINARIAN, null, 200L);

    private Fixtures() {
    }

    static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static Owner owner(Long id) {
        return withId(new Owner("Owner " + id, "555-" + id), id);
    }

    static Pet pet(Long id, Owner owner) {
        return withId(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner), id);
    }

    /** General consultation: 30 minutes, 40.00. */
    static ClinicService consultation() {
        return withId(new ClinicService(ServiceCode.GENERAL_CONSULTATION, "General consultation", 30,
                new BigDecimal("40.00")), 1L);
    }

    static ClinicService service(Long id, ServiceCode code, int minutes, String price) {
        return withId(new ClinicService(code, code.name(), minutes, new BigDecimal(price)), id);
    }

    /** Monday-Friday 09:00-13:00 and 14:00-18:00, cost 20.00. */
    static Veterinarian vet(Long id, String name, String cost) {
        List<ScheduleBlock> blocks = new java.util.ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.getValue() <= 5) {
                blocks.add(new ScheduleBlock(d, LocalTime.of(9, 0), LocalTime.of(13, 0)));
                blocks.add(new ScheduleBlock(d, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            }
        }
        return withId(new Veterinarian(name, "General", new BigDecimal(cost), blocks), id);
    }

    static Appointment appointment(Long id, Pet pet, ClinicService service, Veterinarian vet, LocalDateTime start,
                                   AppointmentStatus status, LocalDateTime reservedUntil) {
        return withId(new Appointment(pet, service, vet, start, status, Actor.RECEPTIONIST, reservedUntil, null, NOW),
                id);
    }
}
