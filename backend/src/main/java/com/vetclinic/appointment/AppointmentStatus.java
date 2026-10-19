package com.vetclinic.appointment;

public enum AppointmentStatus {
    RESERVED, SCHEDULED, IN_PROGRESS, FINISHED, PAID, CLOSED, RESCHEDULED, CANCELLED;

    /** Final states: no further transitions, observations locked. */
    public boolean isFinal() {
        return this == CLOSED || this == RESCHEDULED || this == CANCELLED;
    }
}
