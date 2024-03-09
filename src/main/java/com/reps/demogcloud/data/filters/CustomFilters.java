package com.reps.demogcloud.data.filters;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomFilters {

    @Autowired
    private PunishRepository punishRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private EmployeeRepository employeeRepository;



    //Filters for Punishments
    public List<Punishment> filterPunishmentObjBySchool(List<Punishment> punishments, String schoolName) {
        return punishments
                .stream()
                .filter(punishment -> {
                    Student student = punishment.getStudent();
                    return student != null && student.getSchool() !=null && student.getSchool().equalsIgnoreCase(schoolName);

                })
                .collect(Collectors.toList());
    }


    public List<Punishment> FetchPunishmentDataByIsArchivedAndSchool(boolean bool) throws ResourceNotFoundException {
        List<Punishment> archivedRecords = punishRepository.findByIsArchivedAndStudent_School(bool,getSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }

    public List<Punishment> FetchPunishmentDataByIsArchivedAndSchoolAndStatus(boolean bool,String status) throws ResourceNotFoundException {
        List<Punishment> archivedRecords = FetchPunishmentDataByIsArchivedAndSchool(bool);
        return archivedRecords.stream().filter(x-> x.getStatus().equalsIgnoreCase(status)).toList();

    }

    public List<Punishment> FetchPunishmentDataByInfractionNameAndIsArchived(String infractionName,boolean bool) throws ResourceNotFoundException {
        List<Punishment> archivedRecords = punishRepository.findByInfractionInfractionNameAndIsArchivedAndStudent_School(infractionName,bool,getSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }


    public List<Punishment> filterPunishmentsByTeacherEmail(List<Punishment> punishments, String teacherEmail) {
        return punishments
                .stream()
                .filter(punishment -> {
                    return  punishment.getTeacherEmail() !=null && punishment.getTeacherEmail().equalsIgnoreCase(teacherEmail);

                })
                .collect(Collectors.toList());
    }
    public List<Punishment> filterPunishmentObjByStudent(List<Punishment> punishments, String studentEmail) {
        return punishments
                .stream()
                .filter(punishment -> {
                    return  punishment.getStudent().getStudentEmail() !=null && punishment.getStudent().getStudentEmail().equalsIgnoreCase(studentEmail);

                })
                .collect(Collectors.toList());
    }


    //Filter for Employee Endpoints
    public List<Employee> FetchEmployeeDataByIsArchivedAndSchool(boolean bool) throws ResourceNotFoundException {
        List<Employee> archivedRecords = employeeRepository.findByIsArchivedAndSchool(bool,getSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }



    // private methods for user details
    private String getSchoolName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserModel userModel = userService.loadUserModelByUsername(authentication.getName());
        return fetchSchoolName(authentication, userModel);
    }

    private String fetchSchoolName(Authentication authentication, UserModel userModel) {
        if (authentication != null && authentication.getPrincipal() != null) {

            if (userModel.getRoles().stream().anyMatch(role -> "STUDENT".equals(role.getRole()))) {
                Student student = studentRepository.findByStudentEmailIgnoreCase(userModel.getUsername());
                return student.getSchool();

            } else {
                Employee employee = employeeRepository.findByEmailIgnoreCase(userModel.getUsername());
                return employee.getSchool();

            }
        }
        return null;
    }



}
