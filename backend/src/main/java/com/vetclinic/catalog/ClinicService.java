package com.vetclinic.catalog;

import com.vetclinic.shared.error.BadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "clinic_service")
public class ClinicService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ServiceCode code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    protected ClinicService() {
    }

    public ClinicService(ServiceCode code, String name, int durationMinutes, BigDecimal basePrice) {
        if (durationMinutes <= 0) {
            throw new BadRequestException("INVALID_DURATION", "Duration must be greater than zero");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new BadRequestException("INVALID_PRICE", "Price must be zero or more");
        }
        this.code = code;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.basePrice = basePrice.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public ServiceCode getCode() { return code; }
    public String getName() { return name; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getBasePrice() { return basePrice; }
}
