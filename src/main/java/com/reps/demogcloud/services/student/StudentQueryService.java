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

    private final com.reps.demogcloud.services.WorkflowAccessService access;
    private final StudentUtils studentUtils;
    private final SchoolUtils schoolUtils;
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;

    public List<PunishmentDTO> getIssList(String schoolName) {
        requireSchoolStaff(schoolName);
        List<Punishment> punishments = punishRepository.findAllBySchoolAndArchived(schoolName, false);

        Set<String> seenStudentEmails = new HashSet<>();
        List<PunishmentDTO> result = new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (Punishment punishment : punishments) {
            if (!access.canReadStudent(punishment.getStudentEmail())) continue;
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
        requireSchoolStaff(school);
        List<Punishment> punishments = punishRepository.findAllBySchoolAndArchived(school, false);
        Set<String> uniqueStudentEmails = new HashSet<>(); // Set to keep track of unique student names
        List<PunishmentDTO> punishedStudents = new ArrayList<>();

        for (Punishment punishment : punishments) {
            if (!access.canReadStudent(punishment.getStudentEmail())) continue;
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
        access.requireStaff();
        List<Student> fetchData = studentRepository.findByParentEmail(parentEmail);
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived() && access.sameSchool(x.getSchool()) && access.canReadStudent(x.getStudentEmail())) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }


        return studentRecord;
    }

    public List<Student> findByStudentLastName(String lastName) throws ResourceNotFoundException {
        List<Student> fetchData = findByLastNameAndSchool(lastName);
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived() && access.sameSchool(x.getSchool()) && access.canReadStudent(x.getStudentEmail())) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }


        return studentRecord;
    }

    public Student findByStudentEmail(String email) throws Exception {
        access.requireStudentAccess(email);
        var findMe = studentRepository.findByStudentEmailIgnoreCase(email);

        if (findMe == null) {
            throw new Exception("No student with that email exists");
        }

        requireRecordSchool(findMe);
        return studentView(findMe);
    }

    public List<Student> findByStudentEmailList(List<String> email) throws Exception {
        List<Student> studentList = new ArrayList<>();

        email.forEach(access::requireStudentAccess);
        for (String emailAddress : email) {
            var findMe = studentRepository.findByStudentEmailIgnoreCase(emailAddress);

            if (findMe == null) {
                throw new Exception("No student with that email exists");
            }

            requireRecordSchool(findMe);
            studentList.add(studentView(findMe));
        }

        return studentList;
    }

    public Student findByLoggedInStudent() throws Exception {
        access.requireStudent();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());

        if (findMe == null) {
            throw new Exception("No student with that email exists");
        }

        requireRecordSchool(findMe);
        return studentView(findMe);
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

        access.requireStudentAccess(findMe.getStudentEmail());
        requireRecordSchool(findMe);
        return studentView(findMe);
    }

    public List<Student> findAllStudentArchived(boolean bool) throws ResourceNotFoundException {
        List<Student> archivedRecords = findByArchivedAndSchool(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in students table");
        }
        return archivedRecords;
    }

    public List<Student> findBySchool(String school) {
        requireSchoolStaff(school);
        return visible(studentRepository.findBySchool(school), false);
    }

    public List<Student> findByLastNameAndSchool(String lastName) {
        access.requireStaff();
        return visible(studentRepository.findByArchivedAndLastNameAndSchool(false, lastName, schoolUtils.fetchSchoolName()), false);
    }

    public List<Student> findByArchivedAndSchool(boolean b) {
        access.requireStaff();
        if (b) access.requireAdmin();
        List<Student> archivedRecords = visible(studentRepository.findByArchivedAndSchool(b, schoolUtils.fetchSchoolName()), b);
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
    private void requireRecordSchool(Student student) {
        if (!access.sameSchool(student.getSchool())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
    private Student studentView(Student record) {
        if (!access.isStudentActor()) return record;
        Student view = new Student();
        org.springframework.beans.BeanUtils.copyProperties(record, view);
        // Student assignment/wallet screens never need staff notes or subscription lists.
        view.setNotesArray(null); view.setSpotters(null);
        return view;
    }
    private void requireSchoolStaff(String school) {
        access.requireStaff();
        if (!access.sameSchool(school)) throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
    private List<Student> visible(List<Student> records, boolean archived) {
        return new ArrayList<>(records.stream().filter(s -> access.sameSchool(s.getSchool()))
                .filter(s -> archived ? s.isArchived() : access.canReadStudent(s.getStudentEmail())).toList());
    }

    /** Enrollment lookup deliberately excludes contact details, case history and balances. */
    public List<StudentDirectoryEntry> enrollmentDirectory() {
        access.requireStaff();
        return studentRepository.findByArchivedAndSchool(false, schoolUtils.fetchSchoolName()).stream()
                .filter(s -> !s.isArchived() && access.sameSchool(s.getSchool()))
                .map(s -> new StudentDirectoryEntry(s.getStudentIdNumber(), s.getStudentEmail(), s.getFirstName(), s.getLastName())).toList();
    }
    public record StudentDirectoryEntry(String studentIdNumber, String studentEmail, String firstName, String lastName) {}
}
