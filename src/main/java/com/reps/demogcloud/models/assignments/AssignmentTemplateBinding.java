package com.reps.demogcloud.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assignment_template_bindings")
public class AssignmentTemplateBinding {

    @Id
    private String id;

    private String teacherEmail;          // who this binding is for (null for school-wide)
    private String schoolId;              // optional, for school defaults
    private String infractionName;
    private int level;

    private String assignmentTemplateId;

    private boolean active = true;

    private Instant createdAt;
    private Instant updatedAt;
}
