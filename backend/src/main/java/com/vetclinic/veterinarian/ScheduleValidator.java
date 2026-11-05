package com.vetclinic.veterinarian;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.vetclinic.shared.error.BadRequestException;

/** TASK.md: blocks on the same weekday must not overlap and each starts before it ends. */
@Component
public class ScheduleValidator {

	public void validate(List<ScheduleBlock> blocks) {
		if (blocks == null || blocks.isEmpty()) {
			throw new BadRequestException("INVALID_SCHEDULE", "At least one schedule block is required");
		}
		for (ScheduleBlock b : blocks) {
			if (b.getDay() == null || b.getStart() == null || b.getEnd() == null
					|| !b.getStart().isBefore(b.getEnd())) {
				throw new BadRequestException("INVALID_SCHEDULE",
						"Block on " + b.getDay() + " must start before it ends");
			}
		}
		Map<DayOfWeek, List<ScheduleBlock>> byDay = blocks.stream()
			.collect(Collectors.groupingBy(ScheduleBlock::getDay));
		for (Map.Entry<DayOfWeek, List<ScheduleBlock>> e : byDay.entrySet()) {
			List<ScheduleBlock> sorted = e.getValue()
				.stream()
				.sorted(Comparator.comparing(ScheduleBlock::getStart))
				.toList();
			for (int i = 1; i < sorted.size(); i++) {
				ScheduleBlock prev = sorted.get(i - 1);
				ScheduleBlock cur = sorted.get(i);
				if (cur.getStart().isBefore(prev.getEnd())) {
					throw new BadRequestException("INVALID_SCHEDULE", "Blocks on " + e.getKey() + " "
							+ prev.getStart() + "–" + prev.getEnd() + " and " + cur.getStart() + "–"
							+ cur.getEnd() + " overlap");
				}
			}
		}
	}
}
