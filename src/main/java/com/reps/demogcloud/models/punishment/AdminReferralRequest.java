package com.reps.demogcloud.models.punishment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminReferralRequest {
    private String studentEmail;
    private String infractionPeriod;
    private String infractionDescription;
    private String adminEmail;
}
