package com.reps.demogcloud.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StudentUtilsTest {

    private final StudentUtils studentUtils = new StudentUtils();

    @Test
    void getWorkDaysBetweenTwoDates_shouldReturnZero_whenStartEqualsEnd() {
        LocalDate date = LocalDate.of(2026, 4, 1);

        int result = studentUtils.getWorkDaysBetweenTwoDates(date, date);

        assertEquals(0, result);
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldReturnZero_whenStartIsAfterEnd() {
        LocalDate start = LocalDate.of(2026, 4, 5);
        LocalDate end = LocalDate.of(2026, 4, 1);

        int result = studentUtils.getWorkDaysBetweenTwoDates(start, end);

        assertEquals(0, result);
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldThrow_whenStartIsNull() {
        LocalDate end = LocalDate.of(2026, 4, 1);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> studentUtils.getWorkDaysBetweenTwoDates(null, end)
        );

        assertEquals("Start date and end date are required.", ex.getMessage());
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldThrow_whenEndIsNull() {
        LocalDate start = LocalDate.of(2026, 4, 1);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> studentUtils.getWorkDaysBetweenTwoDates(start, null)
        );

        assertEquals("Start date and end date are required.", ex.getMessage());
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldCountOnlyWeekdays_betweenTwoWeekdays() {
        LocalDate start = LocalDate.of(2026, 4, 6); // Monday
        LocalDate end = LocalDate.of(2026, 4, 10);  // Friday

        int result = studentUtils.getWorkDaysBetweenTwoDates(start, end);

        assertEquals(4, result);
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldSkipWeekendDays() {
        LocalDate start = LocalDate.of(2026, 4, 3); // Friday
        LocalDate end = LocalDate.of(2026, 4, 7);   // Tuesday

        int result = studentUtils.getWorkDaysBetweenTwoDates(start, end);

        assertEquals(2, result); // Friday + Monday
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldReturnZero_whenRangeIsWeekendOnly() {
        LocalDate start = LocalDate.of(2026, 4, 4); // Saturday
        LocalDate end = LocalDate.of(2026, 4, 6);   // Monday

        int result = studentUtils.getWorkDaysBetweenTwoDates(start, end);

        assertEquals(0, result);
    }

    @Test
    void getWorkDaysBetweenTwoDates_shouldCountSingleWeekdayCorrectly() {
        LocalDate start = LocalDate.of(2026, 4, 6); // Monday
        LocalDate end = LocalDate.of(2026, 4, 7);   // Tuesday

        int result = studentUtils.getWorkDaysBetweenTwoDates(start, end);

        assertEquals(1, result);
    }

    @Test
    void isDifferent_shouldReturnTrue_whenNewValueIsDifferent() {
        boolean result = studentUtils.isDifferent("old", "new");

        assertTrue(result);
    }

    @Test
    void isDifferent_shouldReturnFalse_whenValuesAreEqual() {
        boolean result = studentUtils.isDifferent("same", "same");

        assertFalse(result);
    }

    @Test
    void isDifferent_shouldReturnFalse_whenNewValueIsNull() {
        boolean result = studentUtils.isDifferent("existing", null);

        assertFalse(result);
    }

    @Test
    void isDifferent_shouldReturnTrue_whenExistingValueIsNull_andNewValueIsNotNull() {
        boolean result = studentUtils.isDifferent(null, "new");

        assertTrue(result);
    }

    @Test
    void isDifferent_shouldReturnFalse_whenBothValuesAreNull() {
        boolean result = studentUtils.isDifferent(null, null);

        assertFalse(result);
    }
}
