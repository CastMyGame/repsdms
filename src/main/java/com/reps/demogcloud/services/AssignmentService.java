package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.models.assignments.Assignment;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;

    public AssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }
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
}
