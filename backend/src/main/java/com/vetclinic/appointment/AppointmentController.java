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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CancelRequest;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.ObservationsRequest;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

	private final BookingService booking;
	private final AppointmentQueryService queries;

	private final AppointmentActionService actions;

	public AppointmentController(BookingService booking, AppointmentQueryService queries,
			AppointmentActionService actions) {
		this.actions = actions;
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

	@PostMapping("/{id}/confirm")
	@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
	public AppointmentResponse confirm(@PathVariable Long id, Authentication authentication) {
		return actions.confirm(CurrentUser.from(authentication), id);
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
	public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request,
			Authentication authentication) {
		return actions.cancel(CurrentUser.from(authentication), id, request.reason());
	}

	@PostMapping("/{id}/start")
	@PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
	public AppointmentResponse start(@PathVariable Long id, Authentication authentication) {
		return actions.start(CurrentUser.from(authentication), id);
	}

	@PostMapping("/{id}/finish")
	@PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
	public AppointmentResponse finish(@PathVariable Long id, Authentication authentication) {
		return actions.finish(CurrentUser.from(authentication), id);
	}

	@PostMapping("/{id}/close")
	@PreAuthorize("hasRole('RECEPTIONIST')")
	public AppointmentResponse close(@PathVariable Long id, Authentication authentication) {
		return actions.close(CurrentUser.from(authentication), id);
	}

	@PostMapping("/{id}/reschedule")
	@PreAuthorize("hasRole('RECEPTIONIST')")
	public ResponseEntity<AppointmentResponse> reschedule(@PathVariable Long id,
			@Valid @RequestBody RescheduleRequest request, Authentication authentication) {
		AppointmentResponse created = actions.reschedule(CurrentUser.from(authentication), id, request);
		return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
	}

	@PatchMapping("/{id}/observations")
	@PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
	public AppointmentResponse observations(@PathVariable Long id, @Valid @RequestBody ObservationsRequest request,
			Authentication authentication) {
		return actions.updateObservations(CurrentUser.from(authentication), id, request.observations());
	}

	@GetMapping
	public PageResponse<AppointmentResponse> list(@RequestParam(required = false) List<AppointmentStatus> status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long veterinarianId, @RequestParam(required = false) Boolean debt,
			@RequestParam(required = false) Boolean ongoing,
			Pageable pageable, Authentication authentication) {
		return queries.list(CurrentUser.from(authentication),
				new AppointmentFilter(status, date, veterinarianId, debt, ongoing), pageable);
	}

	@GetMapping("/{id}")
	public AppointmentResponse get(@PathVariable Long id, Authentication authentication) {
		return queries.get(CurrentUser.from(authentication), id);
	}
}
