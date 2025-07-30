package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.UserContextService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class PunishmentQueryService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final PunishRepository punishRepository;
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final MongoTemplate mongoTemplate;
    private final CustomFilters customFilters;
    private final UserContextService userContextService;

    public Punishment findByPunishmentId(String punishmentId) {
        Punishment p = punishRepository.findByPunishmentId(punishmentId);
        if (p == null || p.isArchived()) {
            throw new ResourceNotFoundException("No punishment with that ID exists");
        }
        return p;
    }

    public List<Punishment> findAllByArchived(boolean archived) {
        return punishRepository.findByIsArchived(archived);
    }

    public List<Punishment> findAllForStudent(String studentEmail) {
        return punishRepository.findByStudentEmailIgnoreCase(studentEmail)
                .stream()
                .filter(p -> !p.isArchived())
                .toList();
    }

    public Punishment updateMapIndex(String id, int index) {
        Punishment punishment = punishRepository.findByPunishmentId(id);
        if (punishment == null) {
            throw new ResourceNotFoundException("No Punishment with Id " + id + " exists");
        }
        punishment.setMapIndex(index);
        return punishRepository.save(punishment);
    }

    public List<Punishment> updateDescriptions() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment p : all) {
            if (p.getInfractionDescription().size() > 1) {
                p.getInfractionDescription().remove(0);
                saved.add(punishRepository.save(p));
            }
        }
        return saved;
    }

    public List<Punishment> updateInfractionName() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment p : all) {
            Infraction inf = infractionRepository.findByInfractionId(p.getInfractionId());
            p.setInfractionName(inf.getInfractionName());
            saved.add(punishRepository.save(p));
        }
        return saved;
    }

    public List<Punishment> updateInfractionLevel() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment p : all) {
            Infraction inf = infractionRepository.findByInfractionId(p.getInfractionId());
            p.setInfractionLevel(inf.getInfractionLevel());
            saved.add(punishRepository.save(p));
        }
        return saved;
    }

    public List<Punishment> updateStudentEmails() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment p : all) {
            p.setStudentEmail(p.getStudentEmail()); // Forces update
            saved.add(punishRepository.save(p));
        }
        return saved;
    }

    public List<Punishment> updateSchools() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment p : all) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(p.getStudentEmail());
            p.setSchoolName(student.getSchool());
            saved.add(punishRepository.save(p));
        }
        return saved;
    }

    public List<Punishment> findByStudentEmailAndInfraction(String email, String infractionId) {
        List<Punishment> results = punishRepository.findByStudentEmailAndInfractionId(email, infractionId)
                .stream()
                .filter(p -> !p.isArchived())
                .toList();

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        return results;
    }

    public List<Punishment> findAll() {
        return punishRepository.findByIsArchived(false);
    }

    public List<Punishment> findAllPunishmentIsArchived(boolean bool) throws ResourceNotFoundException {
        List<Punishment> archivedRecords = punishRepository.findByIsArchived(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in punihsment table");
        }
        return archivedRecords;
    }

    public List<Punishment> getAllPunishmentByStudentEmail(String studentEmail) {
        return punishRepository.getAllPunishmentByStudentEmail(studentEmail);
    }

    public List<Punishment> getAllPunishmentForStudent(String studentEmail) {
        return punishRepository.findByStudentEmailIgnoreCase(studentEmail);
    }

    public List<Punishment> getAllOpenAssignments() {
        LocalDate now = LocalDate.now();

        List<Punishment> fetchPunishmentData = punishRepository.findByStatusAndTimeCreatedBefore("OPEN", now);
        return fetchPunishmentData.stream()
                .filter(x -> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList();  // Collect the filtered punishments into a list
    }

    public List<TeacherDTO> getTeacherResponse(List<Punishment> punishmentList) {
        // Extract student emails from the given punishmentList
        List<String> studentEmails = punishmentList.stream()
                .map(Punishment::getStudentEmail)
                .collect(Collectors.toList());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("studentEmail").in(studentEmails)), // Match only the specified student emails
                lookup("students", "studentEmail", "studentEmail", "studentInfo"), // Join with the students collection
                unwind("studentInfo"),
                project()
                        .and("studentInfo.studentEmail").as("studentEmail")
                        .and("studentInfo.firstName").as("studentFirstName")
                        .and("studentInfo.lastName").as("studentLastName")
                        .and("infractionName").as("infractionName")
                        .and("timeCreated").as("timeCreated")
                        .and("infractionDescription").as("infractionDescription")
                        .and("classPeriod").as("classPeriod")
                        .and("teacherEmail").as("teacherEmail")
                        .and("status").as("status")
                        .and("infractionLevel").as("infractionLevel")
                        .andExclude("_id")
        );

        AggregationResults<TeacherDTO> results =
                mongoTemplate.aggregate(aggregation, "Punishments", TeacherDTO.class);

        return results.getMappedResults();
    }

    public List<Punishment> findByStatus(String status) throws ResourceNotFoundException {
        var fetchData = customFilters.FetchPunishmentDataByIsArchivedAndSchoolAndStatus(false, status);


        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        logger.debug(String.valueOf(fetchData));
        return fetchData;
    }

    public List<Punishment> findAllSchool() {
        return customFilters.FetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findAllPunishmentsByStudentEmail() {
        return customFilters.LoggedInStudentFetchPunishmentDataByIsArchivedAndSchool(false);
    }

    public List<Punishment> findByArchivedAndSchool(boolean isArchived) {
        return punishRepository.findByIsArchivedAndSchoolName(isArchived, userContextService.getCurrentUserSchool());
    }

    public List<Punishment> findByArchivedStatusAndStatus(boolean archived, String status) {
        return findByArchivedAndSchool(archived).stream()
                .filter(p -> p.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByIsArchivedAndSchool(boolean isArchived) {
        String schoolName = userContextService.getSchoolForCurrentUser();
        return punishRepository.findByIsArchivedAndSchoolName(isArchived, schoolName);
    }

    public List<Punishment> findByIsArchivedAndSchoolAndStatus(boolean isArchived, String status) {
        return findByIsArchivedAndSchool(isArchived).stream()
                .filter(p -> p.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByLoggedInTeacher(boolean isArchived) {
        String email = userContextService.getCurrentUsername();
        return findByIsArchivedAndSchool(isArchived).stream()
                .filter(p -> p.getTeacherEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByLoggedInStudent(boolean isArchived) {
        String email = userContextService.getCurrentUsername();
        return findByIsArchivedAndSchool(isArchived).stream()
                .filter(p -> p.getStudentEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

}
