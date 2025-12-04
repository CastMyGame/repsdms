package com.reps.demogcloud.models.assignments;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.reps.demogcloud.models.assignments.AssignmentTemplate.*;

public class AssignmentConverter {

    /**
     * Convert a legacy Assignment (Mongo document) into the new AssignmentTemplate shape.
     */
    public static AssignmentTemplate fromLegacy(Assignment legacy) {
        AssignmentTemplate template = new AssignmentTemplate();

        template.setId(legacy.getAssignmentId());
        template.setInfractionName(legacy.getInfractionName());
        template.setLevel(legacy.getLevel());
        template.setCreatedBySystem(true); // all current ones are system-created
        template.setCreatedByUserId(null);
        template.setCreatedAt(Instant.now()); // or null if you want to set later
        template.setUpdatedAt(Instant.now());

        List<TemplateQuestion> normalized = new ArrayList<>();

        List<Assignment.Question> legacyQuestions = legacy.getQuestions();
        if (legacyQuestions != null) {
            int order = 1;
            for (int i = 0; i < legacyQuestions.size(); i++) {
                Assignment.Question legacyQ = legacyQuestions.get(i);
                String legacyType = legacyQ.getType();

                // 1) READING + optional RETRY block
                if ("reading".equalsIgnoreCase(legacyType)) {
                    TemplateQuestion q = new TemplateQuestion();
                    q.setId("q" + order);
                    q.setOrder(order++);
                    q.setType(QuestionType.READING_MC);
                    q.setRequired(true);

                    q.setPrompt(legacyQ.getQuestion());
                    q.setTitle(legacyQ.getTitle());
                    q.setPassageBody(legacyQ.getBody());
                    q.setPassageReferences(legacyQ.getReferences());

                    // Map radioAnswers → options
                    q.setOptions(mapRadioAnswers(legacyQ.getRadioAnswers()));

                    // Default grading config: single choice, must match correct exactly
                    q.setSelectionMode(SelectionMode.SINGLE);
                    q.setGradingMode(GradingMode.ALL_CORRECT);

                    // Look ahead for a retryQuestion
                    if (i + 1 < legacyQuestions.size()) {
                        Assignment.Question maybeRetry = legacyQuestions.get(i + 1);
                        if ("retryQuestion".equalsIgnoreCase(maybeRetry.getType())) {
                            RetryConfig retry = new RetryConfig();
                            retry.setEnabled(true);
                            retry.setMode(RetryMode.TEXT);
                            retry.setTextToCopy(maybeRetry.getTextToCompare());
                            retry.setRequiredAccuracyPercent(80); // your current default
                            q.setRetry(retry);

                            i++; // skip the retryQuestion we just consumed
                        }
                    }

                    normalized.add(q);
                    continue;
                }

                // 2) exploratory-open-ended
                if ("exploratory-open-ended".equalsIgnoreCase(legacyType)) {
                    TemplateQuestion q = new TemplateQuestion();
                    q.setId("q" + order);
                    q.setOrder(order++);
                    q.setType(QuestionType.EXPLORATORY_OPEN);
                    q.setRequired(true);

                    q.setPrompt(legacyQ.getQuestion());
                    q.setTitle(legacyQ.getTitle());
                    q.setMinLength(null);
                    q.setMaxLength(null);
                    q.setGraded(false);

                    normalized.add(q);
                    continue;
                }

                // 3) exploratory-radio
                if ("exploratory-radio".equalsIgnoreCase(legacyType)) {
                    TemplateQuestion q = new TemplateQuestion();
                    q.setId("q" + order);
                    q.setOrder(order++);
                    q.setType(QuestionType.EXPLORATORY_RADIO);
                    q.setRequired(true);

                    q.setPrompt(legacyQ.getQuestion());
                    q.setTitle(legacyQ.getTitle());

                    // For now, use a standard Agree/Neutral/Disagree set.
                    // If you later persist options in legacy, this can be updated.
                    q.setOptions(defaultExploratoryRadioOptions());
                    q.setGraded(false);

                    normalized.add(q);
                    continue;
                }

                // 4) standalone retryQuestion (should normally be eaten by a reading)
                if ("retryQuestion".equalsIgnoreCase(legacyType)) {
                    // Safety: skip; they should have been attached above
                    continue;
                }

                // 5) Unknown/future types: skip or log in real code
            }
        }

        template.setQuestions(normalized);
        return template;
    }

    private static List<AnswerOption> mapRadioAnswers(Map<String, Assignment.RadioAnswer> radioAnswers) {
        if (radioAnswers == null || radioAnswers.isEmpty()) {
            return Collections.emptyList();
        }

        // Keep deterministic ordering by key
        return radioAnswers.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> {
                    String id = entry.getKey();
                    Assignment.RadioAnswer legacyAns = entry.getValue();
                    AnswerOption opt = new AnswerOption();
                    opt.setId(id);
                    opt.setLabel(legacyAns.getLabel());
                    opt.setCorrect(legacyAns.isValue()); // 'value' was true/false
                    return opt;
                })
                .collect(Collectors.toList());
    }

    private static List<AnswerOption> defaultExploratoryRadioOptions() {
        List<AnswerOption> opts = new ArrayList<>();
        opts.add(new AnswerOption("agree", "Agree", false));
        opts.add(new AnswerOption("neutral", "Neutral", false));
        opts.add(new AnswerOption("disagree", "Disagree", false));
        return opts;
    }
}
