package com.vetclinic.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.auth.Role;
import com.vetclinic.auth.UserAccountRepository;
import com.vetclinic.catalog.ClinicServiceRepository;
import com.vetclinic.veterinarian.VeterinarianRepository;

@SpringBootTest
@Transactional
class DemoDataSeederTests {

	@Autowired
	private DemoDataSeeder seeder;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private VeterinarianRepository vets;

	@Autowired
	private ClinicServiceRepository services;

	@Test
	void seedsReceptionistVetsAndServices() {
		assertThat(accounts.findByUsername("reception")).get().extracting("role").isEqualTo(Role.RECEPTIONIST);
		var ana = accounts.findByUsername("vet.ana").orElseThrow();
		var luis = accounts.findByUsername("vet.luis").orElseThrow();
		assertThat(ana.getRole()).isEqualTo(Role.VETERINARIAN);
		assertThat(vets.findById(ana.getVeterinarianId()).orElseThrow().getSchedule()).hasSize(10);
		assertThat(vets.findById(luis.getVeterinarianId()).orElseThrow().getSpecialization()).isEqualTo("Surgery");
		assertThat(services.count()).isEqualTo(4);
	}

	@Test
	void runningTwiceCreatesNoDuplicates() {
		long accountCount = accounts.count();
		long vetCount = vets.count();
		long serviceCount = services.count();

		seeder.run();

		assertThat(accounts.count()).isEqualTo(accountCount);
		assertThat(vets.count()).isEqualTo(vetCount);
		assertThat(services.count()).isEqualTo(serviceCount);
	}
}
