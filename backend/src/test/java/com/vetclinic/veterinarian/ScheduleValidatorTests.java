package com.vetclinic.veterinarian;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetclinic.shared.error.DomainException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleValidatorTests {

    private final ScheduleValidator validator = new ScheduleValidator();

    private static ScheduleBlock block(DayOfWeek day, int startHour, int endHour) {
        return new ScheduleBlock(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private void assertInvalid(List<ScheduleBlock> blocks) {
        assertThatThrownBy(() -> validator.validate(blocks))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("INVALID_SCHEDULE"));
    }

    @Test
    void nonOverlappingBlocksOnSameDayAreAccepted() {
        assertThatCode(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, 9, 13), block(DayOfWeek.MONDAY, 14, 18)))).doesNotThrowAnyException();
    }

    @Test
    void adjacentBlocksAreAccepted() {
        assertThatCode(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, 13, 15), block(DayOfWeek.MONDAY, 9, 13)))).doesNotThrowAnyException();
    }

    @Test
    void sameTimesOnDifferentDaysAreAccepted() {
        assertThatCode(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, 9, 13), block(DayOfWeek.TUESDAY, 9, 13)))).doesNotThrowAnyException();
    }

    @Test
    void overlappingBlocksOnSameDayAreRejected() {
        assertInvalid(List.of(block(DayOfWeek.MONDAY, 9, 13), block(DayOfWeek.MONDAY, 12, 15)));
    }

    @Test
    void overlapMessageNamesBlocks() {
        assertThatThrownBy(() -> validator.validate(List.of(
                block(DayOfWeek.MONDAY, 9, 13), block(DayOfWeek.MONDAY, 12, 15))))
                .hasMessage("Blocks on Monday 09:00-13:00 and 12:00-15:00 overlap");
    }

    @Test
    void blockWithStartEqualToEndIsRejected() {
        assertInvalid(List.of(block(DayOfWeek.MONDAY, 9, 9)));
    }

    @Test
    void blockWithStartAfterEndIsRejected() {
        assertThatThrownBy(() -> validator.validate(List.of(block(DayOfWeek.MONDAY, 13, 9))))
                .hasMessage("Block on Monday must start before it ends");
    }

    @Test
    void emptyScheduleIsRejected() {
        assertInvalid(List.of());
    }
}
