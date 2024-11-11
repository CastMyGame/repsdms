package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.dto.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class DTOService {
    private final PunishmentService punishmentService;

    private final EmployeeService employeeService;

    private final StudentService studentService;

    private final OfficeReferralService officeReferralService;

    private final SchoolService schoolService;

    private final EmployeeRepository employeeRepository;


    public AdminOverviewDTO getAdminOverData() throws Exception {
        //Reduce Time by Making Fewer Calls Add Filter Methods
        // -> this is global call to get all school related punishments as well as the call to get referrals
        List<Punishment> allSchoolPunishments = punishmentService.findAllSchool();
        List<OfficeReferral> allSchoolReferrals = officeReferralService.findAllSchool();

        //From this list created teacher response with student names included
        List<TeacherDTO> allSchoolPunishmentsWithDisplayInformation = punishmentService.getTeacherResponse(allSchoolPunishments);

        //Get Write Up List only - excludes Shout-Outs, BxConcerns,
        List<TeacherDTO> punishmentsFilteredByReferralsOnly = allSchoolPunishmentsWithDisplayInformation.stream().filter(punishment -> !punishment.getInfractionName().equalsIgnoreCase("Positive Behavior Shout Out!") && !punishment.getInfractionName().equalsIgnoreCase("Behavioral Concern")).toList();

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

        Optional<List<Employee>> teachersListOpt = employeeService.findAllByRole("TEACHER");
        List<Employee> teachersList = new ArrayList<>();
        if(teachersListOpt.isPresent()){
            teachersList = teachersListOpt.get();
        }

        //Get Employee and School Information based on who is the logged in user
        Employee teacher = employeeService.findByLoggedInEmployee();
        School school = employeeService.getEmployeeSchool();

        return new AdminOverviewDTO(allSchoolPunishmentsWithDisplayInformation,punishmentsFilteredByReferralsOnly, punishmentFilteredByShoutOuts, teachersList, allSchoolReferrals, teacher, school);
    }

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

        // Update weekly punishment counts for each class in the teacher's roster
        updateWeeklyPunishmentsForTeacherClasses(teacher, punishmentsFilteredByTeacher);

        return new TeacherOverviewDTO( punishmentsFilteredByTeacher, punishmentsFilteredByTeacherAndReferralsOnly, punishmentFilteredByShoutOuts,allSchoolReferralsWithDisplayInformation, teacher, school);
    }

    public StudentOverviewDTO getLoggedInStudentOverData() throws Exception {
        List<Punishment> punishmentList = punishmentService.findAllPunishmentsByStudentEmail();
        List<OfficeReferral> referralList = officeReferralService.findByLoggedInStudent();
        Student student = studentService.findByLoggedInStudent();
        School school =  studentService.getStudentSchool();

        return new StudentOverviewDTO(punishmentList, referralList, school, student);
    }

    public StudentOverviewDTO getStudentOverData(String studentEmail) throws Exception {
        List<Punishment> punishmentList = punishmentService.getAllPunishmentByStudentEmail(studentEmail);
        List<OfficeReferral> referralList = officeReferralService.findByStudentEmail(studentEmail);
        Student student = studentService.findByStudentEmail(studentEmail);
        School school =  schoolService.findSchoolByName(student.getSchool());

        return new StudentOverviewDTO(punishmentList, referralList, school, student);
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

    private void updateWeeklyPunishmentsForTeacherClasses(Employee teacher, List<TeacherDTO> punishmentsFilteredByTeacher) {
        LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);

        // Count punishments within the last week, grouped by class period
        Map<String, Long> weeklyPunishmentCountsByClass = punishmentsFilteredByTeacher.stream()
                .filter(dto -> dto.getTimeCreated() != null && dto.getTimeCreated().isAfter(oneWeekAgo))
                .collect(Collectors.groupingBy(TeacherDTO::getClassPeriod, Collectors.counting()));

        // Update each class in the teacher's roster with the weekly punishment count
        for (Employee.ClassRoster classRoster : teacher.getClasses()) {
            String classPeriod = classRoster.getClassName();
            int writeupCount = weeklyPunishmentCountsByClass.getOrDefault(classPeriod, 0L).intValue();
            classRoster.setPunishmentsThisWeek(writeupCount);
        }

        // Save the updated teacher object back to the repository if needed
        employeeRepository.save(teacher);
    }
}
