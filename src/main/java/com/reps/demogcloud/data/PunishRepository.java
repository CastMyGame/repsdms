package com.reps.demogcloud.data;

import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PunishRepository extends MongoRepository<Punishment, String> {
    //May need PunishResponse if that gets made for record keeping instead
    List<Punishment> findByStatusAndTeacherEmailAndStudentEmailAndInfractionId (String status, String studentEmail, String teacherEmail, String infractionId);

    List<Punishment> findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(String studentEmail, String infractionId, String cfr, boolean bool);
    List<Punishment> findByStudentEmailIgnoreCaseAndInfractionIdAndStatus(String studentEmail, String infractionId, String cfr);
    List<Punishment> findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(String studentEmail, String infractionId, String cfr);
    List<Punishment> findByStudentEmailAndInfractionId (String email, String infractionId);
    List<Punishment> findByStatusAndTimeCreatedBefore (String status, LocalDate time);
    List<Punishment> findByStudentEmail (String studentEmail);
    List<Punishment> findByInfractionId (String infractionid);
    List<Punishment> findByStatus (String status);

    List<Punishment> findAllBySchoolAndArchived(String school, Boolean archived);

    List<Punishment> findByArchived(boolean bool);
    List<Punishment> findByStudentEmailIgnoreCase (String email);
    List<Punishment> getAllPunishmentByStudentEmail(String studentEmail);

    List<Punishment> findAllBySchool(String school);

    Punishment findByPunishmentIdAndArchived(String punishmentId, boolean b);
    Punishment findByPunishmentId (String punishId);

    List<Punishment> findByArchivedAndSchool(boolean archived, String school);

    List<Punishment> findByInfractionIdAndArchivedAndSchool(String infractionId, boolean bool, String school);

    List<Punishment> findByStudentEmailIgnoreCaseAndInfractionIdAndArchived(String studentEmail, String infractionName, boolean b);

    List<Punishment> findByInfractionNameAndStatus(String infractionName, String status);

//    List<Punishment> findByFollowUpDateAndGuidanceStatus(LocalDate today, String dormant);

    List<Punishment> findByArchivedAndStatus(boolean archived, String status);
}
