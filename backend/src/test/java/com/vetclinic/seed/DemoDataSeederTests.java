package com.vetclinic.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetclinic.auth.AccountService;
import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoDataSeederTests {

    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired ClinicServiceRepository services;
    @Autowired VeterinarianService veterinarianService;
    @Autowired VeterinarianRepository veterinarians;

    DemoDataSeeder seeder;

    @BeforeEach
    void clean() {
        accounts.deleteAll();
        services.deleteAll();
        veterinarians.deleteAll();
        seeder = new DemoDataSeeder(accounts, accountService, services, veterinarianService);
    }

    @Test
    void seedsReceptionistVeterinariansWithSchedulesAndServices() {
        seeder.run();

        assertThat(accounts.findByUsername("reception")).get().extracting("role").isEqualTo(Role.RECEPTIONIST);
        assertThat(accounts.findByUsername("vet.ana")).get().extracting("role").isEqualTo(Role.VETERINARIAN);
        assertThat(accounts.findByUsername("vet.luis")).isPresent();
        assertThat(services.count()).isEqualTo(4);
        assertThat(veterinarians.count()).isEqualTo(2);
        assertThat(veterinarians.findAll()).allSatisfy(v -> assertThat(v.getSchedule()).hasSize(10));
    }

    @Test
    void runningTheSeederTwiceCreatesNoDuplicates() {
        seeder.run();
        seeder.run();

        assertThat(accounts.count()).isEqualTo(3);
        assertThat(services.count()).isEqualTo(4);
        assertThat(veterinarians.count()).isEqualTo(2);
    }
}
