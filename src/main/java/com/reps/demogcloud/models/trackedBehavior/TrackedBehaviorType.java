package com.reps.demogcloud.models.trackedBehavior;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum TrackedBehaviorType {

    PHONE_OUT("PHONE_OUT", "Phone Out"),
    TALKING_DURING_INSTRUCTION("TALKING_DURING_INSTRUCTION", "Talking During Instruction"),
    INTERRUPTED_CLASSROOM("INTERRUPTED_CLASSROOM", "Interrupted Classroom");

    private final String code;
    private final String displayName;

    TrackedBehaviorType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
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

    public static List<TrackedBehaviorType> getAll() {
        return Arrays.asList(values());
    }

}
