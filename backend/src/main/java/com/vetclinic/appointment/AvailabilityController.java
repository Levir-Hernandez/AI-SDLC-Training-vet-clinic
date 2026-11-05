package com.vetclinic.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vetclinic.appointment.dto.SlotResponse;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

	private final AvailabilityService availability;
	private final Clock clock;

	public AvailabilityController(AvailabilityService availability, Clock clock) {
		this.availability = availability;
		this.clock = clock;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
	public List<SlotResponse> slots(@RequestParam Long serviceId, @RequestParam(required = false) Long veterinarianId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return availability.slots(serviceId, Optional.ofNullable(veterinarianId),
				date == null ? LocalDate.now(clock) : date);
	}
}
