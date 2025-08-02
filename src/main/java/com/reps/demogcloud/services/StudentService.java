package com.reps.demogcloud.services;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import com.reps.demogcloud.services.student.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentService {
    private final StudentQueryService studentQueryService;
    private final StudentMutationService studentMutationService;
    private final StudentPointService studentPointService;
    private final StudentSpotterService studentSpotterService;
    private final StudentAdminService studentAdminService;

    public List<Student> findStudentByParentEmail(String parentEmail) throws ResourceNotFoundException {
        return studentQueryService.findStudentByParentEmail(parentEmail);
    }
    public List<Student> findByStudentLastName(String lastName) throws ResourceNotFoundException {
        return studentQueryService.findByStudentLastName(lastName);
    }

    public Student findByStudentEmail(String email) throws Exception {
        return studentQueryService.findByStudentEmail(email);
    }

    public List<Student> findByStudentEmailList(List<String> email) throws Exception {
        return studentQueryService.findByStudentEmailList(email);
    }

    public Student findByLoggedInStudent() throws Exception {
        return studentQueryService.findByLoggedInStudent();
    }

    public List<Student> getAllStudents(boolean bool) {
        return studentQueryService.getAllStudents(bool);
    }

    public Student findByStudentId(String studentId) throws ResourceNotFoundException {
        return studentQueryService.findByStudentId(studentId);
    }

    public List<Student> findAllStudentArchived(boolean bool) throws ResourceNotFoundException {
        return studentQueryService.findAllStudentArchived(bool);
    }

    public List<PunishmentDTO> getDetentionList(String school) {
        return studentQueryService.getDetentionList(school);
    }

    public List<PunishmentDTO> getIssList(String school) {
        return studentQueryService.getIssList(school);
    }

    public List<Student> findBySchool(String school) {
        return studentQueryService.findBySchool(school);
    }

    public StudentResponse createNewStudent (Student studentRequest ) {
        return studentMutationService.createNewStudent(studentRequest);
    }

    public String deleteStudent ( StudentRequest studentRequest ) throws Exception {
        return studentMutationService.deleteStudent(studentRequest);
    }

    public Student archiveRecord(String studentId) {
        return studentMutationService.archiveRecord(studentId);
    }

    // POINTS SERVICES
    public Student addPoints(String studentEmail, Integer points) throws ResourceNotFoundException{
        return studentPointService.addPoints(studentEmail, points);
    }

    public Student deletePoints(String studentEmail, Integer points) throws ResourceNotFoundException {
        return studentPointService.deletePoints(studentEmail, points);
    }

    public List<Student> transferPoints(String givingStudentEmail, String receivingStudentEmail, Integer pointsGiven) throws ResourceNotFoundException {
        return studentPointService.transferPoints(givingStudentEmail, receivingStudentEmail, pointsGiven);
    }

    public List<Student> massAssignForSchool() {
        return studentAdminService.massAssignForSchool();
    }

    public School getStudentSchool() {
        return studentAdminService.getStudentSchool();
    }

    public Student updateStudentNotes(String id, ThreadEvent event) {
        return studentAdminService.updateStudentNotes(id, event);
    }

    public Student addTimeToStudent(String studentEmail, int additionalHours, int additionalMinutes) {
        return studentAdminService.addTimeToStudent(studentEmail, additionalHours, additionalMinutes);
    }

    public List<Student> updateStudents(List<Student> students) {
        return studentAdminService.updateStudents(students);
    }

    public List<Student> addAsSpotter(UpdateSpottersRequest request) {
        return studentSpotterService.addAsSpotter(request);
    }

    public List<Student> deleteSpotters(UpdateSpottersRequest request) {
        return studentSpotterService.deleteSpotters(request);
    }

    public Student removeSpotterByEmail(String email, Student student) {
        return studentSpotterService.removeSpotterByEmail(email, student);
    }

    public List<Student> findBySpotter(String spotterEmail) {
        return studentSpotterService.findBySpotter(spotterEmail);
    }
}
