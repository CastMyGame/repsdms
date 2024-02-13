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
    List<Punishment> findByStudent (Student student);
    List<Punishment> findByInfractionInfractionName (String infractionName);
    List<Punishment> findByStatus (String status);
    Punishment findByPunishmentId (String punishId);
    List<Punishment> findByStudentStudentEmailAndInfractionInfractionName (String email, String infractionName);

    List<Punishment> findByIsArchived (boolean bool);


    List<Punishment> findByStudentStudentEmailIgnoreCase (String email);
    List<Punishment> findByStatusAndTeacherEmailAndStudentStudentEmailAndInfractionInfractionName (String status, String studentEmail, String teacherEmail, String infractionName);
    List<Punishment> findByStatusAndTimeCreatedBefore (String status, LocalDate time);
    List<Punishment> findByStatusAndTimeCreatedBetween (String status, LocalDate timeCreated, LocalDate now);
    Punishment findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndInfractionInfractionLevelAndStatus (String studentEmail, String infractionName, String infractionLevel, String status);

    List<Punishment> findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(String studentEmail, String infractionName, String cfr);

    List<Punishment> findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatusAndIsArchived(String studentEmail, String infractionName, String cfr,boolean bool);

    Punishment findByPunishmentIdAndIsArchived(String punishmentId, boolean b);

    List<Punishment> getAllPunishmentByStudentStudentEmail(String studentEmail);

}
