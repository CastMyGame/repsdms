package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.utils.StudentUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentAdminService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final StudentUtils studentUtils;

    public Student addTimeToStudent(String studentEmail, int additionalHours, int additionalMinutes) {
        // Fetch the student by ID
        Student bankStudent = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (bankStudent != null) {
            Student.TimeBank currentTimeBank = bankStudent.getTimeBank();

            if (currentTimeBank == null) {
                currentTimeBank = new Student.TimeBank(0, 0);
            }

            // Add the additional time to the current timeBank
            int newMinutes = currentTimeBank.getMinutes() + additionalMinutes;
            int newHours = currentTimeBank.getHours() + additionalHours;

            // If newMinutes >= 60, convert excess minutes to hours
            if (newMinutes >= 60) {
                newHours += newMinutes / 60;
                newMinutes = newMinutes % 60;
            }

            // Set the updated time bank back to the student
            bankStudent.setTimeBank(new Student.TimeBank(newHours, newMinutes));

            // Save the updated student object
            return studentRepository.save(bankStudent);

        } else {
            throw new RuntimeException("Student not found");
        }
    }

    public List<Student> updateStudents(List<Student> students) {
        List<Student> updatedStudents = new ArrayList<>();

        for (Student updatedData : students) {
            Optional<Student> existingStudentOpt = studentRepository.findById(updatedData.getStudentIdNumber());

            if (existingStudentOpt.isPresent()) {
                Student existingStudent = existingStudentOpt.get();
                boolean isUpdated = false;

                // Check each field and update only if the new value is different
                if (studentUtils.isDifferent(existingStudent.getFirstName(), updatedData.getFirstName())) {
                    existingStudent.setFirstName(updatedData.getFirstName());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getLastName(), updatedData.getLastName())) {
                    existingStudent.setLastName(updatedData.getLastName());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getParentEmail(), updatedData.getParentEmail())) {
                    existingStudent.setParentEmail(updatedData.getParentEmail());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getStudentEmail(), updatedData.getStudentEmail())) {
                    existingStudent.setStudentEmail(updatedData.getStudentEmail());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getGuidanceEmail(), updatedData.getGuidanceEmail())) {
                    existingStudent.setGuidanceEmail(updatedData.getGuidanceEmail());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getAdminEmail(), updatedData.getAdminEmail())) {
                    existingStudent.setAdminEmail(updatedData.getAdminEmail());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getAddress(), updatedData.getAddress())) {
                    existingStudent.setAddress(updatedData.getAddress());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getGrade(), updatedData.getGrade())) {
                    existingStudent.setGrade(updatedData.getGrade());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getParentPhoneNumber(), updatedData.getParentPhoneNumber())) {
                    existingStudent.setParentPhoneNumber(updatedData.getParentPhoneNumber());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getStudentPhoneNumber(), updatedData.getStudentPhoneNumber())) {
                    existingStudent.setStudentPhoneNumber(updatedData.getStudentPhoneNumber());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getArchivedBy(), updatedData.getArchivedBy())) {
                    existingStudent.setArchivedBy(updatedData.getArchivedBy());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getArchivedExplanation(), updatedData.getArchivedExplanation())) {
                    existingStudent.setArchivedExplanation(updatedData.getArchivedExplanation());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getArchivedOn(), updatedData.getArchivedOn())) {
                    existingStudent.setArchivedOn(updatedData.getArchivedOn());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getPoints(), updatedData.getPoints())) {
                    existingStudent.setPoints(updatedData.getPoints());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getSchool(), updatedData.getSchool())) {
                    existingStudent.setSchool(updatedData.getSchool());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getCurrency(), updatedData.getCurrency())) {
                    existingStudent.setCurrency(updatedData.getCurrency());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getStateStudentId(), updatedData.getStateStudentId())) {
                    existingStudent.setStateStudentId(updatedData.getStateStudentId());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getNotesArray(), updatedData.getNotesArray())) {
                    existingStudent.setNotesArray(updatedData.getNotesArray());
                    isUpdated = true;
                }
                if (studentUtils.isDifferent(existingStudent.getSpotters(), updatedData.getSpotters())) {
                    existingStudent.setSpotters(updatedData.getSpotters());
                    isUpdated = true;
                }
                if (updatedData.getTimeBank() != null && !Objects.equals(existingStudent.getTimeBank(), updatedData.getTimeBank())) {
                    existingStudent.setTimeBank(updatedData.getTimeBank());
                    isUpdated = true;
                }

                if (isUpdated) {
                    studentRepository.save(existingStudent);
                    updatedStudents.add(existingStudent);
                }
            }
        }

        return updatedStudents;
    }

    public Student updateStudentNotes(String id, ThreadEvent event) {
        Optional<Student> studentOptional = studentRepository.findById(id);

        if (studentOptional.isEmpty()) {
            StudentResponse response = new StudentResponse();
            response.setError("No Student with id " + id + " was found");
            return null;

        }

        Student record = studentOptional.get();
        LocalDate timePosted = LocalDate.now();

        List<ThreadEvent> events = record.getNotesArray() == null ? new ArrayList<>() : record.getNotesArray();

        ThreadEvent newEvent = new ThreadEvent();
        newEvent.setEvent(event.getEvent());
        newEvent.setDate(timePosted);
        newEvent.setContent(event.getContent());
        events.add(newEvent);
//
        record.setNotesArray(events);

        return studentRepository.save(record);


    }

    public Optional<School> getStudentSchool() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());

        return schoolRepository.findBySchoolNameIgnoreCase(findMe.getSchool());
    }

    public List<Student> massAssignForSchool() {
        List<Student> students = studentRepository.findAll();
        List<Student> assignedStudents = new ArrayList<>();
        for (Student student : students) {
            String parentPhone = student.getParentPhoneNumber();

            // Check if parentPhone is not null and contains a hyphen
            if (parentPhone != null && parentPhone.contains("-")) {
                // Remove hyphens and add +1 at the beginning
                String formattedPhone = "+1" + parentPhone.replace("-", "");
                student.setParentPhoneNumber(formattedPhone);
                studentRepository.save(student);
                assignedStudents.add(student);
            }
        }
        return assignedStudents;
    }
}
