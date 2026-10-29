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

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private org.springframework.data.mongodb.core.query.Criteria field(String name,Object value){return org.springframework.data.mongodb.core.query.Criteria.where(name).is(value);}
    private List<Student> scoped(org.springframework.data.mongodb.core.query.Criteria criteria){return mongo.find(canonical.query().addCriteria(criteria),Student.class);}
    private boolean inSchool(Student student){return canonical==null?access.sameSchool(student.getSchool()):canonical.owns(student);}
    private Student email(String email){return canonical==null?studentRepository.findByStudentEmailIgnoreCase(email):canonical.student(email);}
    public List<PunishmentDTO> getIssList(String schoolName) {
        requireSchoolStaff(schoolName);
        List<Punishment> punishments = canonical==null?punishRepository.findAllBySchoolAndArchived(schoolName, false):mongo.find(canonical.query().addCriteria(field("archived",false)),Punishment.class);

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

            Student student = email(studentEmail);
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
        List<Student> fetchData = canonical==null?studentRepository.findByParentEmail(parentEmail):scoped(field("parentEmail",parentEmail));
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived() && inSchool(x) && access.canReadStudent(x.getStudentEmail())) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }


        return studentRecord;
    }

    public List<Student> findByStudentLastName(String lastName) throws ResourceNotFoundException {
        List<Student> fetchData = findByLastNameAndSchool(lastName);
        List<Student> studentRecord = fetchData.stream()
                .filter(x -> !x.isArchived() && inSchool(x) && access.canReadStudent(x.getStudentEmail())) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }


        return studentRecord;
    }

    public Student findByStudentEmail(String email) throws Exception {
        access.requireStudentAccess(email);
        var findMe = email(email);

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
            var findMe = email(emailAddress);

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
        var findMe = email(authentication.getName());

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
        var findMe = canonical==null?studentRepository.findByStudentIdNumber(studentId):canonical.one(studentId,Student.class);

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
        return visible(canonical==null?studentRepository.findBySchool(school):scoped(field("archived",false)), false);
    }

    public List<Student> findByLastNameAndSchool(String lastName) {
        access.requireStaff();
        return visible(canonical==null?studentRepository.findByArchivedAndLastNameAndSchool(false, lastName, schoolUtils.fetchSchoolName()):scoped(field("archived",false).and("lastName").is(lastName)), false);
    }

    public List<Student> findByArchivedAndSchool(boolean b) {
        access.requireStaff();
        if (b) access.requireAdmin();
        List<Student> archivedRecords = visible(canonical==null?studentRepository.findByArchivedAndSchool(b, schoolUtils.fetchSchoolName()):scoped(field("archived",b)), b);
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
    private void requireRecordSchool(Student student) {
        if (!inSchool(student)) throw new org.springframework.security.access.AccessDeniedException("Access denied");
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
        if(canonical!=null){canonical.label(school);return;}
        if (!access.sameSchool(school)) throw new org.springframework.security.access.AccessDeniedException("Access denied");
    }
    private List<Student> visible(List<Student> records, boolean archived) {
        return new ArrayList<>(records.stream().filter(s -> inSchool(s))
                .filter(s -> archived ? s.isArchived() : access.canReadStudent(s.getStudentEmail())).toList());
    }

    /** Enrollment lookup deliberately excludes contact details, case history and balances. */
    public List<StudentDirectoryEntry> enrollmentDirectory() {
        access.requireStaff();
        return (canonical==null?studentRepository.findByArchivedAndSchool(false, schoolUtils.fetchSchoolName()):scoped(field("archived",false))).stream()
                .filter(s -> !s.isArchived() && inSchool(s))
                .map(s -> new StudentDirectoryEntry(s.getStudentIdNumber(), s.getStudentEmail(), s.getFirstName(), s.getLastName())).toList();
    }
    public record StudentDirectoryEntry(String studentIdNumber, String studentEmail, String firstName, String lastName) {}
}
