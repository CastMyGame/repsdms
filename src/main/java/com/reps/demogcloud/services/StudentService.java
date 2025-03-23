package com.reps.demogcloud.services;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.time.DayOfWeek;
import java.time.LocalDate;


import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
public class StudentService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;
    private final SchoolRepository schoolRepository;
    private final AuthService authService;

    private final MongoTemplate mongoTemplate;



    private final CustomFilters customFilters;


    public StudentService(StudentRepository studentRepository, PunishRepository punishRepository, SchoolRepository schoolRepository, AuthService authService, MongoTemplate mongoTemplate, CustomFilters customFilters) {

        this.studentRepository = studentRepository;
        this.punishRepository = punishRepository;
        this.schoolRepository = schoolRepository;
        this.authService = authService;
        this.mongoTemplate = mongoTemplate;
        this.customFilters = customFilters;
    }

    public List<Student> findStudentByParentEmail(String parentEmail) throws ResourceNotFoundException {
        List<Student> fetchData = studentRepository.findByParentEmail(parentEmail);
        List<Student> studentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list

        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(studentRecord));
        return studentRecord;
    }
    public List<Student> findByStudentLastName(String lastName) throws ResourceNotFoundException {
        List<Student> fetchData = customFilters.findByLastNameAndSchool(lastName);
        List<Student> studentRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list
        if (studentRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(studentRecord));
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

    public StudentResponse createNewStudent (Student studentRequest ) {
        Set<RoleModel> roles = new HashSet<>();
        RoleModel student = new RoleModel();
        student.setRole("STUDENT");
        roles.add(student);
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(studentRequest.getStudentEmail().toLowerCase());
        authenticationRequest.setPassword(studentRequest.getStudentEmail().toLowerCase());
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
            studentRepository.delete(studentRequest.getStudent());}
        catch (Exception e) {
            throw new Exception("That student does not exist");
        } return studentRequest.getStudent().getFirstName() +
                " " +
                studentRequest.getStudent().getLastName() +
                " has been deleted";
    }

    public List<Student> getAllStudents(boolean bool) {
        List<Student> students = customFilters.findByIsArchivedAndSchool(bool);
        students.sort(Comparator.comparing(Student::getLastName));
        return students;
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

    public List<Student> massAssignForSchool() {
        List<Student> students = studentRepository.findAll();
        List<Student> assignedStudents = new ArrayList<>();
        for(Student student : students) {
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

    public List<Student> findBySchool(String school) {
        return studentRepository.findBySchool(school);
    }

    public List<PunishmentDTO> getDetentionList(String school){
        List<Punishment> punishments = punishRepository.findAllBySchoolNameAndIsArchived(school, false);
        Set<String> uniqueStudentEmails = new HashSet<>(); // Set to keep track of unique student names
        List<PunishmentDTO> punishedStudents = new ArrayList<>();
        for(Punishment punishment : punishments) {
            if(punishment.getStatus().equals("OPEN")) {
                PunishmentDTO dto = new PunishmentDTO();
                LocalDate today = LocalDate.now();
                LocalDate punishmentTime = punishment.getTimeCreated();

                int days = getWorkDaysBetweenTwoDates(punishmentTime, today);

                if (days == 1) {
                    Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
                    String studentEmail = punishment.getStudentEmail();
                    dto.setStudentFirstName(student.getFirstName());
                    dto.setStudentLastName(student.getLastName());
                    dto.setStudentEmail(studentEmail);
                    dto.setPunishment(punishment);
                    if(!uniqueStudentEmails.contains(studentEmail)){
                        punishedStudents.add(dto);

                    }
                    uniqueStudentEmails.add(studentEmail);
                }
            }
        }

        return punishedStudents;
    }


    public List<PunishmentDTO> getIssList(String school){
        
        List<Punishment> punishments = punishRepository.findAllBySchoolNameAndIsArchived(school, false);
        Set<String> uniqueStudentEmails = new HashSet<>(); // Set to keep track of unique student names
        List<PunishmentDTO> punishedStudents = new ArrayList<>();
        for(Punishment punishment : punishments) {
            if(punishment.getStatus().equals("OPEN")) {
                PunishmentDTO dto = new PunishmentDTO();
                LocalDate today = LocalDate.now();
                LocalDate punishmentTime = punishment.getTimeCreated();

                int days = getWorkDaysBetweenTwoDates(punishmentTime, today);

                if (days > 1) {
                    Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
                    String studentEmail = punishment.getStudentEmail();
                    dto.setStudentFirstName(student.getFirstName());
                    dto.setStudentLastName(student.getLastName());
                    dto.setStudentEmail(studentEmail);
                    dto.setPunishment(punishment);
                    if(!uniqueStudentEmails.contains(studentEmail)){
                        punishedStudents.add(dto);

                    }
                    uniqueStudentEmails.add(studentEmail);
                }
            }
        }



        return punishedStudents;
    }

    public int getWorkDaysBetweenTwoDates(LocalDate startTime, LocalDate endTime) {
        final DayOfWeek startW = startTime.getDayOfWeek();
        final DayOfWeek endW = endTime.getDayOfWeek();
        final long days = ChronoUnit.DAYS.between(startTime, endTime);
        final long daysWithoutWeekends = days - 2 * ((days + startW.getValue()) / 7);

        return (int)
                (daysWithoutWeekends
                + (startW == DayOfWeek.SUNDAY ? 1 : 0)
                + (endW == DayOfWeek.SUNDAY ? 1 : 0));
    }



    public School getStudentSchool() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = studentRepository.findByStudentEmailIgnoreCase(authentication.getName());

        return schoolRepository.findSchoolBySchoolName(findMe.getSchool());
    }

    public Student updateStudentNotes(String id, ThreadEvent event) {
        Optional<Student> studentOptional = studentRepository.findById(id);

        if(studentOptional.isEmpty()){
            StudentResponse response = new StudentResponse();
            response.setError("No Student with id " + id +" was found");
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

    public List<Student> addAsSpotter(UpdateSpottersRequest request) {
        List<Student> studentsSpotted = new ArrayList<>();
        for(String studentEmail : request.getStudentEmail()) {
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
                if (isDifferent(existingStudent.getFirstName(), updatedData.getFirstName())) {
                    existingStudent.setFirstName(updatedData.getFirstName());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getLastName(), updatedData.getLastName())) {
                    existingStudent.setLastName(updatedData.getLastName());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getParentEmail(), updatedData.getParentEmail())) {
                    existingStudent.setParentEmail(updatedData.getParentEmail());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getStudentEmail(), updatedData.getStudentEmail())) {
                    existingStudent.setStudentEmail(updatedData.getStudentEmail());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getGuidanceEmail(), updatedData.getGuidanceEmail())) {
                    existingStudent.setGuidanceEmail(updatedData.getGuidanceEmail());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getAdminEmail(), updatedData.getAdminEmail())) {
                    existingStudent.setAdminEmail(updatedData.getAdminEmail());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getAddress(), updatedData.getAddress())) {
                    existingStudent.setAddress(updatedData.getAddress());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getGrade(), updatedData.getGrade())) {
                    existingStudent.setGrade(updatedData.getGrade());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getParentPhoneNumber(), updatedData.getParentPhoneNumber())) {
                    existingStudent.setParentPhoneNumber(updatedData.getParentPhoneNumber());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getStudentPhoneNumber(), updatedData.getStudentPhoneNumber())) {
                    existingStudent.setStudentPhoneNumber(updatedData.getStudentPhoneNumber());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getArchivedBy(), updatedData.getArchivedBy())) {
                    existingStudent.setArchivedBy(updatedData.getArchivedBy());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getArchivedExplanation(), updatedData.getArchivedExplanation())) {
                    existingStudent.setArchivedExplanation(updatedData.getArchivedExplanation());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getArchivedOn(), updatedData.getArchivedOn())) {
                    existingStudent.setArchivedOn(updatedData.getArchivedOn());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getPoints(), updatedData.getPoints())) {
                    existingStudent.setPoints(updatedData.getPoints());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getSchool(), updatedData.getSchool())) {
                    existingStudent.setSchool(updatedData.getSchool());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getCurrency(), updatedData.getCurrency())) {
                    existingStudent.setCurrency(updatedData.getCurrency());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getStateStudentId(), updatedData.getStateStudentId())) {
                    existingStudent.setStateStudentId(updatedData.getStateStudentId());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getNotesArray(), updatedData.getNotesArray())) {
                    existingStudent.setNotesArray(updatedData.getNotesArray());
                    isUpdated = true;
                }
                if (isDifferent(existingStudent.getSpotters(), updatedData.getSpotters())) {
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

    private boolean isDifferent(Object existingValue, Object newValue) {
        return newValue != null && !Objects.equals(existingValue, newValue);
    }
}
