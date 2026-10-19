package com.vetclinic.veterinarian;

import com.vetclinic.shared.error.BadRequestException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "veterinarian")
public class Veterinarian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String specialization;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costPerService;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "veterinarian_schedule", joinColumns = @JoinColumn(name = "veterinarian_id"))
    private List<ScheduleBlock> schedule = new ArrayList<>();

    protected Veterinarian() {
    }

    public Veterinarian(String name, String specialization, BigDecimal costPerService, List<ScheduleBlock> schedule) {
        if (costPerService == null || costPerService.signum() < 0) {
            throw new BadRequestException("INVALID_COST", "Cost per service must be zero or more");
        }
        this.name = name;
        this.specialization = specialization;
        this.costPerService = costPerService.setScale(2, RoundingMode.HALF_UP);
        this.schedule = new ArrayList<>(schedule);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSpecialization() { return specialization; }
    public BigDecimal getCostPerService() { return costPerService; }
    public List<ScheduleBlock> getSchedule() { return List.copyOf(schedule); }
}
