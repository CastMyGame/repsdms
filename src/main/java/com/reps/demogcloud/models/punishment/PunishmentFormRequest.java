package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PunishmentFormRequest {
    private String firstName;
    private String lastName;
    private String studentEmail;
    private String infractionName;
    private String infractionPeriod;
    private List<String> infractionDescription;
    private String teacherEmail;

}
