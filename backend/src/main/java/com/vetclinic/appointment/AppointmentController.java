package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CancelRequest;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.ObservationsRequest;
import com.vetclinic.appointment.dto.RescheduleRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final BookingService booking;
    private final AppointmentQueryService queries;

    private final AppointmentActionService actions;

    public AppointmentController(BookingService booking, AppointmentQueryService queries,
                                 AppointmentActionService actions) {
        this.booking = booking;
        this.queries = queries;
        this.actions = actions;
    }

    /** Owner or reception confirms a reservation; expired holds are rejected by the lifecycle. */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public AppointmentResponse confirm(Authentication authentication, @PathVariable Long id) {
        return actions.confirm(CurrentUser.from(authentication), id);
    }

    /** Owner (own appointment) or reception cancels with a required reason. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public AppointmentResponse cancel(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody CancelRequest request) {
        return actions.cancel(CurrentUser.from(authentication), id, request.reason());
    }

    /** Assigned veterinarian or reception starts the visit. */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
    public AppointmentResponse start(Authentication authentication, @PathVariable Long id) {
        return actions.start(CurrentUser.from(authentication), id);
    }

    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
    public AppointmentResponse finish(Authentication authentication, @PathVariable Long id) {
        return actions.finish(CurrentUser.from(authentication), id);
    }

    /** Observations stay editable until the appointment is closed or cancelled. */
    @PatchMapping("/{id}/observations")
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST')")
    public AppointmentResponse observations(Authentication authentication, @PathVariable Long id,
                                            @Valid @RequestBody ObservationsRequest request) {
        return actions.updateObservations(CurrentUser.from(authentication), id, request.observations());
    }

    /** Reception moves or reassigns the appointment; the response is the new linked appointment. */
    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> reschedule(Authentication authentication, @PathVariable Long id,
                                                          @Valid @RequestBody RescheduleRequest request) {
        AppointmentResponse created = actions.reschedule(CurrentUser.from(authentication), id, request);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
    }

    /** Reception books a walk-in; the service rejects veterinarians. */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> book(Authentication authentication,
                                                    @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse created = booking.book(CurrentUser.from(authentication), request);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<AppointmentResponse> list(Authentication authentication,
                                                  @RequestParam(required = false) List<AppointmentStatus> status,
                                                  @RequestParam(required = false) Boolean ongoing,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @RequestParam(required = false) Long veterinarianId,
                                                  @PageableDefault(size = 20) Pageable pageable) {
        AppointmentFilter filter = new AppointmentFilter(status, date, veterinarianId, null, ongoing);
        return queries.list(CurrentUser.from(authentication), filter, pageable);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(Authentication authentication, @PathVariable Long id) {
        return queries.get(CurrentUser.from(authentication), id);
    }
}
