package com.vetclinic.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.appointment.dto.SlotResponse;
import com.vetclinic.catalog.CatalogService;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;
import com.vetclinic.veterinarian.VeterinarianRepository;
import com.vetclinic.veterinarian.VeterinarianService;

/** Computes bookable slots: duration fits a schedule block, in the future, no overlap with blocking appointments. */
@Service
public class AvailabilityService {

	static final int STEP_MINUTES = 15;

	private final CatalogService catalog;
	private final VeterinarianService vetService;
	private final VeterinarianRepository vets;
	private final AppointmentRepository appointments;
	private final Clock clock;

	public AvailabilityService(CatalogService catalog, VeterinarianService vetService, VeterinarianRepository vets,
			AppointmentRepository appointments, Clock clock) {
		this.catalog = catalog;
		this.vetService = vetService;
		this.vets = vets;
		this.appointments = appointments;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SlotResponse> slots(Long serviceId, Optional<Long> vetId, LocalDate date) {
		ClinicService service = catalog.require(serviceId);
		List<Veterinarian> candidates = vetId.isPresent() ? List.of(vetService.require(vetId.get())) : vets.findAll();
		LocalDateTime now = LocalDateTime.now(clock);
		if (date.isBefore(now.toLocalDate())) {
			return List.of();
		}
		List<SlotResponse> result = new ArrayList<>();
		for (Veterinarian vet : candidates) {
			List<Appointment> blocking = appointments.findBlocking(vet.getId(), date.atStartOfDay(),
					date.plusDays(1).atStartOfDay(), now);
			for (ScheduleBlock block : vet.getSchedule()) {
				if (block.getDay() != date.getDayOfWeek()) {
					continue;
				}
				LocalTime t = block.getStart();
				while (!t.plusMinutes(service.getDurationMinutes()).isAfter(block.getEnd())
						&& t.plusMinutes(service.getDurationMinutes()).isAfter(t)) {
					LocalDateTime start = date.atTime(t);
					LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
					if (start.isAfter(now) && blocking.stream().noneMatch(b -> overlaps(b, start, end))) {
						result.add(new SlotResponse(vet.getId(), vet.getName(), start, end));
					}
					t = t.plusMinutes(STEP_MINUTES);
					if (t.equals(LocalTime.MIDNIGHT)) {
						break;
					}
				}
			}
		}
		result.sort(Comparator.comparing(SlotResponse::startAt).thenComparing(SlotResponse::veterinarianId));
		return result;
	}

	/** True when the slot fits a schedule block on the 15-minute grid and starts in the future. */
	public boolean isBookable(Veterinarian vet, ClinicService service, LocalDateTime start) {
		if (!start.isAfter(LocalDateTime.now(clock)) || start.getSecond() != 0 || start.getNano() != 0) {
			return false;
		}
		LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
		if (!end.toLocalDate().equals(start.toLocalDate())) {
			return false;
		}
		for (ScheduleBlock block : vet.getSchedule()) {
			if (block.getDay() != start.getDayOfWeek()) {
				continue;
			}
			long offset = Duration.between(block.getStart(), start.toLocalTime()).toMinutes();
			if (offset >= 0 && offset % STEP_MINUTES == 0 && !end.toLocalTime().isAfter(block.getEnd())) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(Appointment a, LocalDateTime start, LocalDateTime end) {
		return a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start);
	}
}
