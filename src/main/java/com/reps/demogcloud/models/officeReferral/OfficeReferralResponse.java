package com.reps.demogcloud.models.officeReferral;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OfficeReferralResponse {
    private OfficeReferral officeReferral;
    private String error;
}
