package com.reps.demogcloud.utils;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

@Component
public class StudentUtils {

    public int getWorkDaysBetweenTwoDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }

        if (!start.isBefore(end)) {
            return 0;
        }

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