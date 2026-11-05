package com.vetclinic.veterinarian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vetclinic.shared.error.BadRequestException;

class ScheduleValidatorTests {

	private final ScheduleValidator validator = new ScheduleValidator();

	private static ScheduleBlock block(DayOfWeek d, String s, String e) {
		return new ScheduleBlock(d, LocalTime.parse(s), LocalTime.parse(e));
	}

	private void assertInvalid(List<ScheduleBlock> blocks) {
		assertThatThrownBy(() -> validator.validate(blocks)).isInstanceOfSatisfying(BadRequestException.class,
				e -> assertThat(e.getCode()).isEqualTo("INVALID_SCHEDULE"));
	}

	@Test
	void nonOverlappingAndAdjacentBlocksAreValid() {
		assertThatCode(() -> validator.validate(List.of(block(DayOfWeek.MONDAY, "09:00", "13:00"),
				block(DayOfWeek.MONDAY, "13:00", "17:00"), block(DayOfWeek.TUESDAY, "09:00", "12:00"))))
			.doesNotThrowAnyException();
	}

	@Test
	void overlappingBlocksAreRejected() {
		assertInvalid(List.of(block(DayOfWeek.MONDAY, "12:00", "15:00"), block(DayOfWeek.MONDAY, "09:00", "13:00")));
	}

	@Test
	void sameTimesOnDifferentDaysAreValid() {
		assertThatCode(() -> validator.validate(
				List.of(block(DayOfWeek.MONDAY, "09:00", "13:00"), block(DayOfWeek.TUESDAY, "09:00", "13:00"))))
			.doesNotThrowAnyException();
	}

	@Test
	void startEqualToEndIsRejected() {
		assertInvalid(List.of(block(DayOfWeek.MONDAY, "09:00", "09:00")));
	}

	@Test
	void startAfterEndIsRejected() {
		assertInvalid(List.of(block(DayOfWeek.MONDAY, "10:00", "09:00")));
	}

	@Test
	void emptyListIsRejected() {
		assertInvalid(List.of());
	}
}
