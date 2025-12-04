package com.reps.demogcloud.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assignment_templates")
public class AssignmentTemplate {

    @Id
    private String id;               // will map from legacy assignmentId
    private String infractionName;
    private int level;

    private boolean createdBySystem = true;
    private String createdByUserId;

    private Instant createdAt;
    private Instant updatedAt;

    private List<TemplateQuestion> questions;

    public enum QuestionType {
        READING_MC,
        EXPLORATORY_OPEN,
        EXPLORATORY_RADIO
    }

    public enum SelectionMode {
        SINGLE,
        MULTIPLE
    }

    public enum GradingMode {
        ALL_CORRECT,
        ANY_CORRECT
    }

    public enum RetryMode {
        TEXT // future: IMAGE, etc.
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TemplateQuestion {
        private String id;                 // "q1", UUID, etc.
        private int order;                 // display order
        private QuestionType type;
        private boolean required = true;

        // Shared text fields
        private String prompt;             // what student answers
        private String title;              // optional header

        // Reading passage (for READING_MC)
        private String passageBody;
        private List<String> passageReferences;

        // Options (for READING_MC and EXPLORATORY_RADIO)
        private List<AnswerOption> options;
        private SelectionMode selectionMode;   // SINGLE / MULTIPLE
        private GradingMode gradingMode;       // ALL_CORRECT / ANY_CORRECT

        // Retry block (for READING_MC)
        private RetryConfig retry;

        // Open-ended config (for EXPLORATORY_OPEN)
        private Integer minLength;
        private Integer maxLength;
        private Boolean graded;               // optional: not used yet
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnswerOption {
        private String id;        // "1", "A", "agree", etc.
        private String label;
        private boolean correct;  // for READING_MC; ignored for EXPLORATORY_RADIO
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetryConfig {
        private boolean enabled;
        private RetryMode mode;              // TEXT for now
        private String textToCopy;
        private Integer requiredAccuracyPercent; // null → default 80% on frontend
    }
}
