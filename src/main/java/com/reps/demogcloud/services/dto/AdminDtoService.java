package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.services.EmployeeService;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.StudentService;
import com.reps.demogcloud.utils.DtoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminDtoService {
    private static final String POSITIVE_BEHAVIOR_SHOUT_OUT = "Positive Behavior Shout Out!";
    private static final String ACADEMIC_CONCERN = "Academic Concern";

    private final PunishmentService punishmentService;

    private final EmployeeService employeeService;

    private final OfficeReferralService officeReferralService;

    private final DtoUtils dtoUtils;

    public AdminOverviewDTO getAdminOverData() throws Exception {
        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments as well as the call to get referrals
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        List<OfficeReferral> allSchoolReferrals = officeReferralService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //Get Write Up List only - excludes Shout-Outs, BxConcerns,
        List<TeacherDTO> punishmentsFilteredByReferralsOnly = allSchoolPunishmentsWithDisplayInformation.stream()
                .filter(punishment -> punishment.getInfractionName() != null
                        && !punishment.getInfractionName().equalsIgnoreCase(POSITIVE_BEHAVIOR_SHOUT_OUT)
                        && !punishment.getInfractionName().equalsIgnoreCase(ACADEMIC_CONCERN))
                .toList();

        //Get Shout-Outs Only, School Wide
        List<TeacherDTO> punishmentFilteredByShoutOuts = dtoUtils.listOfShoutOuts(allSchoolPunishmentsWithDisplayInformation);

        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = teachersListOpt.orElseGet(ArrayList::new);

        //Get Employee and School Information based on who is the logged-in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        if (teacher == null) {
            throw new IllegalStateException("Logged in employee not found");
        }

        Optional<School> schoolOpt = employeeService.getEmployeeSchool();

        School school = schoolOpt.orElseThrow(() ->
                new IllegalStateException("School not found: " + teacher.getSchool())
        );

        return new AdminOverviewDTO(
                allSchoolPunishmentsWithDisplayInformation,
                punishmentsFilteredByReferralsOnly,
                punishmentFilteredByShoutOuts,
                teachersList,
                allSchoolReferrals,
                teacher,
                school
        );
    }
}