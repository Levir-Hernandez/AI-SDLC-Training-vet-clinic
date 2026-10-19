package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.owner.Species;
import com.vetclinic.payment.Payment;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.shared.error.ForbiddenException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * Scoping and the debt filter are expressed as JPA specifications, so they are proved against the real
 * repositories on H2 rather than with mocks.
 */
@DataJpaTest
@Import({AppointmentQueryService.class, AppointmentLifecycle.class, PetService.class,
        AppointmentQueryServiceTests.FixedClock.class})
class AppointmentQueryServiceTests {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 12, 0);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired TestEntityManager em;
    @Autowired AppointmentQueryService queries;

    Pet petA;
    Pet petB;
    ClinicService consultation;
    Veterinarian ana;
    Veterinarian luis;
    CurrentUser ownerA;
    CurrentUser ownerB;
    CurrentUser vetAna;
    CurrentUser vetLuis;
    final CurrentUser receptionist = new CurrentUser(99L, Role.RECEPTIONIST, null, null);

    Appointment scheduledA;     // past (10:00 today), ana, owner A
    Appointment scheduledB;     // luis, owner B, tomorrow
    Appointment closedUnpaidA;  // debt
    Appointment closedPaidA;
    Appointment finishedUnpaidA;

    @BeforeEach
    void setUp() {
        Owner a = em.persist(new Owner("Ann", "555-0001"));
        Owner b = em.persist(new Owner("Bob", "555-0002"));
        petA = em.persist(new Pet("Rex", Species.DOG, Breed.BULLDOG, a));
        petB = em.persist(new Pet("Tom", Species.CAT, Breed.SIAMESE, b));
        consultation = em.persist(new ClinicService(ServiceCode.GENERAL_CONSULTATION, "General consultation", 30,
                new BigDecimal("40.00")));
        List<ScheduleBlock> schedule = List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));
        ana = em.persist(new Veterinarian("Ana", "General", new BigDecimal("20.00"), schedule));
        luis = em.persist(new Veterinarian("Luis", "Surgery", new BigDecimal("30.00"), schedule));
        ownerA = new CurrentUser(1L, Role.OWNER, a.getId(), null);
        ownerB = new CurrentUser(2L, Role.OWNER, b.getId(), null);
        vetAna = new CurrentUser(3L, Role.VETERINARIAN, null, ana.getId());
        vetLuis = new CurrentUser(4L, Role.VETERINARIAN, null, luis.getId());

        scheduledA = save(petA, ana, NOW.minusHours(2), AppointmentStatus.SCHEDULED);
        scheduledB = save(petB, luis, NOW.plusDays(1), AppointmentStatus.SCHEDULED);
        closedUnpaidA = save(petA, ana, NOW.minusDays(3), AppointmentStatus.CLOSED);
        closedPaidA = save(petA, ana, NOW.minusDays(2), AppointmentStatus.CLOSED);
        finishedUnpaidA = save(petA, ana, NOW.minusDays(1), AppointmentStatus.FINISHED);
        em.persist(new Payment(closedPaidA, new BigDecimal("60.00"), NOW.minusDays(2)));
        em.flush();
        em.clear();
    }

    private Appointment save(Pet pet, Veterinarian vet, LocalDateTime start, AppointmentStatus status) {
        return em.persist(new Appointment(pet, consultation, vet, start, status, Actor.RECEPTIONIST, null, null,
                NOW.minusDays(10)));
    }

    private List<Long> ids(PageResponse<AppointmentResponse> page) {
        return page.content().stream().map(AppointmentResponse::id).toList();
    }

    private PageRequest page() {
        return PageRequest.of(0, 20);
    }

    @Test
    void ownerListIsScopedToOwnPetsAndSortedByStart() {
        PageResponse<AppointmentResponse> result = queries.list(ownerA, null, page());

        assertThat(ids(result)).containsExactly(closedUnpaidA.getId(), closedPaidA.getId(),
                finishedUnpaidA.getId(), scheduledA.getId());
        assertThat(result.totalElements()).isEqualTo(4);
    }

    @Test
    void veterinarianListIsScopedToAssignedAppointments() {
        assertThat(ids(queries.list(vetLuis, null, page()))).containsExactly(scheduledB.getId());
        assertThat(ids(queries.list(vetAna, null, page()))).hasSize(4).doesNotContain(scheduledB.getId());
    }

    @Test
    void receptionistListContainsEverything() {
        assertThat(queries.list(receptionist, AppointmentFilter.none(), page()).totalElements()).isEqualTo(5);
    }

    @Test
    void debtFilterKeepsOnlyClosedAppointmentsWithoutPayment() {
        PageResponse<AppointmentResponse> result = queries.list(receptionist,
                new AppointmentFilter(null, null, null, true), page());

        assertThat(ids(result)).containsExactly(closedUnpaidA.getId());
        assertThat(result.content().get(0).debt()).isTrue();
    }

    @Test
    void debtFlagIsTrueOnlyForClosedUnpaid() {
        assertThat(queries.get(receptionist, closedUnpaidA.getId()).debt()).isTrue();
        assertThat(queries.get(receptionist, closedPaidA.getId()).debt()).isFalse();
        assertThat(queries.get(receptionist, finishedUnpaidA.getId()).debt()).isFalse();
        assertThat(queries.get(receptionist, scheduledA.getId()).debt()).isFalse();
    }

    @Test
    void statusDateAndVeterinarianFiltersCombine() {
        assertThat(ids(queries.list(receptionist,
                new AppointmentFilter(List.of(AppointmentStatus.SCHEDULED), null, null, null), page())))
                .containsExactly(scheduledA.getId(), scheduledB.getId());
        assertThat(ids(queries.list(receptionist,
                new AppointmentFilter(null, LocalDate.of(2026, 3, 3), null, null), page())))
                .containsExactly(scheduledB.getId());
        assertThat(ids(queries.list(receptionist,
                new AppointmentFilter(List.of(AppointmentStatus.SCHEDULED), null, luis.getId(), null), page())))
                .containsExactly(scheduledB.getId());
    }

    @Test
    void historyIsNewestFirstWithUpcomingFlags() {
        Appointment future = save(petA, ana, NOW.plusDays(2), AppointmentStatus.SCHEDULED);

        PageResponse<AppointmentResponse> history = queries.historyForPet(receptionist, petA.getId(), page());

        assertThat(ids(history)).containsExactly(future.getId(), scheduledA.getId(), finishedUnpaidA.getId(),
                closedPaidA.getId(), closedUnpaidA.getId());
        assertThat(history.content().stream().map(AppointmentResponse::upcoming).toList())
                .containsExactly(true, false, false, false, false);
    }

    @Test
    void ownerCanReadHistoryOfOwnPet() {
        assertThat(queries.historyForPet(ownerA, petA.getId(), page()).totalElements()).isEqualTo(4);
    }

    @Test
    void ownerCannotReadHistoryOfAnotherOwnersPet() {
        assertThatThrownBy(() -> queries.historyForPet(ownerA, petB.getId(), page()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getCode()).isEqualTo("PET_NOT_FOUND"));
    }

    @Test
    void veterinarianCannotReadPetHistory() {
        assertThatThrownBy(() -> queries.historyForPet(vetAna, petA.getId(), page()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownerCannotGetAnotherOwnersAppointment() {
        assertThatThrownBy(() -> queries.get(ownerA, scheduledB.getId()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void veterinarianCannotGetAnotherVeterinariansAppointment() {
        assertThatThrownBy(() -> queries.get(vetAna, scheduledB.getId()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void responseCarriesAllowedActionsFromTheLifecycle() {
        assertThat(queries.get(ownerA, scheduledA.getId()).allowedActions())
                .containsExactly(AppointmentAction.CANCEL);
        assertThat(queries.get(receptionist, scheduledA.getId()).allowedActions())
                .containsExactlyInAnyOrder(AppointmentAction.START, AppointmentAction.RESCHEDULE,
                        AppointmentAction.CANCEL, AppointmentAction.EDIT_OBSERVATIONS);
        assertThat(queries.get(vetAna, scheduledA.getId()).allowedActions())
                .containsExactlyInAnyOrder(AppointmentAction.START, AppointmentAction.EDIT_OBSERVATIONS);
        assertThat(queries.get(receptionist, closedPaidA.getId()).allowedActions()).isEmpty();
    }

    @Test
    void responseMapsPetServiceVeterinarianAndOwner() {
        AppointmentResponse r = queries.get(receptionist, scheduledA.getId());

        assertThat(r.pet().name()).isEqualTo("Rex");
        assertThat(r.ownerName()).isEqualTo("Ann");
        assertThat(r.service().durationMinutes()).isEqualTo(30);
        assertThat(r.veterinarian().name()).isEqualTo("Ana");
        assertThat(r.total()).isEqualByComparingTo("60.00");
        assertThat(r.rescheduledFromId()).isNull();
        assertThat(r.rescheduledToId()).isNull();
    }
}
