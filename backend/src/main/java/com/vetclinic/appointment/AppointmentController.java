package com.vetclinic.appointment;

import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.appointment.dto.CreateAppointmentRequest;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.shared.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final BookingService booking;
    private final AppointmentQueryService queries;

    public AppointmentController(BookingService booking, AppointmentQueryService queries) {
        this.booking = booking;
        this.queries = queries;
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
                                                  @PageableDefault(size = 20) Pageable pageable) {
        return queries.list(CurrentUser.from(authentication), null, pageable);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(Authentication authentication, @PathVariable Long id) {
        return queries.get(CurrentUser.from(authentication), id);
    }
}
