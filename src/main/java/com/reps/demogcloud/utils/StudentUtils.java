package com.reps.demogcloud.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StudentUtils {

    public int getWorkDaysBetweenTwoDates(LocalDate start, LocalDate end) {
        int workDays = 0;
        LocalDate current = start;
        while (current.isBefore(end)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                workDays++;
            }
            current = current.plusDays(1);
        }
        return workDays;
    }

    public boolean isDifferent(Object existingValue, Object newValue) {
        return newValue != null && !Objects.equals(existingValue, newValue);
    }
}
