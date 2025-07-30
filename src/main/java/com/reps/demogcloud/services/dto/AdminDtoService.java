package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.utils.DtoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminDtoService {
    private final PunishmentService punishmentService;

    private final EmployeeService employeeService;

    private final StudentService studentService;

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
        List<TeacherDTO> punishmentsFilteredByReferralsOnly = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Academic Concern")).toList();

        //Get Shout-Outs Only, School Wide
        List<TeacherDTO> punishmentFilteredByShoutOuts = dtoUtils.listOfShoutOuts(allSchoolPunishmentsWithDisplayInformation);

        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if (teachersListOpt.isPresent()) {
            teachersList = teachersListOpt.get();
        }

        //Get Employee and School Information based on who is the logged-in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        School school = employeeService.getEmployeeSchool();

        return new AdminOverviewDTO(allSchoolPunishmentsWithDisplayInformation, punishmentsFilteredByReferralsOnly, punishmentFilteredByShoutOuts, teachersList, allSchoolReferrals, teacher, school);
    }
}
