package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import java.util.NoSuchElementException;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.ast.Assign;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
public class AssignmentService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final AssignmentRepository assignmentRepository;

    public AssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }


    public List<Assignment> getAllAssignments(){
       return  assignmentRepository.findAll();

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
            Assignment updatedAssignment = assignmentRepository.save(existingAssignment);

            return updatedAssignment;
        } catch (NoSuchElementException e) {
            // Handle the case when the assignment with the given id is not found
            // You can throw a custom exception or return null depending on your requirements
            throw new Exception("Assignment with ID " + id + " not found");
        }
    }
}
