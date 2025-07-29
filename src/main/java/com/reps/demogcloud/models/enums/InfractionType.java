package com.reps.demogcloud.models.enums;

public enum InfractionType {
    POSITIVE("Positive Behavior Shout Out!"),
    BEHAVIORAL("Behavioral Concern"),
    ACADEMIC("Academic Concern"),
    INCOMPLETE_WORK("Failure to Complete Work"),
    TEACHER_GUIDANCE("Teacher Guidance Referral"),
    STUDENT_GUIDANCE("Student Guidance Referral");

    private final String label;

    InfractionType(String label) {
        this.label = label;
    }

    public static boolean isSpecialCase(String name) {
        for (InfractionType type : InfractionType.values()) {
            if (type.getLabel().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public String getLabel() {
        return label;
    }
}
