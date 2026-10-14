package com.vetclinic;

import org.springframework.boot.SpringApplication;

public class TestVetClinicBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(VetClinicBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
