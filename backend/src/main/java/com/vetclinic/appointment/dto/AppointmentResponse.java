package com.vetclinic.appointment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import com.vetclinic.appointment.Actor;
import com.vetclinic.appointment.AppointmentAction;
import com.vetclinic.appointment.AppointmentStatus;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Species;

public record AppointmentResponse(Long id, PetRef pet, String ownerName, ServiceRef service, VetRef veterinarian,
		LocalDateTime startAt, LocalDateTime endAt, AppointmentStatus status, Actor bookedBy, BigDecimal total,
		String observations, String cancelReason, LocalDateTime reservedUntil, Long rescheduledFromId,
		Long rescheduledToId, boolean debt, boolean upcoming, Set<AppointmentAction> allowedActions) {

	public record PetRef(Long id, String name, Species species, Breed breed) {
	}

	public record ServiceRef(Long id, String name, int durationMinutes) {
	}

	public record VetRef(Long id, String name) {
	}
}
