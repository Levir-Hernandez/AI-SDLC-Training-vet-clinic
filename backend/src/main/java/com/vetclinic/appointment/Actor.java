package com.vetclinic.appointment;

import com.vetclinic.auth.Role;

public enum Actor {
	OWNER, RECEPTIONIST, VETERINARIAN, SYSTEM;

	public static Actor from(Role role) {
		return switch (role) {
			case OWNER -> OWNER;
			case RECEPTIONIST -> RECEPTIONIST;
			case VETERINARIAN -> VETERINARIAN;
		};
	}
}
