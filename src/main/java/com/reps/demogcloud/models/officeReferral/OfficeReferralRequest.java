package com.reps.demogcloud.models.officeReferral;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfficeReferralRequest {
    private String studentEmail;
    private OfficeReferralCode referralCode;
    private ArrayList<String> referralDescription;
    private String teacherEmail;
    private String classPeriod;
    private int currency;
}
