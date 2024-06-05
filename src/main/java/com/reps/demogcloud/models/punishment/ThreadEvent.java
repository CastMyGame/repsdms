package com.reps.demogcloud.models.punishment;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class ThreadEvent {
    private LocalDate date;
    private String event;
    private String content;
}
