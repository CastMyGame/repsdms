package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentPointService {

    private final StudentRepository studentRepository;

    public Student addPoints(String studentEmail, Integer points) throws ResourceNotFoundException {
        Student goodStudent = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (goodStudent == null) {
            throw new ResourceNotFoundException("Student can not be found");
        }

        goodStudent.setPoints(goodStudent.getPoints() + points);
        studentRepository.save(goodStudent);

        return goodStudent;
    }

    public Student deletePoints(String studentEmail, Integer points) throws ResourceNotFoundException {
        Student badStudent = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (badStudent == null) {
            throw new ResourceNotFoundException("Student can not be found");
        }

        if (badStudent.getPoints() < points) {
            throw new ResourceNotFoundException("You do not have enough points to redeem this");
        }

        badStudent.setPoints(badStudent.getPoints() - points);
        studentRepository.save(badStudent);
        return badStudent;
    }

    public List<Student> transferPoints(String givingStudentEmail, String receivingStudentEmail, Integer pointsGiven)
            throws ResourceNotFoundException {

        Student givingStudent = studentRepository.findByStudentEmailIgnoreCase(givingStudentEmail);
        if (givingStudent == null) {
            throw new ResourceNotFoundException("Giving student can not be found");
        }

        Student receivingStudent = studentRepository.findByStudentEmailIgnoreCase(receivingStudentEmail);
        if (receivingStudent == null) {
            throw new ResourceNotFoundException("Receiving student can not be found");
        }

        if (givingStudent.getPoints() < pointsGiven) {
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
}