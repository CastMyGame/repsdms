package com.reps.demogcloud.services;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

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

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student requestStudentIdNumber(String studentId) throws Exception {
        var findMe = studentRepository.findByStudentIdNumber(studentId);

        if (findMe == null) {
            throw new Exception("No student with that Id exists");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }
    public List<Student> requestStudentLastName(String lastName) throws Exception {
        var findMe = studentRepository.findByLastName(lastName);
//        System.out.println(secretClient.getSecret("TWILIO-ACCOUNT-SID"));  Not working because it isn't even accessing it
        if (findMe.isEmpty()) {
            throw new Exception("No student with that Last Name exists");
        }

        return findMe;
    }

    public Student requestStudentEmail(String email) throws Exception {
        var findMe = studentRepository.findByStudentEmail(email);

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
        return studentRepository.findAll();
    }

    private Student ensureStudentExists(Student student) {
        return null;
    }
}
