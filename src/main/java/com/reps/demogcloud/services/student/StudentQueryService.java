package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.utils.SchoolUtils;
import com.reps.demogcloud.utils.StudentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentQueryService {

    private final StudentUtils studentUtils;
    private final SchoolUtils schoolUtils;
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;

    public List<PunishmentDTO> getIssList(String schoolName) {
        List<Punishment> punishments = punishRepository.findAllBySchoolAndArchived(schoolName, false);

        Set<String> seenStudentEmails = new HashSet<>();
        List<PunishmentDTO> result = new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (Punishment punishment : punishments) {
            if (!"OPEN".equalsIgnoreCase(punishment.getStatus())) {
                continue;
            }

            int daysSinceCreated = studentUtils.getWorkDaysBetweenTwoDates(punishment.getTimeCreated(), today);
            if (daysSinceCreated <= 1) {
                continue;
            }

            String studentEmail = punishment.getStudentEmail();
            if (seenStudentEmails.contains(studentEmail)) {
                continue;
            }

            Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
            if (student == null) {
                continue;
            }

            PunishmentDTO dto = new PunishmentDTO();
            dto.setStudentFirstName(student.getFirstName());
            dto.setStudentLastName(student.getLastName());
            dto.setStudentEmail(studentEmail);
            dto.setPunishment(punishment);

            result.add(dto);
            seenStudentEmails.add(studentEmail);
        }

        return result;
    }

    public List<PunishmentDTO> getDetentionList(String school) {
        List<Punishment> punishments = punishRepository.findAllBySchoolAndArchived(school, false);
        Set<String> uniqueStudentEmails = new HashSet<>(); // Set to keep track of unique student names
        List<PunishmentDTO> punishedStudents = new ArrayList<>();

        for (Punishment punishment : punishments) {
            if (punishment.getStatus().equals("OPEN")) {
                PunishmentDTO dto = new PunishmentDTO();
                LocalDate today = LocalDate.now();
                LocalDate punishmentTime = punishment.getTimeCreated();

                int days = studentUtils.getWorkDaysBetweenTwoDates(punishmentTime, today);

                if (days == 1) {
                    Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
                    String studentEmail = punishment.getStudentEmail();
                    dto.setStudentFirstName(student.getFirstName());
                    dto.setStudentLastName(student.getLastName());
                    dto.setStudentEmail(studentEmail);
                    dto.setPunishment(punishment);

                    if (!uniqueStudentEmails.contains(studentEmail)) {
                        punishedStudents.add(dto);
                    }

                    uniqueStudentEmails.add(studentEmail);
                }
            }
        }

        return punishedStudents;
    }

    public List<Student> findStudentByParentEmail(String parentEmail) throws ResourceNotFoundException {
        List<Student> fetchData = studentRepository.findByParentEmail(parentEmail);
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }

        log.debug(String.valueOf(studentRecord));
        return studentRecord;
    }

    public List<Student> findByStudentLastName(String lastName) throws ResourceNotFoundException {
        List<Student> fetchData = findByLastNameAndSchool(lastName);
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }

        log.debug(String.valueOf(studentRecord));
        return studentRecord;
    }

    public Student findByStudentEmail(String email) throws Exception {
        var findMe = studentRepository.findByStudentEmailIgnoreCase(email);

        if (findMe == null) {
            throw new Exception("No student with that email exists");
        }

        return findMe;
    }

    public List<Student> findByStudentEmailList(List<String> email) throws Exception {
        List<Student> studentList = new ArrayList<>();

        for (String emailAddress : email) {
            var findMe = studentRepository.findByStudentEmailIgnoreCase(emailAddress);

            if (findMe == null) {
                throw new Exception("No student with that email exists");
            }

            studentList.add(findMe);
        }

        return studentList;
    }

    public Student findByLoggedInStudent() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());

        if (findMe == null) {
            throw new Exception("No student with that email exists");
        }

        return findMe;
    }

    public List<Student> getAllStudents(boolean bool) {
        List<Student> students = findByArchivedAndSchool(bool);
        students.sort(Comparator.comparing(Student::getLastName));
        return students;
    }

    public Student findByStudentId(String studentId) throws ResourceNotFoundException {
        var findMe = studentRepository.findByStudentIdNumber(studentId);

        if (findMe == null) {
            throw new ResourceNotFoundException("No students with that ID exist");
        }

        log.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Student> findAllStudentArchived(boolean bool) throws ResourceNotFoundException {
        List<Student> archivedRecords = studentRepository.findByArchived(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in students table");
        }
        return archivedRecords;
    }

    public List<Student> findBySchool(String school) {
        return studentRepository.findBySchool(school);
    }

    public List<Student> findByLastNameAndSchool(String lastName) {
        return studentRepository.findByArchivedAndLastNameAndSchool(false, lastName, schoolUtils.fetchSchoolName());
    }

    public List<Student> findByArchivedAndSchool(boolean b) {
        List<Student> archivedRecords = studentRepository.findByArchivedAndSchool(b, schoolUtils.fetchSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
}
