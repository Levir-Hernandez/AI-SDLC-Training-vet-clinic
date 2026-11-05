package com.vetclinic.appointment.dto;

import java.time.LocalDate;
import java.util.List;

import com.vetclinic.appointment.AppointmentStatus;

/**
 * Optional list filters; debt=true keeps only CLOSED appointments without payment; ongoing=true keeps
 * RESERVED, SCHEDULED, IN_PROGRESS, FINISHED and PAID.
 */
public record AppointmentFilter(List<AppointmentStatus> statuses, LocalDate date, Long veterinarianId, Boolean debt,
		Boolean ongoing) {

	public AppointmentFilter(List<AppointmentStatus> statuses, LocalDate date, Long veterinarianId, Boolean debt) {
		this(statuses, date, veterinarianId, debt, null);
	}

	public static AppointmentFilter none() {
		return new AppointmentFilter(null, null, null, null, null);
	}
}
