package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentSpotterService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final StudentRepository studentRepository;

    public List<Student> addAsSpotter(UpdateSpottersRequest request) {
        List<Student> studentsSpotted = new ArrayList<>();
        for (String studentEmail : request.getStudentEmail()) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
            ArrayList<String> spotters = new ArrayList<>();
            if (findMe.getSpotters() != null) {
                spotters.addAll(findMe.getSpotters());
            }

            spotters.addAll(request.getSpotters());

            findMe.setSpotters(spotters);

            studentsSpotted.add(studentRepository.save(findMe));
        }
        return studentsSpotted;
    }

    public List<Student> deleteSpotters(UpdateSpottersRequest request) {
        List<Student> studentsSpotted = new ArrayList<>();
        for (String studentEmail : request.getStudentEmail()) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
            ArrayList<String> spotters = new ArrayList<>();
            if (findMe.getSpotters() != null) {
                spotters.addAll(findMe.getSpotters());
            }

            for (String email : request.getSpotters()) {
                spotters.remove(email);
            }

            findMe.setSpotters(spotters);

            studentsSpotted.add(studentRepository.save(findMe));
        }
        return studentsSpotted;
    }

    public Student removeSpotterByEmail(String email, Student student) {
        Student recordToUpdate = studentRepository.findByStudentIdNumber(student.getStudentIdNumber());
        List<String> currentSpotters = recordToUpdate.getSpotters();
        currentSpotters.remove(email);
        recordToUpdate.setSpotters(currentSpotters);
        return studentRepository.save(recordToUpdate);

    }


    public List<Student> findBySpotter(String spotterEmail) {
        List<Student> studentsSpotted = new ArrayList<>();
        return studentRepository.findBySpottersContainsIgnoreCase(spotterEmail);
    }
}
