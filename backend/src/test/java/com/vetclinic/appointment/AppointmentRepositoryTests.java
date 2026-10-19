package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AppointmentRepositoryTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 8, 0);
    private static final LocalDateTime T10 = LocalDateTime.of(2026, 3, 2, 10, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private PaymentRepository payments;

    private Pet pet;
    private ClinicService service; // 60 minutes
    private Veterinarian vet;
    private Veterinarian otherVet;

    @BeforeEach
    void setUp() {
        Owner owner = em.persist(new Owner("Ann", "555-0001"));
        pet = em.persist(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
        service = em.persist(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("35.00")));
        List<ScheduleBlock> schedule = List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));
        vet = em.persist(new Veterinarian("Ana", "General", new BigDecimal("20.00"), schedule));
        otherVet = em.persist(new Veterinarian("Luis", "Surgery", new BigDecimal("30.00"), schedule));
    }

    private Appointment save(AppointmentStatus status, LocalDateTime start, LocalDateTime reservedUntil) {
        return em.persist(new Appointment(pet, service, vet, start, status, Actor.RECEPTIONIST, reservedUntil, null, NOW));
    }

    private boolean overlap(LocalDateTime start, LocalDateTime end, Long excludeId) {
        em.flush();
        return appointments.existsOverlap(vet.getId(), start, end, NOW, excludeId);
    }

    @Test
    void scheduledOverlapsAreDetected() {
        save(AppointmentStatus.SCHEDULED, T10, null);
        assertThat(overlap(T10.plusMinutes(30), T10.plusMinutes(90), null)).isTrue();
    }

    @Test
    void inProgressOverlapsAreDetected() {
        save(AppointmentStatus.IN_PROGRESS, T10, null);
        assertThat(overlap(T10.minusMinutes(30), T10.plusMinutes(15), null)).isTrue();
    }

    @Test
    void liveReservationOverlapsAreDetected() {
        save(AppointmentStatus.RESERVED, T10, NOW.plusMinutes(10));
        assertThat(overlap(T10, T10.plusMinutes(60), null)).isTrue();
    }

    @Test
    void adjacentAppointmentDoesNotOverlap() {
        save(AppointmentStatus.SCHEDULED, T10, null); // 10:00-11:00
        assertThat(overlap(T10.plusMinutes(60), T10.plusMinutes(120), null)).isFalse();
        assertThat(overlap(T10.minusMinutes(60), T10, null)).isFalse();
    }

    @Test
    void excludeIdIgnoresThatAppointment() {
        Appointment a = save(AppointmentStatus.SCHEDULED, T10, null);
        assertThat(overlap(T10, T10.plusMinutes(60), a.getId())).isFalse();
        assertThat(overlap(T10, T10.plusMinutes(60), a.getId() + 1000)).isTrue();
    }

    @Test
    void expiredReservationDoesNotOverlap() {
        save(AppointmentStatus.RESERVED, T10, NOW);
        assertThat(overlap(T10, T10.plusMinutes(60), null)).isFalse();
    }

    @Test
    void nonBlockingStatusesDoNotOverlap() {
        save(AppointmentStatus.CANCELLED, T10, null);
        save(AppointmentStatus.RESCHEDULED, T10, null);
        save(AppointmentStatus.FINISHED, T10, null);
        save(AppointmentStatus.PAID, T10, null);
        save(AppointmentStatus.CLOSED, T10, null);
        assertThat(overlap(T10, T10.plusMinutes(60), null)).isFalse();
    }

    @Test
    void otherVeterinarianAppointmentsDoNotOverlap() {
        em.persist(new Appointment(pet, service, otherVet, T10, AppointmentStatus.SCHEDULED,
                Actor.RECEPTIONIST, null, null, NOW));
        assertThat(overlap(T10, T10.plusMinutes(60), null)).isFalse();
    }

    @Test
    void findBlockingReturnsOnlyBlockingAppointmentsOfTheDayInOrder() {
        Appointment late = save(AppointmentStatus.SCHEDULED, T10.plusHours(2), null);
        Appointment early = save(AppointmentStatus.IN_PROGRESS, T10, null);
        save(AppointmentStatus.CANCELLED, T10.plusHours(1), null);
        save(AppointmentStatus.RESERVED, T10.plusHours(3), NOW); // expired
        em.flush();
        List<Appointment> result = appointments.findBlocking(vet.getId(),
                T10.toLocalDate().atStartOfDay(), T10.toLocalDate().plusDays(1).atStartOfDay(), NOW);
        assertThat(result).containsExactly(early, late);
    }

    @Test
    void findExpiredReservations() {
        Appointment expired = save(AppointmentStatus.RESERVED, T10, NOW.minusMinutes(1));
        save(AppointmentStatus.RESERVED, T10.plusHours(2), NOW.plusMinutes(5));
        em.flush();
        assertThat(appointments.findByStatusAndReservedUntilLessThanEqual(AppointmentStatus.RESERVED, NOW))
                .containsExactly(expired);
    }

    @Test
    void findByRescheduledFromId() {
        Appointment old = save(AppointmentStatus.RESCHEDULED, T10, null);
        Appointment next = em.persist(new Appointment(pet, service, vet, T10.plusDays(1),
                AppointmentStatus.SCHEDULED, Actor.RECEPTIONIST, null, old, NOW));
        em.flush();
        assertThat(appointments.findByRescheduledFromId(old.getId())).containsExactly(next);
    }

    @Test
    void duplicateOwnerPhoneIsRejected() {
        em.flush();
        assertThatThrownBy(() -> {
            em.persist(new Owner("Bob", "555-0001"));
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void duplicatePaymentPerAppointmentIsRejected() {
        Appointment a = save(AppointmentStatus.FINISHED, T10, null);
        payments.saveAndFlush(new Payment(a, new BigDecimal("55.00"), NOW));
        assertThat(payments.existsByAppointmentId(a.getId())).isTrue();
        assertThatThrownBy(() -> payments.saveAndFlush(new Payment(a, new BigDecimal("55.00"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
