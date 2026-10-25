package com.reps.demogcloud.services.currency;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

/** Immutable committed receipt; the unique _id serializes retries of one actor/action. */
@Data
@Document("currency_operations")
public class CurrencyOperation {
    @Id private String id;
    private String payloadHash;
    private String actor;
    private String school;
    private String kind;
    private Instant createdAt;
    private List<String> caseIds = new ArrayList<>();
    private List<String> studentIds = new ArrayList<>();
    private List<String> employeeIds = new ArrayList<>();
    private Integer teacherBalance;
    private Map<String,Integer> amounts = new LinkedHashMap<>();
}
