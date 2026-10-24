package com.reps.demogcloud.models.assignments;

import lombok.Data;
import java.time.Instant;
import java.util.*;

/** Additive case-local work; never reuses the behavior description as answer storage. */
@Data
public class AssignmentWork {
    private long version;
    private String state = "ASSIGNED";
    private String unavailableReason;
    private AssignmentTemplate template;
    private Map<String, Answer> answers = new LinkedHashMap<>();
    private String currentQuestionId;
    private List<Submission> submissions = new ArrayList<>();
    private String feedback;
    private String lastActionId;
    private String lastActionDigest;
    private Instant savedAt;

    @Data public static class Answer {
        private String text;
        private List<String> optionIds = new ArrayList<>();
        private String retryText;
    }
    @Data public static class Submission {
        private String id;
        private Instant submittedAt;
        private Map<String, Answer> answers;
        private AssignmentTemplate template;
        private String outcome = "SUBMITTED";
        private String reviewer;
        private String feedback;
        private Instant reviewedAt;
    }
    public static AssignmentWork unavailable(String reason) {
        var work = new AssignmentWork(); work.state = "UNAVAILABLE"; work.unavailableReason = reason; return work;
    }
    public static AssignmentWork assigned(AssignmentTemplate template) {
        var work = new AssignmentWork();
        work.template = copy(template, AssignmentTemplate.class);
        try {
            if(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsBytes(work.template).length>262144)
                throw new IllegalArgumentException("Assignment template is too large to attach safely");
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid assignment template"); }
        if (work.template.getQuestions() == null || work.template.getQuestions().isEmpty())
            throw new IllegalArgumentException("Assignment template has no supported questions");
        if(work.template.getQuestions().size()>200) throw new IllegalArgumentException("Assignment template has too many questions");
        var ids = new HashSet<String>();
        for (int i=0;i<work.template.getQuestions().size();i++) {
            var q = work.template.getQuestions().get(i);
            if (q == null || q.getType() == null) throw new IllegalArgumentException("Unsupported assignment question");
            if (q.getId()==null || q.getId().isBlank()) q.setId("q"+(i+1));
            if(q.getId().length()>100 || q.getId().contains(".") || q.getId().contains("$") || q.getId().indexOf(0)>=0)
                throw new IllegalArgumentException("Unsupported assignment question identifier");
            if (!ids.add(q.getId())) throw new IllegalArgumentException("Duplicate question identifier");
            if(q.getMinLength()!=null && q.getMinLength()<0 || q.getMaxLength()!=null && q.getMaxLength()<0
                    || q.getMinLength()!=null && q.getMaxLength()!=null && q.getMinLength()>q.getMaxLength())
                throw new IllegalArgumentException("Invalid assignment answer length limits");
            if(q.isRequired() && q.getType()==AssignmentTemplate.QuestionType.EXPLORATORY_OPEN && Integer.valueOf(0).equals(q.getMaxLength()))
                throw new IllegalArgumentException("Required answer cannot have a zero maximum length");
            var optionIds=new HashSet<String>();
            if(q.getOptions()!=null) for(var option:q.getOptions())
                if(option==null || option.getId()==null || option.getId().isBlank() || !optionIds.add(option.getId()))
                    throw new IllegalArgumentException("Invalid assignment option identifiers");
            if(q.isRequired() && q.getType()!=AssignmentTemplate.QuestionType.EXPLORATORY_OPEN) {
                if(q.getOptions()==null || q.getOptions().isEmpty()) throw new IllegalArgumentException("Required selection has no options");
                if(q.getType()==AssignmentTemplate.QuestionType.READING_MC && q.getOptions().stream().noneMatch(AssignmentTemplate.AnswerOption::isCorrect)
                        && (q.getRetry()==null || !q.getRetry().isEnabled() || q.getRetry().getTextToCopy()==null || q.getRetry().getTextToCopy().isBlank()))
                    throw new IllegalArgumentException("Required reading has neither a correct answer nor a retry");
            }
        }
        work.currentQuestionId = work.template.getQuestions().get(0).getId();
        return work;
    }
    public static <T> T copy(T value, Class<T> type) {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().convertValue(value,type);
    }
}
