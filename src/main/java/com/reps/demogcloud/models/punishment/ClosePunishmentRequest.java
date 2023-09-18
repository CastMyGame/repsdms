package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePunishmentRequest {

    private String infractionName;
    private String studentEmail;
}
