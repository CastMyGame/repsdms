package com.reps.demogcloud.models.guidance;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GuidanceRequest {
    private Guidance guidance;
    private String linkToPunishment;


}
