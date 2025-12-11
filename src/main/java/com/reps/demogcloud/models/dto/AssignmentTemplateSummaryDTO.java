package com.reps.demogcloud.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentTemplateSummaryDTO {

    private String id;
    private String name;

    // identifying info
    private String infractionName;
    private int level;

    // permissions / ownership
    private boolean createdBySystem;
    private String createdByUserId;

    // basic preview info
    private int questionCount;
    private String firstQuestionPreview;

    // metadata
    private Instant createdAt;
    private Instant updatedAt;
}
