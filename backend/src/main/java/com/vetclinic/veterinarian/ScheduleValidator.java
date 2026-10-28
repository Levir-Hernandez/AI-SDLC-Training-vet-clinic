package com.vetclinic.veterinarian;

import com.vetclinic.shared.error.BadRequestException;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** TASK.md: blocks on the same weekday do not overlap and each starts before it ends. */
@Component
public class ScheduleValidator {

    public void validate(List<ScheduleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw invalid("Schedule must have at least one block");
        }
        for (ScheduleBlock b : blocks) {
            if (!b.getStart().isBefore(b.getEnd())) {
                throw invalid("Block on " + dayName(b.getDay()) + " must start before it ends");
            }
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            List<ScheduleBlock> sameDay = blocks.stream()
                    .filter(b -> b.getDay() == day)
                    .sorted(Comparator.comparing(ScheduleBlock::getStart))
                    .toList();
            for (int i = 1; i < sameDay.size(); i++) {
                ScheduleBlock prev = sameDay.get(i - 1);
                ScheduleBlock cur = sameDay.get(i);
                if (cur.getStart().isBefore(prev.getEnd())) {
                    throw invalid("Blocks on " + dayName(day) + " " + prev.getStart() + "-" + prev.getEnd()
                            + " and " + cur.getStart() + "-" + cur.getEnd() + " overlap");
                }
            }
        }
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException("INVALID_SCHEDULE", message);
    }
}
