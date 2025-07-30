package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DtoUtils {
    private final EmployeeRepository employeeRepository;

    public List<TeacherDTO> listOfShoutOuts(List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation) {
        List<TeacherDTO> punishmentFilteredByShoutOuts = new ArrayList<>(allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")).toList());

        punishmentFilteredByShoutOuts.sort((o1, o2) -> {
            if (o1.getTimeCreated() == null || o2.getTimeCreated() == null)
                return 0;
            return o2.getTimeCreated().compareTo(o1.getTimeCreated());
        });
        return punishmentFilteredByShoutOuts;
    }

    public void updateWeeklyPunishmentsForTeacherClasses(Employee teacher, List<TeacherDTO> punishmentsFilteredByTeacher) {
        if (teacher == null || teacher.getClasses() == null) {
            return; // No classes to update
        }
        LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);

        // Count punishments within the last week, grouped by class period
        Map<String, Long> weeklyPunishmentCountsByClass = punishmentsFilteredByTeacher.stream()
                .filter(dto -> dto.getTimeCreated() != null && dto.getTimeCreated().isAfter(oneWeekAgo))
                .collect(Collectors.groupingBy(TeacherDTO::getClassPeriod, Collectors.counting()));

        // Update each class in the teacher's roster with the weekly punishment count
        for (Employee.ClassRoster classRoster : teacher.getClasses()) {
            String classPeriod = classRoster.getClassPeriod();
            int writeupCount = weeklyPunishmentCountsByClass.getOrDefault(classPeriod, 0L).intValue();
            classRoster.setPunishmentsThisWeek(writeupCount);
        }

        // Save the updated teacher object back to the repository if needed
        employeeRepository.save(teacher);
    }
}
