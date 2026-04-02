package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DtoUtils {

    private static final String POSITIVE_BEHAVIOR_SHOUT_OUT = "Positive Behavior Shout Out!";

    private final EmployeeRepository employeeRepository;

    public List<TeacherDTO> listOfShoutOuts(List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation) {
        if (allSchoolPunishmentsWithDisplayInformation == null || allSchoolPunishmentsWithDisplayInformation.isEmpty()) {
            return new ArrayList<>();
        }

        return allSchoolPunishmentsWithDisplayInformation.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.getInfractionName() != null
                        && dto.getInfractionName().equalsIgnoreCase(POSITIVE_BEHAVIOR_SHOUT_OUT))
                .sorted(Comparator.comparing(
                        TeacherDTO::getTimeCreated,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void updateWeeklyPunishmentsForTeacherClasses(Employee teacher, List<TeacherDTO> punishmentsFilteredByTeacher) {
        if (teacher == null || teacher.getClasses() == null) {
            return;
        }

        LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);

        Map<String, Long> weeklyPunishmentCountsByClass = punishmentsFilteredByTeacher == null
                ? Map.of()
                : punishmentsFilteredByTeacher.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.getClassPeriod() != null)
                .filter(dto -> dto.getTimeCreated() != null && !dto.getTimeCreated().isBefore(oneWeekAgo))
                .collect(Collectors.groupingBy(TeacherDTO::getClassPeriod, Collectors.counting()));

        for (Employee.ClassRoster classRoster : teacher.getClasses()) {
            if (classRoster == null) {
                continue;
            }

            String classPeriod = classRoster.getClassPeriod();
            int writeupCount = weeklyPunishmentCountsByClass.getOrDefault(classPeriod, 0L).intValue();
            classRoster.setPunishmentsThisWeek(writeupCount);
        }

        employeeRepository.save(teacher);
    }
}