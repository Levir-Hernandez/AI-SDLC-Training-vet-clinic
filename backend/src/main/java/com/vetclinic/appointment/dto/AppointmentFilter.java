package com.vetclinic.appointment.dto;

import com.vetclinic.appointment.AppointmentStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * All fields optional; {@code debt = true} keeps only CLOSED appointments without payment (A5);
 * {@code ongoing = true} keeps RESERVED, SCHEDULED, IN_PROGRESS, FINISHED and PAID (the reception board).
 */
public record AppointmentFilter(List<AppointmentStatus> statuses, LocalDate date, Long veterinarianId, Boolean debt,
                                Boolean ongoing) {

    public static final List<AppointmentStatus> ONGOING_STATUSES = List.of(AppointmentStatus.RESERVED,
            AppointmentStatus.SCHEDULED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.FINISHED,
            AppointmentStatus.PAID);

    public static AppointmentFilter none() {
        return new AppointmentFilter(null, null, null, null, null);
    }
}
