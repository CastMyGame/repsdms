package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.services.EmployeeService;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.utils.DtoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherDtoService {

    private final PunishmentService punishmentService;
    private final OfficeReferralService officeReferralService;
    private final EmployeeService employeeService;
    private final DtoUtils dtoUtils;

    public TeacherOverviewDTO getTeacherOverData() throws Exception {

        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        List<OfficeReferral> allSchoolReferrals = officeReferralService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolReferralsWithDisplayInformation = officeReferralService.getTeacherResponse(allSchoolReferrals);

        //Method filter punishments by teachers
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        //Get Shout-Outs Only, School Wide
        dtoUtils.listOfShoutOuts(allSchoolPunishmentsWithDisplayInformation);

        //Get Employee and School Information based on who is the logged-in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        Optional<School> schoolOpt = employeeService.getEmployeeSchool();

        School school = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + teacher.getSchool())
        );

        // Step 1: Collect all student emails from the teacher's class rosters
        List<String> classRosterStudentEmails;
        if (teacher != null && teacher.getClasses() != null) {
            classRosterStudentEmails = teacher.getClasses().stream()
                    .flatMap(classRoster -> classRoster.getClassRoster().stream())
                    .toList();
        } else {
            classRosterStudentEmails = new ArrayList<>();
        }

// Step 2: Filter punishments by the teacher and the class roster student emails
        List<TeacherDTO> punishmentsFilteredByTeacher = allSchoolPunishmentsWithDisplayInformation.stream()
                .filter(punishment -> punishment.getTeacherEmail().equalsIgnoreCase(authentication.getName()) &&
                        classRosterStudentEmails.contains(punishment.getStudentEmail()))
                .collect(Collectors.toList());

        // Separate write-ups, shout-outs, and behavioral concerns
        List<TeacherDTO> writeUpResponse = punishmentsFilteredByTeacher.stream()
                .filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") &&
                        !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern"))
                .collect(Collectors.toList());

        List<TeacherDTO> shoutOutsResponse = punishmentsFilteredByTeacher.stream()
                .filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!"))
                .sorted(Comparator.comparing(TeacherDTO::getTimeCreated).reversed())
                .collect(Collectors.toList());

        // Step 3: Filter office referrals to only include students in the teacher's class roster
        List<TeacherDTO> filteredSchoolReferrals = allSchoolReferralsWithDisplayInformation.stream()
                .filter(referral -> classRosterStudentEmails.contains(referral.getStudentEmail()))
                .collect(Collectors.toList());

        // Update weekly punishment counts for each class in the teacher's roster
        assert teacher != null;
        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, punishmentsFilteredByTeacher);

        return new TeacherOverviewDTO(punishmentsFilteredByTeacher, writeUpResponse, shoutOutsResponse, filteredSchoolReferrals, teacher, school);
    }
}
