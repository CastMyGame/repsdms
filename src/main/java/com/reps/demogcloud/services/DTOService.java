package com.reps.demogcloud.services;

import com.reps.demogcloud.models.dto.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DTOService {
    private final PunishmentService punishmentService;

    private final EmployeeService employeeService;

    private final StudentService studentService;

    public DTOService(PunishmentService punishmentService, EmployeeService employeeService, StudentService studentService) {
        this.punishmentService = punishmentService;
        this.employeeService = employeeService;
        this.studentService = studentService;
    }


    public AdminOverviewDTO getAdminOverData() throws Exception {
        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //Get Write Up List only - excludes Shout-Outs, BxConcerns,
        List<TeacherDTO> punishmentsFilteredByReferralsOnly = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern")).toList();

        //Get Shout-Outs Only, School Wide
        List<TeacherDTO> punishmentFilteredByShoutOuts = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")).toList();

        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        //Get Employee and School Information based on who is the logged in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        School school = employeeService.getEmployeeSchool();

        return new AdminOverviewDTO(allSchoolPunishmentsWithDisplayInformation,punishmentsFilteredByReferralsOnly, punishmentFilteredByShoutOuts,teachersList, teacher, school);
    }

    public TeacherOverviewDTO getTeacherOverData() throws Exception {

        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //Method filter punishments by teachers
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<TeacherDTO> punishmentsFilteredByTeacher = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getTeacherEmail().equalsIgnoreCase(authentication.getName()) ).toList();

        //Get Write Up List only - excludes Shout-Outs, BxConcerns,
        List<TeacherDTO> punishmentsFilteredByTeacherAndReferralsOnly = punishmentsFilteredByTeacher.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern")).toList();

        //Get Shout-Outs Only, School Wide
        List<TeacherDTO> punishmentFilteredByShoutOuts = new ArrayList<>(allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")).toList());
        punishmentFilteredByShoutOuts.sort(new Comparator<TeacherDTO>() {
            @Override
            public int compare(TeacherDTO o1, TeacherDTO o2) {
                if (o1.getTimeCreated() == null || o2.getTimeCreated() == null)
                    return 0;
                return o2.getTimeCreated().compareTo(o1.getTimeCreated());
            }
        });

        //Get Employee and School Information based on who is the logged in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        School school = employeeService.getEmployeeSchool();

        return new TeacherOverviewDTO( punishmentsFilteredByTeacher, punishmentsFilteredByTeacherAndReferralsOnly, punishmentFilteredByShoutOuts, teacher, school);
    }

    public StudentOverviewDTO getStudentOverData() throws Exception {
        List<Punishment> punishmentList = punishmentService.findAllPunishmentsByStudentEmail();
        Student student = studentService.findByLoggedInStudent();
        School school =  studentService.getStudentSchool();

        return new StudentOverviewDTO(punishmentList, school, student);
    }


public List<PunishmentDTO> getDTOPunishments() throws Exception {
        List<PunishmentDTO> punishmentDTOList = new ArrayList<>();
        List<Punishment> punishments = punishmentService.findAllSchool();
        for (Punishment punishment: punishments){
            PunishmentDTO punishmentDTO = new PunishmentDTO();
            punishmentDTO.setPunishment(punishment);
            punishmentDTO.setStudentEmail(punishment.getStudentEmail());

            //get student info
            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setStudentFirstName(student.getFirstName());
            punishmentDTO.setStudentLastName(student.getLastName());
            punishmentDTOList.add(punishmentDTO);

        }

        return punishmentDTOList;

}


}
