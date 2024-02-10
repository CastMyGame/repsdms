package com.reps.demogcloud.services;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
@Slf4j
public class StudentService {
    //    String keyVaultName = "repskv";
//    String keyVaultUri = "https://" + keyVaultName + ".vault.azure.net";
//
//    SecretClient secretClient = new SecretClientBuilder()
//            .vaultUrl(keyVaultUri)
//            .credential(new DefaultAzureCredentialBuilder().build())
//            .buildClient();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    private final PunishRepository punishRepository;

    private final AuthService authService;

    public StudentService(StudentRepository studentRepository, EmailService emailService, PunishRepository punishRepository, AuthService authService) {
        this.studentRepository = studentRepository;
        this.emailService = emailService;
        this.punishRepository = punishRepository;
        this.authService = authService;
    }

    public Student findByStudentIdNumber(String studentId) throws ResourceNotFoundException {
        var findMe = studentRepository.findByStudentIdNumber(studentId);

        if (findMe == null) {
            throw new ResourceNotFoundException("No student with that Id exists");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Student> findStudentByParentEmail(String parentEmail) throws Exception {
        List<Student> fetchData = studentRepository.findByParentEmail(parentEmail);
        List<Student> studentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(studentRecord));
        System.out.println(studentRecord);
        return studentRecord;
    }
    public List<Student> findByStudentLastName(String lastName) throws Exception {
        List<Student> fetchData = studentRepository.findByLastName(lastName);
        List<Student> studentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list
        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(studentRecord));
        System.out.println(studentRecord);
        return studentRecord;
    }

    public Student findByStudentEmail(String email) throws Exception {
        var findMe = studentRepository.findByStudentEmailIgnoreCase(email);

        if (findMe == null) {
            throw new Exception("No student with that email exists");
        }

        return findMe;
    }

    public StudentResponse createNewStudent (Student studentRequest ) {
        Set<RoleModel> roles = new HashSet<>();
        RoleModel student = new RoleModel();
        student.setRole("STUDENT");
        roles.add(student);
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(studentRequest.getStudentEmail().toLowerCase());
        authenticationRequest.setPassword("123abc");
        authenticationRequest.setFirstName(studentRequest.getFirstName());
        authenticationRequest.setLastName(studentRequest.getLastName());
        authenticationRequest.setSchoolName(studentRequest.getSchool());
        authenticationRequest.setRoles(roles);
        studentRequest.setPoints(0);
        try {
            authService.createEmployeeUser(authenticationRequest);
            return new StudentResponse("", studentRepository.save(studentRequest));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new StudentResponse(e.getMessage(), null);
        }
    }

    public String deleteStudent ( StudentRequest studentRequest ) throws Exception {
        try{
            System.out.println(studentRequest.getStudent());
            studentRepository.delete(studentRequest.getStudent());}
        catch (Exception e) {
            throw new Exception("That student does not exist");
        } return new StringBuilder().append(studentRequest.getStudent().getFirstName())
                .append(" ")
                .append(studentRequest.getStudent().getLastName())
                .append(" has been deleted")
                .toString();
    }

    public List<Student> getAllStudents() {
        return studentRepository.findByIsArchived(false);
    }

    private Student ensureStudentExists(Student student) {
        return null;
    }

    public Student findByStudentId(String studentId) throws ResourceNotFoundException {
        var findMe = studentRepository.findByStudentIdNumber(studentId);

        if (findMe == null) {
            throw new ResourceNotFoundException("No students with that ID exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Student> findAllStudentIsArchived(boolean bool) throws ResourceNotFoundException {
        List<Student> archivedRecords = studentRepository.findByIsArchived(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in students table");
        }
        return archivedRecords;
    }


    public Student archiveRecord(String studentId) {
        //Check for existing record
        Student existingRecord = findByStudentId(studentId);
        //Updated Record
        existingRecord.setArchived(true);
        LocalDate createdOn = LocalDate.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(studentId);
        return studentRepository.save(existingRecord);
    }

    // POINTS SERVICES
    public Student addPoints(String studentEmail, Integer points) throws ResourceNotFoundException{
        Student goodStudent = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (goodStudent == null){
            throw new ResourceNotFoundException("Student can not be found");
        }
        goodStudent.setPoints(goodStudent.getPoints() + points);
        studentRepository.save(goodStudent);

        return goodStudent;
    }

    public Student deletePoints(String studentEmail, Integer points) throws ResourceNotFoundException {
        Student badStudent = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if(badStudent.getPoints() < points) {
            throw new ResourceNotFoundException("You do not have enough points to redeem this");
        }
        badStudent.setPoints(badStudent.getPoints() - points);
        studentRepository.save(badStudent);
        return badStudent;
    }

    public List<Student> transferPoints(String givingStudentEmail, String receivingStudentEmail, Integer pointsGiven) throws ResourceNotFoundException {
        Student givingStudent = studentRepository.findByStudentEmailIgnoreCase(givingStudentEmail);
        Student receivingStudent = studentRepository.findByStudentEmailIgnoreCase(receivingStudentEmail);
        if(givingStudent.getPoints() < pointsGiven) {
            throw new ResourceNotFoundException("You do not have enough points to give");
        }
        List<Student> transferReceipt = new ArrayList<>();
        givingStudent.setPoints(givingStudent.getPoints() - pointsGiven);
        studentRepository.save(givingStudent);
        receivingStudent.setPoints(receivingStudent.getPoints() + pointsGiven);
        studentRepository.save(receivingStudent);
        transferReceipt.add(givingStudent);
        transferReceipt.add(receivingStudent);
        return transferReceipt;
    }

    public List<Student> massAssignForSchool(boolean isArchived) {
        List<Student> students = studentRepository.findAll();
        List<Student> assignedStudents = new ArrayList<>();
        for(Student student : students) {
            student.setArchived(isArchived);
            studentRepository.save(student);
            assignedStudents.add(student);
        }
        return assignedStudents;
    }

    public List<Student> getDetentionList(){
        List<Punishment> punishments = punishRepository.findAll();

        for(Punishment punishment : punishments) {
            List<Student> students = new ArrayList<>();
            String createdOn = punishment.getTimeCreated();
            LocalDate today = LocalDate.now();
            LocalDate punishment = L
                    today.getDayOfWeek()
        }
    }
}
