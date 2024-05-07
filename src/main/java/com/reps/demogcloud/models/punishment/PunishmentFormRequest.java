package com.reps.demogcloud.models.punishment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunishmentFormRequest {
    private String studentEmail;
    private String infractionName;
    private String infractionPeriod;
    private String infractionDescription;
    private String teacherEmail;
    private int currency;

}
