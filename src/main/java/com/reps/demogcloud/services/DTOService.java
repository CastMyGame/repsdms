package com.reps.demogcloud.services;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.student.Student;
import com.sun.security.auth.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
        List<Punishment> punishmentList = punishmentService.findAllSchool();
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

        List<Punishment> writeUpList = punishmentService.getAllReferrals();
        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        return new AdminOverviewDTO(punishmentDTOList,writeUpDTOList,teachersList);
    }

    public TeacherOverviewDTO getTeacherOverData() throws Exception {
        List<Punishment> punishmentList = punishmentService.findAllPunishmentsByTeacherEmail();
        List<Punishment> writeUpList = punishmentService.getAllReferrals();
        List<TeacherResponse> punishmentInfo = punishmentService.getTeacherResponse(punishmentList);
        List<TeacherResponse> writeUpInfo = punishmentService.getTeacherResponse(writeUpList);


        //This will Pull School Wide Shoutouts
        List<Punishment> punishments = punishmentService.findAllSchool();
        List<PunishmentDTO> schoolWideShouts = new ArrayList<>();
        for (Punishment punishment: punishments){
            if(punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!")){
                PunishmentDTO punishmentDTO = new PunishmentDTO();
                punishmentDTO.setPunishment(punishment);
                punishmentDTO.setStudentEmail(punishment.getStudentEmail());

                Student student = studentService.findByStudentEmail(punishment.getStudentEmail());
                punishmentDTO.setFirstName(student.getFirstName());
                punishmentDTO.setLastName(student.getLastName());
                schoolWideShouts.add(punishmentDTO);
            }


        }
      ;
        List<PunishmentDTO> writeDTO = new ArrayList<>();
        for (Punishment writeups: writeUpList) {
            PunishmentDTO wu = new PunishmentDTO();
            wu.setPunishment(writeups);
            wu.setStudentEmail(writeups.getStudentEmail());
            //get stduent info
            Student student = studentService.findByStudentEmail(writeups.getStudentEmail());
            wu.setFirstName(student.getFirstName());
            wu.setLastName(student.getLastName());
            writeDTO.add(wu);

        }
        
        return new TeacherOverviewDTO(punishmentList,writeDTO, punishmentInfo, writeUpInfo, schoolWideShouts);
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
