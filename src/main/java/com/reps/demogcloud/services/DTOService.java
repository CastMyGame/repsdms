package com.reps.demogcloud.services;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.student.Student;
import com.sun.security.auth.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DTOService {
    @Autowired
    private PunishmentService punishmentService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private StudentService studentService;


    public AdminOverviewDTO getAdminOverData() throws Exception {
        //Get All School Punishments
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        // Create Punishment DTO with Display Student Info
        List<PunishmentDTO> allSchoolPunishmentDisplay = punishmentService.getDTOResponse(allSchoolPunishments);


        //Filter Data for write ups only and create the DTO with the student info
        List<PunishmentDTO> writeupDisplay = allSchoolPunishmentDisplay.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern")).toList();

        //Teachers List
        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        return new AdminOverviewDTO(allSchoolPunishmentDisplay,writeupDisplay,teachersList);
    }

    public TeacherOverviewDTO getTeacherOverData() throws Exception {

        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherResponse> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //Method filter punishments by teachers
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<TeacherResponse> punishmentsFilteredByTeacher = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getTeacherEmail().equalsIgnoreCase(authentication.getName()) ).toList();

        //Get Write Up List only - excludes Shout-Outs, BxConcerns,
        List<TeacherResponse> punishmentsFilteredByTeacherAndReferralsOnly = punishmentsFilteredByTeacher.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern")).toList();

        //Get Shout-Outs Only, School Wide
        List<TeacherResponse> punishmentFilteredByShoutOuts = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")).toList();

        return new TeacherOverviewDTO( punishmentsFilteredByTeacher, punishmentsFilteredByTeacherAndReferralsOnly, punishmentFilteredByShoutOuts);
    }

    public StudentOverviewDTO getStudentOverData() throws Exception {
        List<Punishment> punishmentList = punishmentService.findAllPunishmentsByStudentEmail();
        Student student = studentService.findByLoggedInStudent();

        return new StudentOverviewDTO(punishmentList,student);
    }


public List<PunishmentDTO> getDTOPunishments() throws Exception {
        List<PunishmentDTO> punishmentDTOList = new ArrayList<>();
        List<Punishment> punishments = punishmentService.findAllSchool();
        for (Punishment punishment: punishments){
            PunishmentDTO punishmentDTO = new PunishmentDTO();
            punishmentDTO.setPunishment(punishment);
            punishmentDTO.setStudentEmail(punishment.getStudentEmail());

            //get stduent info
            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setFirstName(student.getFirstName());
            punishmentDTO.setLastName(student.getLastName());
            punishmentDTOList.add(punishmentDTO);

        }

        return punishmentDTOList;

}


}
