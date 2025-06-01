package com.reps.demogcloud.models.punishment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunishmentFormRequest {
    private String studentEmail;
    private String infractionName;
    private String infractionPeriod;
    private String infractionDescription;
    private String teacherEmail;
    private int currency;
    private String guidanceDescription;
    private String phoneLogDescription;
    private boolean isAdminReferral;
    private boolean isGuidanceReferral;
}
