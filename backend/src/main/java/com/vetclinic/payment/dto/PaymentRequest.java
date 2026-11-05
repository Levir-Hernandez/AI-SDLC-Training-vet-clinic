package com.vetclinic.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(@NotNull @DecimalMin("0.00") BigDecimal amount) {
}
