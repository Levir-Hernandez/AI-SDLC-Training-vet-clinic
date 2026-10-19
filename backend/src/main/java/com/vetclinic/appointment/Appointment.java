package com.vetclinic.appointment;

import com.vetclinic.catalog.ClinicService;
import com.vetclinic.owner.Pet;
import com.vetclinic.veterinarian.Veterinarian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** One appointment covers one service, one pet and one veterinarian (TASK.md). */
@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "ix_appt_vet_start", columnList = "veterinarian_id, start_at"),
        @Index(name = "ix_appt_pet", columnList = "pet_id"),
        @Index(name = "ix_appt_status", columnList = "status")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ClinicService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veterinarian_id", nullable = false)
    private Veterinarian veterinarian;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor bookedBy;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(length = 2000)
    private String observations;

    private LocalDateTime reservedUntil;

    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_id")
    private Appointment rescheduledFrom;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Appointment() {
    }

    public Appointment(Pet pet, ClinicService service, Veterinarian veterinarian, LocalDateTime startAt,
                       AppointmentStatus initialStatus, Actor bookedBy, LocalDateTime reservedUntil,
                       Appointment rescheduledFrom, LocalDateTime createdAt) {
        this.pet = pet;
        this.service = service;
        this.veterinarian = veterinarian;
        this.startAt = startAt;
        this.endAt = startAt.plusMinutes(service.getDurationMinutes());
        this.status = initialStatus;
        this.bookedBy = bookedBy;
        this.total = service.getBasePrice().add(veterinarian.getCostPerService()).setScale(2, RoundingMode.HALF_UP);
        this.reservedUntil = reservedUntil;
        this.rescheduledFrom = rescheduledFrom;
        this.createdAt = createdAt;
    }

    /** Only {@link AppointmentLifecycle} may change the status. */
    void applyStatus(AppointmentStatus newStatus, String reason) {
        this.status = newStatus;
        if (reason != null) {
            this.cancelReason = reason;
        }
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public Long getId() { return id; }
    public Pet getPet() { return pet; }
    public ClinicService getService() { return service; }
    public Veterinarian getVeterinarian() { return veterinarian; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public Actor getBookedBy() { return bookedBy; }
    public BigDecimal getTotal() { return total; }
    public String getObservations() { return observations; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }
    public String getCancelReason() { return cancelReason; }
    public Appointment getRescheduledFrom() { return rescheduledFrom; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
