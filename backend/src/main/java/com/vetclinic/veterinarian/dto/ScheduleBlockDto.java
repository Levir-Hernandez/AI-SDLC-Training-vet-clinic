package com.vetclinic.veterinarian.dto;

import com.vetclinic.veterinarian.ScheduleBlock;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleBlockDto(
        @NotNull(message = "Select a day for each schedule block.") DayOfWeek day,
        @NotNull(message = "Enter a start time for each schedule block.") LocalTime start,
        @NotNull(message = "Enter an end time for each schedule block.") LocalTime end) {

    public static ScheduleBlockDto from(ScheduleBlock b) {
        return new ScheduleBlockDto(b.getDay(), b.getStart(), b.getEnd());
    }

    public ScheduleBlock toBlock() {
        return new ScheduleBlock(day, start, end);
    }
}
