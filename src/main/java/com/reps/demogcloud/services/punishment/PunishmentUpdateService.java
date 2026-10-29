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
    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}


    private final PunishRepository punishRepository;
    private final InfractionRepository infractionRepository;
    private final StudentRepository studentRepository;
    private final com.reps.demogcloud.services.WorkflowAccessService access;

    public Punishment updateMapIndex(String id, int index) {
        access.requireCase(id);
        access.requireStaff();
        throw new IllegalArgumentException("Save assignment answers and progress together through the case page");
    }

    public List<Punishment> updateTimeCreated() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findByArchived(false);
        List<Punishment> saved = new ArrayList<>();

        for (Punishment punishment : all) {
            if (punishment.getInfractionName().equals("Tardy")
                    || punishment.getInfractionName().equals("Horseplay")
                    || punishment.getInfractionName().equals("Disruptive Behavior")
                    || punishment.getInfractionName().equals("Unauthorized Device/Cell Phone")
                    || punishment.getInfractionName().equals("Dress Code")) {

                punishment.setArchived(true);
                punishment.setArchivedBy("repsdiscipline@gmail.com");
                punishment.setArchivedOn(LocalDate.now());
                punishment.setArchivedExplanation(" Tardy Sweep 5/10");

                saved.add(punishRepository.save(punishment));
            }
        }

        return saved;
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

    public List<Punishment> updateStudentEmails() {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy maintenance is unavailable");
        List<Punishment> all = punishRepository.findAll();
        List<Punishment> saved = new ArrayList<>();

        for (Punishment punishment : all) {
            String studentEmail = punishment.getStudentEmail();
            punishment.setStudentEmail(studentEmail);
            saved.add(punishRepository.save(punishment));
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
}
