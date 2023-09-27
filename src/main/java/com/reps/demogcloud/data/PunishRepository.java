package com.reps.demogcloud.data;

import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PunishRepository extends MongoRepository<Punishment, String> {
    //May need PunishResponse if that gets made for record keeping instead
    List<Punishment> findByStudent (Student student);
    List<Punishment> findByInfraction (Infraction infraction);
    List<Punishment> findByStatus (String status);
    Punishment findByPunishmentId (String punishId);
    List<Punishment> findByStudentStudentEmailIgnoreCaseAndInfractionInfractionNameAndStatus (String email, String infractionName, String status);

    List<Punishment> findByStudentStudentEmailAndInfractionInfractionName (String email, String infractionName);


    List<Punishment> findByInfractionInfractionName (String infractionName);
    List<Punishment> findByStatusAndTimeCreatedBefore (String status, LocalDateTime time);
}
