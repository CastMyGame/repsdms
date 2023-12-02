package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return assignmentRepository.deleteByAssignmentName(assignmentName);
    }
}
