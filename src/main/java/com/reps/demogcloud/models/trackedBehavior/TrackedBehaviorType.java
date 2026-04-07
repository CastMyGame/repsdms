package com.reps.demogcloud.models.trackedBehavior;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum TrackedBehaviorType {

    PHONE_OUT(
            "PHONE_OUT",
            "Phone Out",
            List.of("WARNING", "PHONE_CONFISCATED", "PARENT_CONTACT")
    ),
    TALKING_DURING_INSTRUCTION(
            "TALKING_DURING_INSTRUCTION",
            "Talking During Instruction",
            List.of("WARNING", "SEAT_CHANGE", "LUNCH_DETENTION")
    ),
    INTERRUPTED_CLASSROOM(
            "INTERRUPTED_CLASSROOM",
            "Interrupted Classroom",
            List.of("WARNING", "REFLECTION_FORM", "OFFICE_REFERRAL")
    );

    private final String code;
    private final String displayName;
    private final List<String> consequences;

    TrackedBehaviorType(String code, String displayName, List<String> consequences) {
        this.code = code;
        this.displayName = displayName;
        this.consequences = consequences;
    }

    public static boolean isValidCode(String code) {
        return Arrays.stream(values())
                .anyMatch(type -> type.code.equals(code));
    }

    public static String getDisplayNameByCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .map(TrackedBehaviorType::getDisplayName)
                .findFirst()
                .orElse(null);
    }

    public static List<String> getConsequencesByCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .map(TrackedBehaviorType::getConsequences)
                .findFirst()
                .orElse(List.of());
    }

    public static List<TrackedBehaviorType> getAll() {
        return Arrays.asList(values());
    }

}
