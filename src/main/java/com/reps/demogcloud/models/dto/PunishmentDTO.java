package com.reps.demogcloud.models.dto;

import com.reps.demogcloud.models.punishment.Punishment;
import lombok.Data;

@Data
public class PunishmentDTO {
    String studentFirstName;
    String studentLastName;
    String studentEmail;
    Punishment punishment;

}
