package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PunishmentFormRequest {
    private String firstName;
    private String lastName;
    private String studentEmail;
    private String infractionId;
    private String infractionPeriod;
    private ArrayList<String> infractionDescription;
    private String teacherEmail;

}
