package com.vetclinic.appointment.dto;

import java.time.LocalDate;
import java.util.List;

import com.vetclinic.appointment.AppointmentStatus;

/** Optional list filters; debt=true keeps only CLOSED appointments without payment. */
public record AppointmentFilter(List<AppointmentStatus> statuses, LocalDate date, Long veterinarianId, Boolean debt) {

	public static AppointmentFilter none() {
		return new AppointmentFilter(null, null, null, null);
	}
}
