package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
public class StateTimeElement {
    private String date;
    private String time;
}
