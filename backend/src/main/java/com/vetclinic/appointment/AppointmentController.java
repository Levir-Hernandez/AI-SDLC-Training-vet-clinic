package com.vetclinic.appointment;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

	private final BookingService booking;
	private final AppointmentQueryService queries;

	public AppointmentController(BookingService booking, AppointmentQueryService queries) {
		this.booking = booking;
		this.queries = queries;
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
	public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request,
			Authentication authentication) {
		AppointmentResponse created = booking.book(CurrentUser.from(authentication), request);
		return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
	}

	@GetMapping
	public PageResponse<AppointmentResponse> list(@RequestParam(required = false) List<AppointmentStatus> status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long veterinarianId, @RequestParam(required = false) Boolean debt,
			Pageable pageable, Authentication authentication) {
		return queries.list(CurrentUser.from(authentication),
				new AppointmentFilter(status, date, veterinarianId, debt), pageable);
	}

	@GetMapping("/{id}")
	public AppointmentResponse get(@PathVariable Long id, Authentication authentication) {
		return queries.get(CurrentUser.from(authentication), id);
	}
}
