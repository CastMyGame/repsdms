package com.reps.demogcloud.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "assignments")
public class Assignment {
    @Id
    private String assignmentId;
    private String infractionName;
    private int level;
    private List<Question> questions;


    @Data
    @AllArgsConstructor
    public static class Question {
        private String question;
        private String type;
        private String title;
        private String body;
        private Map<String, RadioAnswer> radioAnswers;
        private String textToCompare;
        private List<String> references;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RadioAnswer {
        private boolean value;
        private String label;


    }

}
