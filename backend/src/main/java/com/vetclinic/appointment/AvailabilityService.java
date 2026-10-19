package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.shared.error.NotFoundException;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes bookable slots on a 15-minute grid (A4). A slot is available when the service duration fits a
 * schedule block, it starts after now and it overlaps no reserved (not expired), scheduled or in-progress
 * appointment (TASK.md).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    static final int GRID_MINUTES = 15;

    private final CatalogService catalog;
    private final VeterinarianRepository veterinarians;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public AvailabilityService(CatalogService catalog, VeterinarianRepository veterinarians,
                               AppointmentRepository appointments, Clock clock) {
        this.catalog = catalog;
        this.veterinarians = veterinarians;
        this.appointments = appointments;
        this.clock = clock;
    }

    public List<SlotResponse> slots(Long serviceId, Optional<Long> veterinarianId, LocalDate date) {
        ClinicService service = catalog.require(serviceId);
        List<Veterinarian> vets = veterinarianId
                .map(id -> List.of(veterinarians.findById(id).orElseThrow(() ->
                        new NotFoundException("VETERINARIAN_NOT_FOUND", "Veterinarian " + id + " not found"))))
                .orElseGet(veterinarians::findAll);
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate())) {
            return List.of();
        }
        List<SlotResponse> result = new ArrayList<>();
        for (Veterinarian vet : vets) {
            result.addAll(slotsFor(vet, service, date, now));
        }
        result.sort(Comparator.comparing(SlotResponse::startAt).thenComparing(SlotResponse::veterinarianId));
        return result;
    }

    /** True when the start is grid-aligned, in the future and the service fits inside one schedule block. */
    public boolean isBookable(Veterinarian vet, ClinicService service, LocalDateTime start) {
        if (!start.isAfter(LocalDateTime.now(clock)) || start.getMinute() % GRID_MINUTES != 0
                || start.getSecond() != 0 || start.getNano() != 0) {
            return false;
        }
        LocalTime from = start.toLocalTime();
        LocalTime to = from.plusMinutes(service.getDurationMinutes());
        if (to.isBefore(from)) { // crosses midnight
            return false;
        }
        return vet.getSchedule().stream().anyMatch(b -> b.getDay() == start.getDayOfWeek()
                && !from.isBefore(b.getStart()) && !to.isAfter(b.getEnd()));
    }

    private List<SlotResponse> slotsFor(Veterinarian vet, ClinicService service, LocalDate date, LocalDateTime now) {
        List<Appointment> blocking = appointments.findBlocking(vet.getId(), date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), now);
        List<SlotResponse> result = new ArrayList<>();
        for (ScheduleBlock block : vet.getSchedule()) {
            if (block.getDay() != date.getDayOfWeek()) {
                continue;
            }
            LocalDateTime blockEnd = date.atTime(block.getEnd());
            for (LocalDateTime start = date.atTime(block.getStart());
                 !start.plusMinutes(service.getDurationMinutes()).isAfter(blockEnd);
                 start = start.plusMinutes(GRID_MINUTES)) {
                LocalDateTime slotStart = start;
                LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
                if (start.isAfter(now) && blocking.stream().noneMatch(a -> overlaps(a, slotStart, end))) {
                    result.add(new SlotResponse(vet.getId(), vet.getName(), start, end));
                }
            }
        }
        return result;
    }

    private static boolean overlaps(Appointment a, LocalDateTime start, LocalDateTime end) {
        return a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start);
    }
}
