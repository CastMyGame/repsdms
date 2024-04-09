package com.reps.demogcloud.services;

import com.reps.demogcloud.models.dto.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.student.Student;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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



        //Get Write up with student info
        List<Punishment> writeUps = punishmentService.getAllReferrals();
        List<PunishmentDTO> writeUpDTOList = new ArrayList<>();
        for (Punishment punishment: writeUps){
            PunishmentDTO punishmentDTO = new PunishmentDTO();
            punishmentDTO.setPunishment(punishment);
            punishmentDTO.setStudentEmail(punishment.getStudentEmail());

            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setFirstName(student.getFirstName());
            punishmentDTO.setLastName(student.getLastName());
            writeUpDTOList.add(punishmentDTO);

        }

        List<Punishment> punishments = punishmentService.findAllSchool();
        List<PunishmentDTO> punishmentDTOList = new ArrayList<>();
        for (Punishment punishment: punishments){
            PunishmentDTO punishmentDTO = new PunishmentDTO();
            punishmentDTO.setPunishment(punishment);
            punishmentDTO.setStudentEmail(punishment.getStudentEmail());

            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setFirstName(student.getFirstName());
            punishmentDTO.setLastName(student.getLastName());
            punishmentDTOList.add(punishmentDTO);

        }

        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        return new AdminOverviewDTO(punishmentDTOList,writeUpDTOList,teachersList);
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
        List<TeacherDTO> punishmentFilteredByShoutOuts = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")).toList();

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

            //get student info
            Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
            punishmentDTO.setFirstName(student.getFirstName());
            punishmentDTO.setLastName(student.getLastName());
            punishmentDTOList.add(punishmentDTO);

        }

        return punishmentDTOList;

}


}
