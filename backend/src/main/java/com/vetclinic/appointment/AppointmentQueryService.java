package com.vetclinic.appointment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vetclinic.appointment.dto.AppointmentFilter;
import com.vetclinic.appointment.dto.AppointmentResponse;
import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.PetService;
import com.vetclinic.payment.Payment;
import com.vetclinic.payment.PaymentRepository;
import com.vetclinic.shared.PageResponse;
import com.vetclinic.shared.error.NotFoundException;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Service
public class AppointmentQueryService {

	private final AppointmentRepository appointments;
	private final PaymentRepository payments;
	private final PetService petService;
	private final AppointmentLifecycle lifecycle;
	private final Clock clock;

	public AppointmentQueryService(AppointmentRepository appointments, PaymentRepository payments,
			PetService petService, AppointmentLifecycle lifecycle, Clock clock) {
		this.appointments = appointments;
		this.payments = payments;
		this.petService = petService;
		this.lifecycle = lifecycle;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<AppointmentResponse> list(CurrentUser user, AppointmentFilter filter, Pageable pageable) {
		Pageable sorted = pageable.getSort().isSorted() ? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startAt"));
		Actor actor = user.actor();
		return PageResponse.from(appointments.findAll(spec(user, filter), sorted).map(a -> toResponse(a, actor)));
	}

	@Transactional(readOnly = true)
	public AppointmentResponse get(CurrentUser user, Long id) {
		return toResponse(requireScoped(user, id), user.actor());
	}

	/** Owners and veterinarians only reach their own appointments; others look nonexistent (TASK.md scoping). */
	@Transactional(readOnly = true)
	public Appointment requireScoped(CurrentUser user, Long id) {
		Appointment a = appointments.findById(id).orElseThrow(() -> notFound(id));
		if (user.role() == Role.OWNER && !a.getPet().getOwner().getId().equals(user.ownerId())) {
			throw notFound(id);
		}
		if (user.role() == Role.VETERINARIAN && !a.getVeterinarian().getId().equals(user.veterinarianId())) {
			throw notFound(id);
		}
		return a;
	}

	@Transactional(readOnly = true)
	public PageResponse<AppointmentResponse> historyForPet(CurrentUser user, Long petId, Pageable pageable) {
		Pet pet = petService.requireAccessible(user, petId);
		Pageable p = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startAt").descending());
		Actor actor = user.actor();
		return PageResponse.from(appointments.findByPetId(pet.getId(), p).map(a -> toResponse(a, actor)));
	}

	@Transactional(readOnly = true)
	public AppointmentResponse toResponse(Appointment a, Actor actor) {
		Long toId = appointments.findByRescheduledFromId(a.getId()).stream().findFirst().map(Appointment::getId).orElse(null);
		// Debt: a CLOSED appointment that never received a payment.
		boolean debt = a.getStatus() == AppointmentStatus.CLOSED && !payments.existsByAppointmentId(a.getId());
		Pet pet = a.getPet();
		return new AppointmentResponse(a.getId(),
				new AppointmentResponse.PetRef(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed()),
				pet.getOwner().getName(),
				new AppointmentResponse.ServiceRef(a.getService().getId(), a.getService().getName(),
						a.getService().getDurationMinutes()),
				new AppointmentResponse.VetRef(a.getVeterinarian().getId(), a.getVeterinarian().getName()),
				a.getStartAt(), a.getEndAt(), a.getStatus(), a.getBookedBy(), a.getTotal(), a.getObservations(),
				a.getCancelReason(), a.getReservedUntil(),
				a.getRescheduledFrom() == null ? null : a.getRescheduledFrom().getId(), toId, debt,
				a.getStartAt().isAfter(LocalDateTime.now(clock)), lifecycle.allowedActions(a, actor));
	}

	private Specification<Appointment> spec(CurrentUser user, AppointmentFilter f) {
		AppointmentFilter filter = f == null ? AppointmentFilter.none() : f;
		return (root, query, cb) -> {
			List<Predicate> ps = new ArrayList<>();
			if (user.role() == Role.OWNER) {
				ps.add(cb.equal(root.get("pet").get("owner").get("id"), user.ownerId()));
			}
			else if (user.role() == Role.VETERINARIAN) {
				ps.add(cb.equal(root.get("veterinarian").get("id"), user.veterinarianId()));
			}
			if (filter.statuses() != null && !filter.statuses().isEmpty()) {
				ps.add(root.get("status").in(filter.statuses()));
			}
			if (Boolean.TRUE.equals(filter.ongoing())) {
				ps.add(root.get("status").in(AppointmentStatus.RESERVED, AppointmentStatus.SCHEDULED,
						AppointmentStatus.IN_PROGRESS, AppointmentStatus.FINISHED, AppointmentStatus.PAID));
			}
			if (filter.date() != null) {
				ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startAt"), filter.date().atStartOfDay()));
				ps.add(cb.lessThan(root.<LocalDateTime>get("startAt"), filter.date().plusDays(1).atStartOfDay()));
			}
			if (filter.veterinarianId() != null) {
				ps.add(cb.equal(root.get("veterinarian").get("id"), filter.veterinarianId()));
			}
			if (Boolean.TRUE.equals(filter.debt())) {
				ps.add(cb.equal(root.get("status"), AppointmentStatus.CLOSED));
				ps.add(cb.not(cb.exists(unpaid(query, root, cb))));
			}
			return cb.and(ps.toArray(new Predicate[0]));
		};
	}

	private Subquery<Long> unpaid(jakarta.persistence.criteria.CriteriaQuery<?> query, Root<Appointment> root,
			jakarta.persistence.criteria.CriteriaBuilder cb) {
		Subquery<Long> sq = query.subquery(Long.class);
		Root<Payment> p = sq.from(Payment.class);
		sq.select(p.get("id")).where(cb.equal(p.get("appointment"), root));
		return sq;
	}

	private NotFoundException notFound(Long id) {
		return new NotFoundException("APPOINTMENT_NOT_FOUND", "Appointment " + id + " not found");
	}
}
