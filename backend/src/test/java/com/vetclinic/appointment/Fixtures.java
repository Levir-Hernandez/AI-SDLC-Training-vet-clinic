package com.vetclinic.appointment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import com.vetclinic.auth.CurrentUser;
import com.vetclinic.auth.Role;
import com.vetclinic.catalog.ClinicService;
import com.vetclinic.catalog.ServiceCode;
import com.vetclinic.owner.Breed;
import com.vetclinic.owner.Owner;
import com.vetclinic.owner.Pet;
import com.vetclinic.owner.Species;
import com.vetclinic.veterinarian.ScheduleBlock;
import com.vetclinic.veterinarian.Veterinarian;

/** Shared test data: 2030-01-01 08:00 is "now" (Tuesday); the veterinarian works Wednesdays 09:00-17:00. */
public final class Fixtures {

	public static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);
	public static final LocalDateTime START = LocalDateTime.of(2030, 1, 2, 10, 0);

	private Fixtures() {
	}

	/** Clock whose time tests can move. */
	public static class MutableClock extends Clock {

		private LocalDateTime now;

		public MutableClock(LocalDateTime now) {
			this.now = now;
		}

		public void set(LocalDateTime now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.toInstant(ZoneOffset.UTC);
		}
	}

	public static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

	public static Owner owner(Long id) {
		return withId(new Owner("Owner" + id, "555-" + id), id);
	}

	public static Pet pet(Long id, Owner owner) {
		return withId(new Pet("Rex" + id, Species.DOG, Breed.BULLDOG, owner), id);
	}

	/** 60 minutes, base price 40.00. */
	public static ClinicService service(Long id) {
		return withId(new ClinicService(ServiceCode.GROOMING, "Grooming", 60, new BigDecimal("40.00")), id);
	}

	/** Cost per service 20.00, works Wednesdays 09:00-17:00. */
	public static Veterinarian vet(Long id) {
		return withId(new Veterinarian("Dr. " + id, "General", new BigDecimal("20.00"),
				List.of(new ScheduleBlock(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))), id);
	}

	public static Appointment appointment(Long id, Pet pet, ClinicService service, Veterinarian vet,
			LocalDateTime start, AppointmentStatus status) {
		Appointment a = new Appointment(pet, service, vet, start, start.plusMinutes(service.getDurationMinutes()),
				status, Actor.RECEPTIONIST, new BigDecimal("60.00"), NOW);
		return withId(a, id);
	}

	public static CurrentUser ownerUser(Long ownerId) {
		return new CurrentUser(100L + ownerId, Role.OWNER, ownerId, null);
	}

	public static CurrentUser vetUser(Long vetId) {
		return new CurrentUser(200L + vetId, Role.VETERINARIAN, null, vetId);
	}

	public static CurrentUser receptionist() {
		return new CurrentUser(300L, Role.RECEPTIONIST, null, null);
	}
}
