package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.utils.SchoolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PunishmentQueryService {

    private final PunishRepository punishRepository;
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final MongoTemplate mongoTemplate;
    private final SchoolUtils schoolUtils;
    private final UserContextService userContextService;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    private List<Punishment> scoped(Criteria criteria){return mongoTemplate.find(canonical.query().addCriteria(criteria),Punishment.class).stream().filter(p->canonical.canReadRecord(p,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT)).toList();}
    public Punishment findByPunishmentId(String punishmentId) {
        Punishment punishment = canonical==null?punishRepository.findByPunishmentId(punishmentId):canonical.record(punishmentId,Punishment.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT);
        if (punishment == null || punishment.isArchived()) {
            throw new ResourceNotFoundException("No punishment with that ID exists");
        }
        return punishment;
    }

    public List<Punishment> findAllByArchived(boolean archived) {
        return canonical==null?punishRepository.findByArchived(archived):scoped(Criteria.where("archived").is(archived));
    }

    public List<Punishment> findAllForStudent(String studentEmail) {
        return (canonical==null?punishRepository.findByStudentEmailIgnoreCase(studentEmail):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(studentEmail))))
                .stream()
                .filter(p -> !p.isArchived())
                .toList();
    }

    public Punishment updateMapIndex(String id, int index) {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        Punishment punishment = punishRepository.findByPunishmentId(id);
        if (punishment == null) {
            throw new ResourceNotFoundException("No Punishment with Id " + id + " exists");
        }
        punishment.setMapIndex(index);
        return punishRepository.save(punishment);
    }

    public List<Punishment> updateDescriptions() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            if (punishment.getInfractionDescription().size() > 1) {
                punishment.getInfractionDescription().remove(0);
                saved.add(punishRepository.save(punishment));
            }
        }
        return saved;
    }

    public List<Punishment> updateInfractionName() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionName(infraction.getInfractionName());
            saved.add(punishRepository.save(punishment));
        }
        return saved;
    }

    public List<Punishment> updateInfractionLevel() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionLevel(infraction.getInfractionLevel());
            saved.add(punishRepository.save(punishment));
        }
        return saved;
    }

    public List<Punishment> updateStudentEmails() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            punishment.setStudentEmail(punishment.getStudentEmail());
            saved.add(punishRepository.save(punishment));
        }
        return saved;
    }

    public List<Punishment> updateSchools() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            punishment.setSchool(student.getSchool());
            saved.add(punishRepository.save(punishment));
        }
        return saved;
    }

    public List<Punishment> findByStudentEmailAndInfraction(String email, String infractionId) {
        List<Punishment> results = (canonical==null?punishRepository.findByStudentEmailAndInfractionId(email, infractionId):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(email)).and("infractionId").is(infractionId)))
                .stream()
                .filter(p -> !p.isArchived())
                .toList();

        if (results.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        return results;
    }

    public List<Punishment> findAll() {
        return canonical==null?punishRepository.findByArchived(false):scoped(Criteria.where("archived").is(false));
    }

    public List<Punishment> findAllPunishmentArchived(boolean archived) {
        List<Punishment> archivedRecords = canonical==null?punishRepository.findByArchived(archived):scoped(Criteria.where("archived").is(archived));
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in punihsment table");
        }
        return archivedRecords;
    }

    public List<Punishment> getAllPunishmentByStudentEmail(String studentEmail) {
        return canonical==null?punishRepository.getAllPunishmentByStudentEmail(studentEmail):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(studentEmail)));
    }

    public List<Punishment> getAllPunishmentForStudent(String studentEmail) {
        return (canonical==null?punishRepository.findByStudentEmailIgnoreCase(studentEmail):scoped(Criteria.where("studentEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(studentEmail))));
    }

    public List<Punishment> getAllOpenAssignments() {
        LocalDate now = LocalDate.now();
        return (canonical==null?punishRepository.findByStatusAndTimeCreatedBefore("OPEN", now):scoped(Criteria.where("status").is("OPEN").and("timeCreated").lt(now)))
                .stream()
                .filter(p -> !p.isArchived())
                .toList();
    }

    public List<TeacherDTO> getTeacherResponse(List<Punishment> punishmentList) {
        List<String> studentEmails = punishmentList.stream()
                .map(Punishment::getStudentEmail)
                .collect(Collectors.toList());

        Aggregation aggregation = newAggregation(
                match(canonical==null?Criteria.where("studentEmail").in(studentEmails):Criteria.where("tenantSchoolId").is(canonical.tenant()).and("_id").in(punishmentList.stream().filter(p->canonical.canReadRecord(p,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT)).map(Punishment::getPunishmentId).toList())),
                canonical==null?lookup("students", "studentEmail", "studentEmail", "studentInfo"):canonical.studentLookup("studentInfo"),
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

    public List<Punishment> findByStatus(String status) {
        List<Punishment> fetchData = fetchPunishmentDataByArchivedAndSchoolAndStatus(false, status);
        if (fetchData.isEmpty()) {
            throw new ResourceNotFoundException("No punishments with that status exist");
        }
        log.debug("Punishments with status {}: {}", status, fetchData);
        return fetchData;
    }

    public List<Punishment> findAllSchool() {
        return fetchPunishmentDataByArchivedAndSchool(false);
    }

    public List<Punishment> findAllPunishmentsByStudentEmail() {
        return loggedInStudentFetchPunishmentDataByArchivedAndSchool(false);
    }

    public List<Punishment> findByArchivedAndSchool(boolean archived) {
        return canonical==null?punishRepository.findByArchivedAndSchool(archived, userContextService.getCurrentUserSchool()):scoped(Criteria.where("archived").is(archived));
    }

    public List<Punishment> findByArchivedStatusAndStatus(boolean archived, String status) {
        return findByArchivedAndSchool(archived).stream()
                .filter(p -> p.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByArchivedAndSchoolAndStatus(boolean archived, String status) {
        return findByArchivedAndSchool(archived).stream()
                .filter(p -> p.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByLoggedInTeacher(boolean archived) {
        String email = userContextService.getCurrentUsername();
        return findByArchivedAndSchool(archived).stream()
                .filter(p -> p.getTeacherEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

    public List<Punishment> findByLoggedInStudent(boolean archived) {
        String email = userContextService.getCurrentUsername();
        return findByArchivedAndSchool(archived).stream()
                .filter(p -> p.getStudentEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }

    public List<Punishment> filterPunishmentObjBySchool(List<Punishment> punishments, String schoolName) {
        if(canonical!=null){canonical.label(schoolName);return punishments.stream().filter(p->canonical.canReadRecord(p,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT)).toList();}
        return punishments.stream()
                .filter(punishment -> {
                    Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
                    return student != null
                            && student.getSchool() != null
                            && student.getSchool().equalsIgnoreCase(schoolName);
                })
                .collect(Collectors.toList());
    }

    public List<Punishment> fetchPunishmentDataByArchivedAndSchool(boolean archived) {
        List<Punishment> archivedRecords =
                canonical==null?punishRepository.findByArchivedAndSchool(archived, schoolUtils.fetchSchoolName()):scoped(Criteria.where("archived").is(archived));
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }

    public List<Punishment> fetchPunishmentDataByArchivedAndSchoolAndStatus(boolean archived, String status) {
        List<Punishment> archivedRecords = fetchPunishmentDataByArchivedAndSchool(archived);
        return archivedRecords.stream()
                .filter(x -> x.getStatus().equalsIgnoreCase(status))
                .toList();
    }

    public List<Punishment> fetchPunishmentDataByInfractionNameAndArchived(String infractionId, boolean archived) {
        List<Punishment> archivedRecords = canonical==null?punishRepository.findByInfractionIdAndArchivedAndSchool(infractionId,archived,schoolUtils.fetchSchoolName()):scoped(Criteria.where("infractionId").is(infractionId).and("archived").is(archived));
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }

    public List<Punishment> filterPunishmentsByTeacherEmail(List<Punishment> punishments, String teacherEmail) {
        return punishments.stream()
                .filter(punishment ->
                        punishment.getTeacherEmail() != null
                                && punishment.getTeacherEmail().equalsIgnoreCase(teacherEmail))
                .collect(Collectors.toList());
    }

    public List<Punishment> filterPunishmentObjByStudent(List<Punishment> punishments, String studentEmail) {
        return punishments.stream()
                .filter(punishment ->
                        punishment.getStudentEmail() != null
                                && punishment.getStudentEmail().equalsIgnoreCase(studentEmail))
                .collect(Collectors.toList());
    }

    public List<Punishment> loggedInUserFetchPunishmentDataByArchivedAndSchool(boolean archived) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Punishment> archivedRecords =
                canonical==null?punishRepository.findByArchivedAndSchool(archived, schoolUtils.fetchSchoolName()):scoped(Criteria.where("archived").is(archived));

        return archivedRecords.stream()
                .filter(x -> x.getTeacherEmail().equalsIgnoreCase(authentication.getName()))
                .toList();
    }

    public List<Punishment> loggedInStudentFetchPunishmentDataByArchivedAndSchool(boolean archived) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Punishment> archivedRecords =
                canonical==null?punishRepository.findByArchivedAndSchool(archived, schoolUtils.fetchSchoolName()):scoped(Criteria.where("archived").is(archived));

        return archivedRecords.stream()
                .filter(x -> x.getStudentEmail().equalsIgnoreCase(authentication.getName()))
                .toList();
    }
}
