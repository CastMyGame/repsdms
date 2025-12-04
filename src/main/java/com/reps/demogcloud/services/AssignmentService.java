package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.data.AssignmentTemplateRepository;
import com.reps.demogcloud.models.assignments.Assignment;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import com.reps.demogcloud.models.assignments.AssignmentConverter;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTemplateRepository assignmentTemplateRepository;

    // -------- LEGACY METHODS (still operate on old Assignment model) --------
    public List<Assignment> getAllAssignments() {
        List<Assignment> assignments = new ArrayList<>();
        try {
            assignments = assignmentRepository.findAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
       return assignments;
    }
    public  Assignment createNewAssignment(Assignment assignments){
        return assignmentRepository.save(assignments);
    }
    public  Assignment deleteAssignment(String assignmentName){
        return assignmentRepository.deleteByInfractionName(assignmentName);
    }
    public Assignment updateNewAssignment(Assignment assignment, String id) throws Exception {
        try {
            Assignment existingAssignment = assignmentRepository.findById(id).orElseThrow();

            // Update fields of existingAssignment with the values from the provided assignment
            existingAssignment.setInfractionName(assignment.getInfractionName());
            existingAssignment.setLevel(assignment.getLevel());
            existingAssignment.setQuestions(assignment.getQuestions());

            // Update other fields as needed

            // Save the updated assignment
            return assignmentRepository.save(existingAssignment);

        } catch (NoSuchElementException e) {
            // Handle the case when the assignment with the given id is not found
            // You can throw a custom exception or return null depending on your requirements
            throw new Exception("Assignment with ID " + id + " not found");
        }
    }
    // -------- NEW METHOD: MIGRATE LEGACY -> TEMPLATES --------

    /**
     * One-time migration: read all legacy Assignment documents
     * and write corresponding AssignmentTemplate documents
     * into the 'assignment_templates' collection.
     *
     * Returns the count migrated.
     */
    public int migrateLegacyAssignmentsToTemplates() {
        List<Assignment> legacyAssignments = assignmentRepository.findAll();
        log.info("Found {} legacy assignments to migrate", legacyAssignments.size());

        int migrated = 0;
        for (Assignment legacy : legacyAssignments) {
            AssignmentTemplate template = AssignmentConverter.fromLegacy(legacy);
            assignmentTemplateRepository.save(template);
            migrated++;
            log.info("Migrated legacy assignment {} -> template {}",
                    legacy.getAssignmentId(), template.getId());
        }

        log.info("Finished migrating {} legacy assignments to templates", migrated);
        return migrated;
    }

    // -------- (OPTIONAL) simple template read methods for later --------

    public List<AssignmentTemplate> getAllTemplates() {
        return assignmentTemplateRepository.findAll();
    }

    public List<AssignmentTemplate> getTemplatesByInfractionAndLevel(String infractionName, int level) {
        return assignmentTemplateRepository.findByInfractionNameAndLevel(infractionName, level);
    }
}
