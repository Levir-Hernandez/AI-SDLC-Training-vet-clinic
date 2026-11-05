package com.vetclinic.veterinarian.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.vetclinic.veterinarian.ScheduleBlock;

import jakarta.validation.constraints.NotNull;

public record ScheduleBlockDto(@NotNull DayOfWeek day, @NotNull LocalTime start, @NotNull LocalTime end) {

	public ScheduleBlock toEntity() {
		return new ScheduleBlock(day, start, end);
	}

	public static ScheduleBlockDto from(ScheduleBlock b) {
		return new ScheduleBlockDto(b.getDay(), b.getStart(), b.getEnd());
	}
}
