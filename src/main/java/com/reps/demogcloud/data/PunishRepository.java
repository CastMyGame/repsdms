package com.reps.demogcloud.data;

import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.joda.time.DateTime;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
    List<Punishment> findByStatusAndTimeCreatedBefore (String status, LocalDateTime time);
    List<Punishment> findByStatusAndTimeCreatedBetween (String status, DateTime timeCreated, DateTime now);
    Punishment findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndInfractionInfractionLevelAndStatus (String studentEmail, String infractionName, String infractionLevel, String status);

    List<Punishment> findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus(String studentEmail, String infractionName, String cfr);

    List<Punishment> findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatusAndIsArchived(String studentEmail, String infractionName, String cfr,boolean bool);

    Punishment findByPunishmentIdAndIsArchived(String punishmentId, boolean b);

    List<Punishment> getAllPunishmentByStudentStudentEmail(String studentEmail);

}
