package com.vetclinic.veterinarian;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VeterinarianRepository extends JpaRepository<Veterinarian, Long> {

	/** Serializes concurrent bookings for the same veterinarian. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Veterinarian v where v.id = :id")
	Optional<Veterinarian> findLockedById(@Param("id") Long id);
}
