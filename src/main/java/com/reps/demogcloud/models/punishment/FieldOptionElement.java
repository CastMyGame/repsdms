package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldOptionElement {
    private Integer value;
    private String text;
    private String note;
    private Boolean isPrimary;
    private Boolean isRemoved;
}
