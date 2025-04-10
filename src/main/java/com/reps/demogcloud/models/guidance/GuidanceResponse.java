package com.reps.demogcloud.models.guidance;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GuidanceResponse {
    private List<GuidanceReferral> guidance;
    private String error;
    private String message;


}
