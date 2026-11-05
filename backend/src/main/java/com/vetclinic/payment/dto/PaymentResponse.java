package com.vetclinic.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.vetclinic.appointment.AppointmentStatus;

public record PaymentResponse(Long id, Long appointmentId, BigDecimal amount, LocalDateTime paidAt,
		AppointmentStatus appointmentStatus) {
}
