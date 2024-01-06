package com.reps.demogcloud.services;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
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

    public StudentService(StudentRepository studentRepository, EmailService emailService) {
        this.studentRepository = studentRepository;
        this.emailService = emailService;
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
        try {
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
        LocalDateTime createdOn = LocalDateTime.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(studentId);
        return studentRepository.save(existingRecord);
    }

    public Student addPoints(String studentEmail, Integer points) {
        Student goodStudent = studentRepository.findByStudentIdNumber(studentEmail);
        goodStudent.setPoints(goodStudent.getPoints() + points);

        return goodStudent;
    }
}
