package com.vetclinic.seed;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.veterinarian.VeterinarianService;
import com.vetclinic.veterinarian.dto.CreateVeterinarianRequest;
import com.vetclinic.veterinarian.dto.ScheduleBlockDto;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Seeds the demo data required by TASK.md (assumptions A2, A3); safe to run more than once. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

    private final UserAccountRepository accountRepository;
    private final AccountService accounts;
    private final ClinicServiceRepository services;
    private final VeterinarianService veterinarians;

    public DemoDataSeeder(UserAccountRepository accountRepository, AccountService accounts,
                          ClinicServiceRepository services, VeterinarianService veterinarians) {
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.services = services;
        this.veterinarians = veterinarians;
    }

    @Override
    public void run(String... args) {
        seedReceptionist();
        seedService(ServiceCode.GENERAL_CONSULTATION, "General consultation", 30, "40.00");
        seedService(ServiceCode.GROOMING, "Grooming", 60, "35.00");
        seedService(ServiceCode.VACCINATION, "Vaccination", 15, "25.00");
        seedService(ServiceCode.HOSPITALIZATION, "Hospitalization", 240, "150.00");
        seedVeterinarian("vet.ana", "Dr. Ana Torres", "General medicine", "20.00");
        seedVeterinarian("vet.luis", "Dr. Luis Gomez", "Surgery", "30.00");
    }

    private void seedReceptionist() {
        if (!accountRepository.existsByUsername("reception")) {
            accounts.create("reception", "reception123", Role.RECEPTIONIST, "Reception", null, null);
        }
    }

    private void seedService(ServiceCode code, String name, int minutes, String price) {
        if (services.findByCode(code).isEmpty()) {
            services.save(new ClinicService(code, name, minutes, new BigDecimal(price)));
        }
    }

    private void seedVeterinarian(String username, String name, String specialization, String cost) {
        if (accountRepository.existsByUsername(username)) {
            return;
        }
        List<ScheduleBlockDto> schedule = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            schedule.add(new ScheduleBlockDto(day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
            schedule.add(new ScheduleBlockDto(day, LocalTime.of(14, 0), LocalTime.of(18, 0)));
        }
        veterinarians.create(new CreateVeterinarianRequest(name, specialization, new BigDecimal(cost),
                username, "vet123", schedule));
    }
}
