package com.vetclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.OwnerRepository;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetRepository;
import com.vetclinic.owner.Species;
import com.vetclinic.shared.error.DomainException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Two simultaneous bookings of the same veterinarian slot: exactly one may succeed (TASK.md acceptance). */
@SpringBootTest
class BookingConcurrencyIntegrationTests {

    @Autowired BookingService booking;
    @Autowired AppointmentRepository appointments;
    @Autowired PetRepository pets;
    @Autowired OwnerRepository owners;
    @Autowired ClinicServiceRepository services;
    @Autowired VeterinarianRepository veterinarians;

    Owner owner;
    Pet pet1;
    Pet pet2;
    ClinicService service;
    Veterinarian vet;
    LocalDateTime slot;

    final CurrentUser receptionist = new CurrentUser(1L, Role.RECEPTIONIST, null, null);

    @BeforeEach
    void setUp() {
        owner = owners.save(new Owner("Concurrent Owner", "555-9999"));
        pet1 = pets.save(new Pet("Rex", Species.DOG, Breed.BULLDOG, owner));
        pet2 = pets.save(new Pet("Max", Species.DOG, Breed.BULLDOG, owner));
        service = services.save(new ClinicService(ServiceCode.GENERAL_CONSULTATION, "General consultation", 30,
                new BigDecimal("40.00")));
        vet = veterinarians.save(new Veterinarian("Ana", "General", new BigDecimal("20.00"),
                List.of(new ScheduleBlock(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)))));
        slot = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(10, 0);
    }

    @AfterEach
    void tearDown() {
        appointments.deleteAll();
        pets.deleteAll();
        owners.deleteAll();
        services.deleteAll();
        veterinarians.deleteAll();
    }

    @Test
    void onlyOneOfTwoSimultaneousBookingsForTheSameSlotSucceeds() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Pet pet : List.of(pet1, pet2)) {
                Callable<Object> task = () -> {
                    barrier.await();
                    return booking.book(receptionist,
                            new CreateAppointmentRequest(pet.getId(), service.getId(), vet.getId(), slot));
                };
                futures.add(pool.submit(task));
            }

            int successes = 0;
            List<String> failureCodes = new ArrayList<>();
            for (Future<Object> f : futures) {
                try {
                    f.get();
                    successes++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DomainException.class);
                    failureCodes.add(((DomainException) e.getCause()).getCode());
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(failureCodes).containsExactly("SLOT_TAKEN");
            assertThat(appointments.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
