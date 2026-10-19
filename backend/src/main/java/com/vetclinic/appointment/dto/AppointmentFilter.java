package com.vetclinic.appointment.dto;

import com.vetclinic.appointment.AppointmentStatus;
import java.time.LocalDate;
import java.util.List;

/** All fields optional; {@code debt = true} keeps only CLOSED appointments without payment (A5). */
public record AppointmentFilter(List<AppointmentStatus> statuses, LocalDate date, Long veterinarianId, Boolean debt) {

    public static AppointmentFilter none() {
        return new AppointmentFilter(null, null, null, null);
    }
}
