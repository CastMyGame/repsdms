package com.reps.demogcloud.services;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.AdminOverviewDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.TeacherOverviewDTO;
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

    public AdminOverviewDTO getAdminOverData(){
        List<Punishment> punishmentList = punishmentService.findAll();
        List<Punishment> writeUpList = punishmentService.getAllReferrals();
        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        return new AdminOverviewDTO(punishmentList,writeUpList,teachersList);
    }

    public TeacherOverviewDTO getTeacherOverData(){
        List<Punishment> punishmentList = punishmentService.findAll();
        return new TeacherOverviewDTO(punishmentList);
    }

}
