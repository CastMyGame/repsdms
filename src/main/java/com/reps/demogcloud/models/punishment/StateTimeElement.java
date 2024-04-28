package com.reps.demogcloud.models.punishment;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class StateTimeElement {
    private LocalDate date;
    private LocalTime time;
}
