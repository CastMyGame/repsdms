package com.reps.demogcloud.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assignments")
public class Assignment {
    @Id
    private String assignmentId;
    private String assignmentName;
    private int level;
    private List<Question> questions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Question {
        private String question;
        private String title;
        private String body;
        private List<Reference> references;
        private List<RadioAnswer> radioAnswers;
        private RetryQuestion retryQuestion;
        private ExploratoryQuestions exploratoryQuestions;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Reference {
        private String details;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RadioAnswer {
        private String value;
        private String label;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetryQuestion {
        private String imgUrl;
        private String textToCompare;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExploratoryQuestions {
        private String exploratoryQuestion;
        private String getExploratoryQuestionDescription;
    }
}
