package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PunishmentUpdateService {
    private final PunishRepository punishRepository;
    private final InfractionRepository infractionRepository;
    private final StudentRepository studentRepository;

    public Punishment updateMapIndex(String id, int index) {
        Punishment punishment = punishRepository.findByPunishmentId(id);
        if (punishment != null) {
            punishment.setMapIndex(index);
            punishRepository.save(punishment);
            return punishment;

        } else {
            throw new ResourceNotFoundException("No Punishment with Id " + id + " number exist");

        }


    }

    public List<Punishment> updateTimeCreated() {
        List<Punishment> all = punishRepository.findByIsArchived(false);
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            if (punishment.getInfractionName().equals("Tardy") ||
                    punishment.getInfractionName().equals("Horseplay") ||
                    punishment.getInfractionName().equals("Disruptive Behavior") ||
                    punishment.getInfractionName().equals("Unauthorized Device/Cell Phone") ||
                    punishment.getInfractionName().equals("Dress Code")) {
                punishment.setArchived(true);
                punishment.setArchivedBy("repsdiscipline@gmail.com");
                punishment.setArchivedOn(LocalDate.now());
                punishment.setArchivedExplanation(" Tardy Sweep 5/10");
                punishRepository.save(punishment);
                saved.add(punishment);
            }
        }
        return saved;
    }

    public List<Punishment> updateDescriptions() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            if (punishment.getInfractionDescription().size() > 1) {
                punishment.getInfractionDescription().remove(0);
                punishRepository.save(punishment);
                saved.add(punishment);
            }
        }
        return saved;
    }

    public List<Punishment> updateStudentEmails() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            String studentEmail = punishment.getStudentEmail();
            punishment.setStudentEmail(studentEmail);
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateInfractionName() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infractionName = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionName(infractionName.getInfractionName());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateInfractionLevel() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Infraction infractionName = infractionRepository.findByInfractionId(punishment.getInfractionId());
            punishment.setInfractionLevel(infractionName.getInfractionLevel());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }

    public List<Punishment> updateSchools() {
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();
        for (Punishment punishment : all) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            punishment.setSchoolName(student.getSchool());
            punishRepository.save(punishment);
            saved.add(punishment);
        }
        return saved;
    }
}
